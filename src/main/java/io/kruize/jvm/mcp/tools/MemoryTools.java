package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for JVM Memory Investigation
 */
@ApplicationScoped
public class MemoryTools {
    
    private static final Logger LOG = Logger.getLogger(MemoryTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = """
            Get current heap memory status with utilization metrics and 5-minute trends.
            Returns heap used/max/committed bytes, utilization percentage, and growth rate.
            
            No parameters required.
            
            Use this tool when:
            - Checking current memory pressure
            - Investigating OOM (Out of Memory) issues
            - Monitoring heap utilization trends
            - Initial memory health assessment
            
            Returns: Current heap metrics, utilization %, available bytes, and 5m trend analysis.
            """)
    public Map<String, Object> getHeapStatus() {
        LOG.info("Executing getHeapStatus tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get current heap metrics
            Double heapUsed = metricsService.getCurrentMetricValue("jvm_memory_heap_used_bytes");
            Double heapMax = metricsService.getCurrentMetricValue("jvm_memory_heap_max_bytes");
            Double heapCommitted = metricsService.getCurrentMetricValue("jvm_memory_heap_committed_bytes");
            
            if (heapUsed == null || heapMax == null || heapCommitted == null) {
                result.put("error", "Unable to fetch heap metrics from data source");
                return result;
            }
            
            // Calculate heap status
            Map<String, Object> heap = new HashMap<>();
            heap.put("used_bytes", heapUsed.longValue());
            heap.put("max_bytes", heapMax.longValue());
            heap.put("committed_bytes", heapCommitted.longValue());
            heap.put("utilization_percent", (heapUsed / heapMax) * 100);
            heap.put("available_bytes", heapMax.longValue() - heapUsed.longValue());
            
            result.put("heap", heap);
            
            // Get 5-minute trend
            List<MetricValue> recentValues = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", "5m", "30s");
            
            if (!recentValues.isEmpty()) {
                Map<String, Object> trend = new HashMap<>();
                trend.put("window", "5m");
                
                Map<String, Double> stats = metricsService.calculateStatistics(recentValues);
                trend.put("min_used_bytes", stats.get("min").longValue());
                trend.put("max_used_bytes", stats.get("max").longValue());
                trend.put("avg_used_bytes", stats.get("avg").longValue());
                
                Double growthRate = metricsService.calculateGrowthRate(recentValues);
                trend.put("growth_rate_bytes_per_min", growthRate.longValue());
                
                result.put("recent_trend", trend);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getHeapStatus", e);
            result.put("error", "Error fetching heap status: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Get detailed breakdown of all JVM memory pools (heap and non-heap) with utilization metrics.
            Shows which specific pools are under pressure. Supports OpenJ9 pools (nursery, tenured-SOA/LOA).
            
            No parameters required.
            
            Use this tool when:
            - Identifying which memory pool is causing issues
            - Investigating metaspace/code cache problems
            - Analyzing OpenJ9 memory pool distribution
            - Determining if specific generation is full
            
            Returns: List of all pools with used/max/committed bytes, utilization %, and pool type (heap/non-heap).
            """)
    public Map<String, Object> getMemoryPoolsBreakdown() {
        LOG.info("Executing getMemoryPoolsBreakdown tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Query all memory pools
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> poolsUsed = 
                metricsService.executeQuery("jvm_memory_pool_used_bytes");
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> poolsMax = 
                metricsService.executeQuery("jvm_memory_pool_max_bytes");
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> poolsCommitted = 
                metricsService.executeQuery("jvm_memory_pool_committed_bytes");
            
            // Build pool information
            Map<String, Map<String, Object>> poolsMap = new HashMap<>();
            
            // Process used bytes
            for (var series : poolsUsed) {
                String poolName = series.getLabels().get("pool");
                if (poolName != null && !series.getValues().isEmpty()) {
                    Map<String, Object> poolInfo = poolsMap.computeIfAbsent(poolName, k -> new HashMap<>());
                    poolInfo.put("name", poolName);
                    poolInfo.put("used_bytes", series.getValues().get(0).getValue().longValue());
                }
            }
            
            // Process max bytes
            for (var series : poolsMax) {
                String poolName = series.getLabels().get("pool");
                if (poolName != null && !series.getValues().isEmpty()) {
                    Map<String, Object> poolInfo = poolsMap.computeIfAbsent(poolName, k -> new HashMap<>());
                    poolInfo.put("max_bytes", series.getValues().get(0).getValue().longValue());
                }
            }
            
            // Process committed bytes
            for (var series : poolsCommitted) {
                String poolName = series.getLabels().get("pool");
                if (poolName != null && !series.getValues().isEmpty()) {
                    Map<String, Object> poolInfo = poolsMap.computeIfAbsent(poolName, k -> new HashMap<>());
                    poolInfo.put("committed_bytes", series.getValues().get(0).getValue().longValue());
                }
            }
            
            // Calculate utilization and determine type
            for (Map<String, Object> poolInfo : poolsMap.values()) {
                Long used = (Long) poolInfo.get("used_bytes");
                Long max = (Long) poolInfo.get("max_bytes");
                
                // Determine pool type based on name
                String poolName = (String) poolInfo.get("name");
                if (poolName.toLowerCase().contains("metaspace") || 
                    poolName.toLowerCase().contains("code") ||
                    poolName.toLowerCase().contains("compressed")) {
                    poolInfo.put("type", "non-heap");
                } else {
                    poolInfo.put("type", "heap");
                }
                
                if (used != null && max != null && max > 0) {
                    poolInfo.put("utilization_percent", (used.doubleValue() / max.doubleValue()) * 100);
                } else {
                    poolInfo.put("utilization_percent", null);
                }
            }
            
            result.put("pools", poolsMap.values());
            
        } catch (Exception e) {
            LOG.error("Error in getMemoryPoolsBreakdown", e);
            result.put("error", "Error fetching memory pools: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show heap and non-heap memory usage trends over a specified time window with statistics.
            Provides time-series data for trend analysis and pattern identification.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Analyzing memory growth patterns over time
            - Identifying memory spikes or drops
            - Correlating memory changes with events
            - Detecting gradual memory leaks
            
            Returns: Time-series samples with timestamps, min/max/avg statistics for heap and non-heap.
            """)
    public Map<String, Object> getMemoryOverTime(
            String lookback,
            String step) {
        
        LOG.infof("Executing getMemoryOverTime tool with lookback=%s, step=%s", lookback, step);
        
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
            
            // Get heap usage over time
            List<MetricValue> heapValues = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", lookback, step);
            
            if (!heapValues.isEmpty()) {
                Map<String, Object> heapData = new HashMap<>();
                heapData.put("samples", heapValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "used_bytes", v.getValue().longValue()
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(heapValues);
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("min_bytes", stats.get("min").longValue());
                statistics.put("max_bytes", stats.get("max").longValue());
                statistics.put("avg_bytes", stats.get("avg").longValue());
                statistics.put("current_bytes", stats.get("current").longValue());
                
                heapData.put("statistics", statistics);
                result.put("heap", heapData);
                result.put("start_time", heapValues.get(0).getTimestamp().toString());
            }
            
            // Get non-heap usage over time
            List<MetricValue> nonHeapValues = metricsService.getMetricRange(
                "jvm_memory_nonheap_used_bytes", lookback, step);
            
            if (!nonHeapValues.isEmpty()) {
                Map<String, Object> nonHeapData = new HashMap<>();
                nonHeapData.put("samples", nonHeapValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "used_bytes", v.getValue().longValue()
                    ))
                    .toList());
                
                Map<String, Double> stats = metricsService.calculateStatistics(nonHeapValues);
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("min_bytes", stats.get("min").longValue());
                statistics.put("max_bytes", stats.get("max").longValue());
                statistics.put("avg_bytes", stats.get("avg").longValue());
                statistics.put("current_bytes", stats.get("current").longValue());
                
                nonHeapData.put("statistics", statistics);
                result.put("non_heap", nonHeapData);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getMemoryOverTime", e);
            result.put("error", "Error fetching memory over time: " + e.getMessage());
        }
        
        return result;
    }
}

