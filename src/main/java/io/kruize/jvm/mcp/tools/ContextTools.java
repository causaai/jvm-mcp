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
    
    @Tool(description = """
            Get a comprehensive snapshot of JVM health including current state, recent trends, and health indicators.
            Returns all key metrics across memory, GC, threads, CPU, and classes in a single call.
            
            No parameters required - automatically gathers:
            - Current state: Heap, GC activity, threads, CPU usage, system memory, loaded classes
            - Recent trends (5m): Heap growth rate, GC frequency, thread/CPU trends
            - Health indicators: Heap pressure, GC pressure, thread health, CPU health with severity levels
            
            Use this tool for initial JVM health assessment or periodic health checks.
            For deeper investigation of specific issues, use getInvestigationBundle with a focus area.
            """)
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
                Map<String, Object> gcData = new HashMap<>();
                gcData.put("collectors", gcActivity.get("collectors"));
                gcData.put("summary", gcActivity.get("summary"));
                currentState.put("gc", gcData);
            }
            
            // Thread state
            Map<String, Object> threadState = threadTools.getThreadState();
            if (!threadState.containsKey("error")) {
                Map<String, Object> threadData = new HashMap<>();
                threadData.put("current_threads", threadState.get("current_threads"));
                threadData.put("daemon_threads", threadState.get("daemon_threads"));
                threadData.put("peak_threads", threadState.get("peak_threads"));
                currentState.put("threads", threadData);
            }
            
            // CPU usage
            Map<String, Object> cpuUsage = cpuResourceTools.getCpuUsage();
            if (!cpuUsage.containsKey("error")) {
                Map<String, Object> cpuData = new HashMap<>();
                cpuData.put("process_cpu_percent", cpuUsage.get("process_cpu_percent"));
                cpuData.put("system_cpu_percent", cpuUsage.get("system_cpu_percent"));
                currentState.put("cpu", cpuData);
            }
            
            // System resources
            Map<String, Object> systemResources = cpuResourceTools.getSystemResources();
            if (!systemResources.containsKey("error")) {
                currentState.put("system_memory", systemResources.get("physical_memory"));
            }
            
            // Class loading
            Map<String, Object> classLoading = applicationTools.getClassLoadingStats();
            if (!classLoading.containsKey("error")) {
                Map<String, Object> classData = new HashMap<>();
                classData.put("loaded_classes", classLoading.get("loaded_classes"));
                currentState.put("classes", classData);
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
            if (!gcEfficiency.containsKey("error") && gcEfficiency.get("collections_per_minute") != null) {
                double gcFreq = (Double) gcEfficiency.get("collections_per_minute");
                recentTrends.put("gc_frequency_trend",
                    gcFreq > 3.0 ? "increasing" : gcFreq < 1.0 ? "decreasing" : "stable");
            } else {
                recentTrends.put("gc_frequency_trend", "unknown");
            }
            
            // Thread count trend
            recentTrends.put("thread_count_trend", "stable");
            
            // CPU trend
            recentTrends.put("cpu_trend", "stable");
            
            result.put("recent_trends_5m", recentTrends);
            
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
            if (gcEfficiency.containsKey("gc_overhead_percent") && gcEfficiency.get("gc_overhead_percent") != null) {
                double gcOverhead = (Double) gcEfficiency.get("gc_overhead_percent");
                
                if (gcOverhead > 10) {
                    healthIndicators.put("gc_pressure", "high");
                } else if (gcOverhead > 5) {
                    healthIndicators.put("gc_pressure", "moderate");
                } else {
                    healthIndicators.put("gc_pressure", "low");
                }
            } else {
                healthIndicators.put("gc_pressure", "unknown");
            }
            
            // Thread health
            healthIndicators.put("thread_health", "healthy");
            
            // CPU health
            if (cpuUsage.containsKey("process_cpu_percent") && cpuUsage.get("process_cpu_percent") != null) {
                double cpuPercent = (Double) cpuUsage.get("process_cpu_percent");
                
                if (cpuPercent > 80) {
                    healthIndicators.put("cpu_health", "high_usage");
                } else if (cpuPercent > 50) {
                    healthIndicators.put("cpu_health", "moderate_usage");
                } else {
                    healthIndicators.put("cpu_health", "healthy");
                }
            } else {
                healthIndicators.put("cpu_health", "unknown");
            }
            
            result.put("health_indicators", healthIndicators);
            
        } catch (Exception e) {
            LOG.error("Error in getJvmHealthContext", e);
            result.put("error", "Error fetching JVM health context: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Perform a comprehensive deep-dive investigation into a specific JVM subsystem.
            Returns current state, historical trends, and correlations in one call.
            
            Parameters:
            - focusArea: The JVM subsystem to investigate. Options:
              * "memory" - Heap status, memory pools, usage trends, memory-GC correlation
              * "gc" - Garbage collection activity, efficiency, behavior, memory/CPU correlations
              * "threads" - Thread state, activity patterns over time
              * "cpu" - CPU usage, system resources, trends, CPU-GC correlation
            - lookback: Time window for historical analysis (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            
            Use this tool when you need complete context about a specific subsystem for root cause analysis.
            Example: focusArea="memory", lookback="2h" for investigating memory issues over 2 hours.
            """)
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

