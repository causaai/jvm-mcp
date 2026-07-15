package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for Time-Window Investigation
 */
@ApplicationScoped
public class TimeWindowTools {
    
    private static final Logger LOG = Logger.getLogger(TimeWindowTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = "Get all metrics during a specific incident timeframe")
    public Map<String, Object> getIncidentWindowData(
            String startTime,
            String endTime,
            String step) {
        
        LOG.infof("Executing getIncidentWindowData tool with start=%s, end=%s, step=%s", 
            startTime, endTime, step);
        
        // Default step
        if (step == null || step.isEmpty()) {
            step = "1m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("step", step);
        
        try {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            
            result.put("start_time", start.toString());
            result.put("end_time", end.toString());
            
            long durationSeconds = Duration.between(start, end).getSeconds();
            result.put("duration_seconds", durationSeconds);
            
            Map<String, Object> metrics = new HashMap<>();
            
            // Heap usage during incident
            analyzeHeapDuringIncident(metrics, start, end, step);
            
            // GC activity during incident
            analyzeGcDuringIncident(metrics, start, end, step);
            
            // Thread count during incident
            analyzeThreadsDuringIncident(metrics, start, end, step);
            
            // CPU usage during incident
            analyzeCpuDuringIncident(metrics, start, end, step);
            
            result.put("metrics", metrics);
            
        } catch (Exception e) {
            LOG.error("Error in getIncidentWindowData", e);
            result.put("error", "Error fetching incident window data: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Compare metrics before and after an event (deployment, restart, etc.)")
    public Map<String, Object> getBeforeAfterSnapshot(
            String eventTime,
            String window) {
        
        LOG.infof("Executing getBeforeAfterSnapshot tool with eventTime=%s, window=%s", 
            eventTime, window);
        
        // Default window
        if (window == null || window.isEmpty()) {
            window = "5m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("event_time", eventTime);
        result.put("window", window);
        
        try {
            Instant event = Instant.parse(eventTime);
            Duration windowDuration = parseDuration(window);
            
            Instant beforeStart = event.minus(windowDuration);
            Instant afterEnd = event.plus(windowDuration);
            
            // Before window
            Map<String, Object> before = new HashMap<>();
            before.put("time_range", beforeStart.toString() + " to " + event.toString());
            analyzeWindowMetrics(before, beforeStart, event);
            result.put("before", before);
            
            // After window
            Map<String, Object> after = new HashMap<>();
            after.put("time_range", event.toString() + " to " + afterEnd.toString());
            analyzeWindowMetrics(after, event, afterEnd);
            result.put("after", after);
            
            // Calculate changes
            Map<String, Object> changes = calculateChanges(before, after);
            result.put("changes", changes);
            
        } catch (Exception e) {
            LOG.error("Error in getBeforeAfterSnapshot", e);
            result.put("error", "Error fetching before/after snapshot: " + e.getMessage());
        }
        
        return result;
    }
    
    private void analyzeHeapDuringIncident(Map<String, Object> metrics, Instant start, Instant end, String step) {
        try {
            String lookback = Duration.between(start, end).toMinutes() + "m";
            List<MetricValue> heapValues = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", lookback, step);
            
            if (!heapValues.isEmpty()) {
                Map<String, Object> heapData = new HashMap<>();
                
                heapData.put("samples", heapValues.stream()
                    .filter(v -> !v.getTimestamp().isBefore(start) && !v.getTimestamp().isAfter(end))
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "bytes", v.getValue().longValue()
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(heapValues);
                heapData.put("min", stats.get("min").longValue());
                heapData.put("max", stats.get("max").longValue());
                heapData.put("avg", stats.get("avg").longValue());
                
                if (heapValues.size() >= 2) {
                    long startValue = heapValues.get(0).getValue().longValue();
                    long endValue = heapValues.get(heapValues.size() - 1).getValue().longValue();
                    heapData.put("start_value", startValue);
                    heapData.put("end_value", endValue);
                    
                    if (startValue > 0) {
                        double changePercent = ((endValue - startValue) * 100.0) / startValue;
                        heapData.put("change_percent", Math.round(changePercent * 10.0) / 10.0);
                    }
                }
                
                metrics.put("heap_usage", heapData);
            }
        } catch (Exception e) {
            LOG.error("Error analyzing heap during incident", e);
        }
    }
    
    private void analyzeGcDuringIncident(Map<String, Object> metrics, Instant start, Instant end, String step) {
        try {
            String lookback = Duration.between(start, end).toMinutes() + "m";
            List<MetricValue> gcCountValues = metricsService.getMetricRange(
                "jvm_gc_collection_seconds_count", lookback, step);
            List<MetricValue> gcTimeValues = metricsService.getMetricRange(
                "jvm_gc_collection_seconds_sum", lookback, step);
            
            if (!gcCountValues.isEmpty() && !gcTimeValues.isEmpty()) {
                Map<String, Object> gcData = new HashMap<>();
                
                long totalCollections = 0;
                double totalGcTime = 0.0;
                
                if (gcCountValues.size() >= 2) {
                    totalCollections = gcCountValues.get(gcCountValues.size() - 1).getValue().longValue() 
                        - gcCountValues.get(0).getValue().longValue();
                    totalGcTime = gcTimeValues.get(gcTimeValues.size() - 1).getValue() 
                        - gcTimeValues.get(0).getValue();
                }
                
                gcData.put("total_collections", totalCollections);
                gcData.put("total_gc_time_seconds", Math.round(totalGcTime * 100.0) / 100.0);
                
                long durationMinutes = Duration.between(start, end).toMinutes();
                if (durationMinutes > 0) {
                    gcData.put("avg_collections_per_min", 
                        Math.round((totalCollections * 1.0 / durationMinutes) * 10.0) / 10.0);
                }
                
                metrics.put("gc_activity", gcData);
            }
        } catch (Exception e) {
            LOG.error("Error analyzing GC during incident", e);
        }
    }
    
    private void analyzeThreadsDuringIncident(Map<String, Object> metrics, Instant start, Instant end, String step) {
        try {
            String lookback = Duration.between(start, end).toMinutes() + "m";
            List<MetricValue> threadValues = metricsService.getMetricRange(
                "jvm_threads_current", lookback, step);
            
            if (!threadValues.isEmpty()) {
                Map<String, Object> threadData = new HashMap<>();
                
                threadData.put("samples", threadValues.stream()
                    .filter(v -> !v.getTimestamp().isBefore(start) && !v.getTimestamp().isAfter(end))
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "count", v.getValue().longValue()
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(threadValues);
                threadData.put("min", stats.get("min").longValue());
                threadData.put("max", stats.get("max").longValue());
                threadData.put("avg", stats.get("avg").longValue());
                
                if (threadValues.size() >= 2) {
                    long startValue = threadValues.get(0).getValue().longValue();
                    long endValue = threadValues.get(threadValues.size() - 1).getValue().longValue();
                    threadData.put("start_value", startValue);
                    threadData.put("end_value", endValue);
                    
                    if (startValue > 0) {
                        double changePercent = ((endValue - startValue) * 100.0) / startValue;
                        threadData.put("change_percent", Math.round(changePercent * 10.0) / 10.0);
                    }
                }
                
                metrics.put("thread_count", threadData);
            }
        } catch (Exception e) {
            LOG.error("Error analyzing threads during incident", e);
        }
    }
    
    private void analyzeCpuDuringIncident(Map<String, Object> metrics, Instant start, Instant end, String step) {
        try {
            String lookback = Duration.between(start, end).toMinutes() + "m";
            List<MetricValue> cpuValues = metricsService.getMetricRange(
                "jvm_process_cpu_load", lookback, step);
            
            if (!cpuValues.isEmpty()) {
                Map<String, Object> cpuData = new HashMap<>();
                
                cpuData.put("samples", cpuValues.stream()
                    .filter(v -> !v.getTimestamp().isBefore(start) && !v.getTimestamp().isAfter(end))
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "percent", Math.round(v.getValue() * 100 * 10.0) / 10.0
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(cpuValues);
                cpuData.put("avg_process_percent", Math.round(stats.get("avg") * 100 * 10.0) / 10.0);
                cpuData.put("max_process_percent", Math.round(stats.get("max") * 100 * 10.0) / 10.0);
                
                metrics.put("cpu_usage", cpuData);
            }
        } catch (Exception e) {
            LOG.error("Error analyzing CPU during incident", e);
        }
    }
    
    private void analyzeWindowMetrics(Map<String, Object> window, Instant start, Instant end) {
        try {
            String lookback = Duration.between(start, end).toMinutes() + "m";
            
            // Heap usage
            List<MetricValue> heapValues = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", lookback, "1m");
            if (!heapValues.isEmpty()) {
                Map<String, Double> heapStats = metricsService.calculateStatistics(heapValues);
                Map<String, Object> heapUsage = new HashMap<>();
                heapUsage.put("avg", heapStats.get("avg").longValue());
                heapUsage.put("min", heapStats.get("min").longValue());
                heapUsage.put("max", heapStats.get("max").longValue());
                window.put("heap_usage_bytes", heapUsage);
            }
            
            // GC collections per minute
            String gcQuery = "rate(jvm_gc_collection_seconds_count[1m]) * 60";
            // Simplified: use current value as approximation
            Double gcFreq = metricsService.getCurrentMetricValue("jvm_gc_collection_seconds_count");
            if (gcFreq != null) {
                window.put("gc_collections_per_min", Math.round(gcFreq * 10.0) / 10.0);
            }
            
            // Thread count
            List<MetricValue> threadValues = metricsService.getMetricRange(
                "jvm_threads_current", lookback, "1m");
            if (!threadValues.isEmpty()) {
                Map<String, Double> threadStats = metricsService.calculateStatistics(threadValues);
                window.put("thread_count", threadStats.get("avg").longValue());
            }
            
            // CPU percent
            List<MetricValue> cpuValues = metricsService.getMetricRange(
                "jvm_process_cpu_load", lookback, "1m");
            if (!cpuValues.isEmpty()) {
                Map<String, Double> cpuStats = metricsService.calculateStatistics(cpuValues);
                window.put("cpu_percent", Math.round(cpuStats.get("avg") * 100 * 10.0) / 10.0);
            }
            
        } catch (Exception e) {
            LOG.error("Error analyzing window metrics", e);
        }
    }
    
    private Map<String, Object> calculateChanges(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new HashMap<>();
        
        try {
            // Heap usage delta
            if (before.containsKey("heap_usage_bytes") && after.containsKey("heap_usage_bytes")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> beforeHeap = (Map<String, Object>) before.get("heap_usage_bytes");
                @SuppressWarnings("unchecked")
                Map<String, Object> afterHeap = (Map<String, Object>) after.get("heap_usage_bytes");
                
                long beforeAvg = (Long) beforeHeap.get("avg");
                long afterAvg = (Long) afterHeap.get("avg");
                
                double deltaPercent = ((afterAvg - beforeAvg) * 100.0) / beforeAvg;
                changes.put("heap_usage_delta_percent", Math.round(deltaPercent * 10.0) / 10.0);
            }
            
            // GC frequency delta
            if (before.containsKey("gc_collections_per_min") && after.containsKey("gc_collections_per_min")) {
                double beforeGc = (Double) before.get("gc_collections_per_min");
                double afterGc = (Double) after.get("gc_collections_per_min");
                
                if (beforeGc > 0) {
                    double deltaPercent = ((afterGc - beforeGc) * 100.0) / beforeGc;
                    changes.put("gc_frequency_delta_percent", Math.round(deltaPercent * 10.0) / 10.0);
                }
            }
            
            // Thread count delta
            if (before.containsKey("thread_count") && after.containsKey("thread_count")) {
                long beforeThreads = (Long) before.get("thread_count");
                long afterThreads = (Long) after.get("thread_count");
                
                changes.put("thread_count_delta", afterThreads - beforeThreads);
            }
            
            // CPU delta
            if (before.containsKey("cpu_percent") && after.containsKey("cpu_percent")) {
                double beforeCpu = (Double) before.get("cpu_percent");
                double afterCpu = (Double) after.get("cpu_percent");
                
                changes.put("cpu_delta_percent", Math.round((afterCpu - beforeCpu) * 10.0) / 10.0);
            }
            
        } catch (Exception e) {
            LOG.error("Error calculating changes", e);
        }
        
        return changes;
    }
    
    private Duration parseDuration(String duration) {
        try {
            String unit = duration.substring(duration.length() - 1);
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
            
            return switch (unit) {
                case "s" -> Duration.ofSeconds(value);
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                case "d" -> Duration.ofDays(value);
                default -> Duration.ofMinutes(value);
            };
        } catch (Exception e) {
            LOG.warnf("Invalid duration format: %s, defaulting to 5 minutes", duration);
            return Duration.ofMinutes(5);
        }
    }
}

