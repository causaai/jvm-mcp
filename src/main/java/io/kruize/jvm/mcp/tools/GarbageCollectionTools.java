package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricTimeSeries;
import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;

/**
 * MCP Tools for JVM Garbage Collection Investigation
 */
@ApplicationScoped
public class GarbageCollectionTools {
    
    private static final Logger LOG = Logger.getLogger(GarbageCollectionTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = """
            Show current garbage collection activity with collection counts and pause times per collector.
            Provides total collections, total GC time, and average pause times for each GC collector.
            
            No parameters required.
            
            Use this tool when:
            - Investigating GC-related performance issues
            - Checking if GC is causing application pauses
            - Comparing different GC collectors (e.g., global vs scavenge in OpenJ9)
            - Initial GC health assessment
            
            Returns: Per-collector metrics (name, total collections, total time, avg pause ms) and overall summary.
            """)
    public Map<String, Object> getGcActivity() {
        LOG.info("Executing getGcActivity tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get GC collection counts and times
            List<MetricTimeSeries> gcCounts = metricsService.executeQuery("jvm_gc_collection_seconds_count");
            List<MetricTimeSeries> gcTimes = metricsService.executeQuery("jvm_gc_collection_seconds_sum");
            
            // Build GC collector information
            Map<String, Map<String, Object>> collectorsMap = new HashMap<>();
            
            // Process counts
            for (var series : gcCounts) {
                String gcName = series.getLabels().get("gc");
                if (gcName != null && !series.getValues().isEmpty()) {
                    Map<String, Object> collectorInfo = collectorsMap.computeIfAbsent(gcName, k -> new HashMap<>());
                    collectorInfo.put("name", gcName);
                    collectorInfo.put("total_collections", series.getValues().get(0).getValue().longValue());
                }
            }
            
            // Process times
            for (var series : gcTimes) {
                String gcName = series.getLabels().get("gc");
                if (gcName != null && !series.getValues().isEmpty()) {
                    Map<String, Object> collectorInfo = collectorsMap.computeIfAbsent(gcName, k -> new HashMap<>());
                    collectorInfo.put("total_time_seconds", series.getValues().get(0).getValue());
                }
            }
            
            // Calculate average pause times
            List<Map<String, Object>> collectors = new ArrayList<>();
            long totalCollections = 0;
            double totalTime = 0.0;
            
            for (Map<String, Object> collectorInfo : collectorsMap.values()) {
                Long collections = (Long) collectorInfo.get("total_collections");
                Double time = (Double) collectorInfo.get("total_time_seconds");
                
                if (collections != null && time != null && collections > 0) {
                    double avgPauseMs = (time / collections) * 1000;
                    collectorInfo.put("avg_pause_ms", Math.round(avgPauseMs * 10.0) / 10.0);
                    
                    totalCollections += collections;
                    totalTime += time;
                }
                
                collectors.add(collectorInfo);
            }
            
            result.put("collectors", collectors);
            
            // Summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("total_collections", totalCollections);
            summary.put("total_time_seconds", Math.round(totalTime * 100.0) / 100.0);
            if (totalCollections > 0) {
                summary.put("overall_avg_pause_ms", Math.round((totalTime / totalCollections) * 1000 * 10.0) / 10.0);
            }
            result.put("summary", summary);
            
        } catch (Exception e) {
            LOG.error("Error in getGcActivity", e);
            result.put("error", "Error fetching GC activity: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show GC frequency (collections per minute) and time overhead trends over a specified window.
            Provides time-series data to identify GC patterns and spikes.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Analyzing GC frequency patterns over time
            - Identifying GC storms or spikes
            - Correlating GC activity with application events
            - Detecting increasing GC pressure trends
            
            Returns: Time-series of GC frequency (per min) and GC time overhead (%) with min/max/avg statistics.
            """)
    public Map<String, Object> getGcBehaviorOverTime(
            String lookback,
            String step) {
        
        LOG.infof("Executing getGcBehaviorOverTime tool with lookback=%s, step=%s", lookback, step);
        
        // Default values
        if (lookback == null || lookback.isEmpty()) {
            lookback = "1h";
        }
        if (step == null || step.isEmpty()) {
            step = "1m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        result.put("step", step);
        
        try {
            Instant end = Instant.now();
            result.put("end_time", end.toString());
            
            // Query GC frequency (collections per minute)
            String frequencyQuery = String.format("rate(jvm_gc_collection_seconds_count[%s]) * 60", step);
            List<MetricTimeSeries> frequencyData = metricsService.executeQuery(frequencyQuery);
            
            if (!frequencyData.isEmpty()) {
                List<MetricValue> frequencyValues = frequencyData.get(0).getValues();
                
                Map<String, Object> gcFrequency = new HashMap<>();
                gcFrequency.put("samples", frequencyValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "collections_per_min", Math.round(v.getValue() * 10.0) / 10.0
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(frequencyValues);
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("min_per_min", Math.round(stats.get("min") * 10.0) / 10.0);
                statistics.put("max_per_min", Math.round(stats.get("max") * 10.0) / 10.0);
                statistics.put("avg_per_min", Math.round(stats.get("avg") * 10.0) / 10.0);
                
                gcFrequency.put("statistics", statistics);
                result.put("gc_frequency", gcFrequency);
                result.put("start_time", frequencyValues.get(0).getTimestamp().toString());
            }
            
            // Query GC time overhead (percentage)
            String overheadQuery = String.format("rate(jvm_gc_collection_seconds_sum[%s]) * 100", step);
            List<MetricTimeSeries> overheadData = metricsService.executeQuery(overheadQuery);
            
            if (!overheadData.isEmpty()) {
                List<MetricValue> overheadValues = overheadData.get(0).getValues();
                
                Map<String, Object> gcTimeOverhead = new HashMap<>();
                gcTimeOverhead.put("samples", overheadValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "gc_time_percent", Math.round(v.getValue() * 10.0) / 10.0
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(overheadValues);
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("min_percent", Math.round(stats.get("min") * 10.0) / 10.0);
                statistics.put("max_percent", Math.round(stats.get("max") * 10.0) / 10.0);
                statistics.put("avg_percent", Math.round(stats.get("avg") * 10.0) / 10.0);
                
                gcTimeOverhead.put("statistics", statistics);
                result.put("gc_time_overhead", gcTimeOverhead);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getGcBehaviorOverTime", e);
            result.put("error", "Error fetching GC behavior over time: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Assess garbage collection efficiency by analyzing GC overhead, frequency, and heap reclamation rate.
            Determines if GC is effectively reclaiming memory or struggling.
            
            Parameters:
            - window: Time window for analysis (e.g., "5m", "10m", "30m", "1h"). Default: "5m"
            
            Use this tool when:
            - Determining if GC is keeping up with allocation
            - Investigating high GC overhead issues
            - Assessing GC effectiveness before OOM
            - Evaluating if heap size is appropriate
            
            Returns: GC overhead %, collections per minute, heap reclamation analysis (before/after GC, reclamation rate).
            """)
    public Map<String, Object> getGcEfficiency(String window) {
        LOG.infof("Executing getGcEfficiency tool with window=%s", window);
        
        // Default value
        if (window == null || window.isEmpty()) {
            window = "5m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("window", window);
        result.put("timestamp", Instant.now().toString());
        
        try {
            // GC overhead percentage
            String overheadQuery = String.format("rate(jvm_gc_collection_seconds_sum[%s]) * 100", window);
            List<MetricTimeSeries> overheadData = metricsService.executeQuery(overheadQuery);
            
            if (!overheadData.isEmpty() && !overheadData.get(0).getValues().isEmpty()) {
                double gcOverhead = overheadData.get(0).getValues().get(0).getValue();
                result.put("gc_overhead_percent", Math.round(gcOverhead * 10.0) / 10.0);
            }
            
            // Collections per minute
            String frequencyQuery = String.format("rate(jvm_gc_collection_seconds_count[%s]) * 60", window);
            List<MetricTimeSeries> frequencyData = metricsService.executeQuery(frequencyQuery);
            
            if (!frequencyData.isEmpty() && !frequencyData.get(0).getValues().isEmpty()) {
                double collectionsPerMin = frequencyData.get(0).getValues().get(0).getValue();
                result.put("collections_per_minute", Math.round(collectionsPerMin * 10.0) / 10.0);
            }
            
            // Heap reclamation analysis
            List<MetricValue> heapValues = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", window, "30s");
            
            if (!heapValues.isEmpty()) {
                Map<String, Double> stats = metricsService.calculateStatistics(heapValues);
                
                Map<String, Object> heapReclamation = new HashMap<>();
                heapReclamation.put("avg_heap_before_gc_bytes", stats.get("max").longValue());
                heapReclamation.put("avg_heap_after_gc_bytes", stats.get("min").longValue());
                
                long reclaimed = stats.get("max").longValue() - stats.get("min").longValue();
                heapReclamation.put("avg_reclaimed_bytes", reclaimed);
                
                if (stats.get("max") > 0) {
                    double reclamationRate = (reclaimed / stats.get("max")) * 100;
                    heapReclamation.put("reclamation_rate_percent", Math.round(reclamationRate * 10.0) / 10.0);
                }
                
                result.put("heap_reclamation", heapReclamation);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getGcEfficiency", e);
            result.put("error", "Error fetching GC efficiency: " + e.getMessage());
        }
        
        return result;
    }
}

