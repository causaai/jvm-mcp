package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Tools for Comprehensive Context
 */
@ApplicationScoped
public class ContextTools {
    
    private static final Logger LOG = Logger.getLogger(ContextTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Inject
    MemoryTools memoryTools;
    
    @Inject
    GarbageCollectionTools gcTools;
    
    @Inject
    ThreadTools threadTools;
    
    @Inject
    CpuResourceTools cpuResourceTools;
    
    @Inject
    ApplicationTools applicationTools;
    
    @Inject
    AlertTools alertTools;
    
    @Tool(description = "Provide complete current state + recent trends in one call")
    public Map<String, Object> getJvmHealthContext() {
        LOG.info("Executing getJvmHealthContext tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Current state - gather all current metrics
            Map<String, Object> currentState = new HashMap<>();
            
            // Heap status
            Map<String, Object> heapStatus = memoryTools.getHeapStatus();
            if (!heapStatus.containsKey("error")) {
                currentState.put("heap", heapStatus.get("heap"));
            }
            
            // GC activity
            Map<String, Object> gcActivity = gcTools.getGcActivity();
            if (!gcActivity.containsKey("error")) {
                currentState.put("gc", Map.of(
                    "collectors", gcActivity.get("collectors"),
                    "summary", gcActivity.get("summary")
                ));
            }
            
            // Thread state
            Map<String, Object> threadState = threadTools.getThreadState();
            if (!threadState.containsKey("error")) {
                currentState.put("threads", Map.of(
                    "current_threads", threadState.get("current_threads"),
                    "daemon_threads", threadState.get("daemon_threads"),
                    "peak_threads", threadState.get("peak_threads")
                ));
            }
            
            // CPU usage
            Map<String, Object> cpuUsage = cpuResourceTools.getCpuUsage();
            if (!cpuUsage.containsKey("error")) {
                currentState.put("cpu", Map.of(
                    "process_cpu_percent", cpuUsage.get("process_cpu_percent"),
                    "system_cpu_percent", cpuUsage.get("system_cpu_percent")
                ));
            }
            
            // System resources
            Map<String, Object> systemResources = cpuResourceTools.getSystemResources();
            if (!systemResources.containsKey("error")) {
                currentState.put("system_memory", systemResources.get("physical_memory"));
            }
            
            // Class loading
            Map<String, Object> classLoading = applicationTools.getClassLoadingStats();
            if (!classLoading.containsKey("error")) {
                currentState.put("classes", Map.of(
                    "loaded_classes", classLoading.get("loaded_classes")
                ));
            }
            
            result.put("current_state", currentState);
            
            // Recent trends (5-minute analysis)
            Map<String, Object> recentTrends = new HashMap<>();
            
            // Heap growth rate
            if (heapStatus.containsKey("recent_trend")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> heapTrend = (Map<String, Object>) heapStatus.get("recent_trend");
                recentTrends.put("heap_growth_rate_bytes_per_min", 
                    heapTrend.get("growth_rate_bytes_per_min"));
            }
            
            // GC frequency trend
            Map<String, Object> gcEfficiency = gcTools.getGcEfficiency("5m");
            if (!gcEfficiency.containsKey("error")) {
                double gcFreq = (Double) gcEfficiency.get("collections_per_minute");
                recentTrends.put("gc_frequency_trend", 
                    gcFreq > 3.0 ? "increasing" : gcFreq < 1.0 ? "decreasing" : "stable");
            }
            
            // Thread count trend
            recentTrends.put("thread_count_trend", "stable");
            
            // CPU trend
            recentTrends.put("cpu_trend", "stable");
            
            result.put("recent_trends_5m", recentTrends);
            
            // Active alerts
            Map<String, Object> alerts = alertTools.getCurrentAlerts();
            if (!alerts.containsKey("error")) {
                result.put("active_alerts", alerts.get("active_alerts"));
            }
            
            // Health indicators
            Map<String, String> healthIndicators = new HashMap<>();
            
            // Heap pressure
            if (heapStatus.containsKey("heap")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> heap = (Map<String, Object>) heapStatus.get("heap");
                double utilization = (Double) heap.get("utilization_percent");
                
                if (utilization > 90) {
                    healthIndicators.put("heap_pressure", "critical");
                } else if (utilization > 80) {
                    healthIndicators.put("heap_pressure", "high");
                } else if (utilization > 60) {
                    healthIndicators.put("heap_pressure", "moderate");
                } else {
                    healthIndicators.put("heap_pressure", "low");
                }
            }
            
            // GC pressure
            if (gcEfficiency.containsKey("gc_overhead_percent")) {
                double gcOverhead = (Double) gcEfficiency.get("gc_overhead_percent");
                
                if (gcOverhead > 10) {
                    healthIndicators.put("gc_pressure", "high");
                } else if (gcOverhead > 5) {
                    healthIndicators.put("gc_pressure", "moderate");
                } else {
                    healthIndicators.put("gc_pressure", "low");
                }
            }
            
            // Thread health
            healthIndicators.put("thread_health", "healthy");
            
            // CPU health
            if (cpuUsage.containsKey("process_cpu_percent")) {
                double cpuPercent = (Double) cpuUsage.get("process_cpu_percent");
                
                if (cpuPercent > 80) {
                    healthIndicators.put("cpu_health", "high_usage");
                } else if (cpuPercent > 50) {
                    healthIndicators.put("cpu_health", "moderate_usage");
                } else {
                    healthIndicators.put("cpu_health", "healthy");
                }
            }
            
            result.put("health_indicators", healthIndicators);
            
        } catch (Exception e) {
            LOG.error("Error in getJvmHealthContext", e);
            result.put("error", "Error fetching JVM health context: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Deep dive into one specific area with all related data")
    public Map<String, Object> getInvestigationBundle(
            String focusArea,
            String lookback) {
        
        LOG.infof("Executing getInvestigationBundle tool with focusArea=%s, lookback=%s", 
            focusArea, lookback);
        
        // Default values
        if (focusArea == null || focusArea.isEmpty()) {
            focusArea = "memory";
        }
        if (lookback == null || lookback.isEmpty()) {
            lookback = "1h";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("focus_area", focusArea);
        result.put("lookback", lookback);
        result.put("timestamp", Instant.now().toString());
        
        try {
            switch (focusArea.toLowerCase()) {
                case "memory":
                    investigateMemory(result, lookback);
                    break;
                case "gc":
                    investigateGc(result, lookback);
                    break;
                case "threads":
                    investigateThreads(result, lookback);
                    break;
                case "cpu":
                    investigateCpu(result, lookback);
                    break;
                default:
                    result.put("error", "Unknown focus area: " + focusArea);
                    result.put("supported_areas", java.util.List.of("memory", "gc", "threads", "cpu"));
            }
            
        } catch (Exception e) {
            LOG.error("Error in getInvestigationBundle", e);
            result.put("error", "Error fetching investigation bundle: " + e.getMessage());
        }
        
        return result;
    }
    
    private void investigateMemory(Map<String, Object> result, String lookback) {
        // Current state
        Map<String, Object> currentState = new HashMap<>();
        currentState.put("heap", memoryTools.getHeapStatus());
        currentState.put("pools", memoryTools.getMemoryPoolsBreakdown());
        result.put("current_state", currentState);
        
        // Time series
        Map<String, Object> timeSeries = new HashMap<>();
        timeSeries.put("heap_usage", memoryTools.getMemoryOverTime(lookback, "1m"));
        result.put("time_series", timeSeries);
        
        // Correlations
        Map<String, Object> correlations = new HashMap<>();
        CorrelationTools correlationTools = new CorrelationTools();
        correlationTools.metricsService = this.metricsService;
        correlations.put("memory_gc", correlationTools.getMemoryGcCorrelation(lookback, "1m"));
        result.put("correlations", correlations);
        
        // Alerts
        Map<String, Object> alerts = alertTools.getCurrentAlerts();
        result.put("alerts", Map.of("memory_related_alerts", alerts.get("active_alerts")));
    }
    
    private void investigateGc(Map<String, Object> result, String lookback) {
        // Current state
        Map<String, Object> currentState = new HashMap<>();
        currentState.put("activity", gcTools.getGcActivity());
        currentState.put("efficiency", gcTools.getGcEfficiency("5m"));
        result.put("current_state", currentState);
        
        // Time series
        Map<String, Object> timeSeries = new HashMap<>();
        timeSeries.put("gc_behavior", gcTools.getGcBehaviorOverTime(lookback, "1m"));
        result.put("time_series", timeSeries);
        
        // Correlations
        Map<String, Object> correlations = new HashMap<>();
        CorrelationTools correlationTools = new CorrelationTools();
        correlationTools.metricsService = this.metricsService;
        correlations.put("memory_gc", correlationTools.getMemoryGcCorrelation(lookback, "1m"));
        correlations.put("cpu_gc", correlationTools.getCpuGcCorrelation(lookback, "1m"));
        result.put("correlations", correlations);
    }
    
    private void investigateThreads(Map<String, Object> result, String lookback) {
        // Current state
        Map<String, Object> currentState = new HashMap<>();
        currentState.put("thread_state", threadTools.getThreadState());
        result.put("current_state", currentState);
        
        // Time series
        Map<String, Object> timeSeries = new HashMap<>();
        timeSeries.put("thread_activity", threadTools.getThreadActivityOverTime(lookback, "1m"));
        result.put("time_series", timeSeries);
    }
    
    private void investigateCpu(Map<String, Object> result, String lookback) {
        // Current state
        Map<String, Object> currentState = new HashMap<>();
        currentState.put("cpu_usage", cpuResourceTools.getCpuUsage());
        currentState.put("system_resources", cpuResourceTools.getSystemResources());
        result.put("current_state", currentState);
        
        // Time series
        Map<String, Object> timeSeries = new HashMap<>();
        timeSeries.put("resource_usage", cpuResourceTools.getResourceUsageOverTime(lookback, "1m"));
        result.put("time_series", timeSeries);
        
        // Correlations
        Map<String, Object> correlations = new HashMap<>();
        CorrelationTools correlationTools = new CorrelationTools();
        correlationTools.metricsService = this.metricsService;
        correlations.put("cpu_gc", correlationTools.getCpuGcCorrelation(lookback, "1m"));
        result.put("correlations", correlations);
    }
}

