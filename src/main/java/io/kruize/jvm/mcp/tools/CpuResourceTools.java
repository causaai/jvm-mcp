package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for CPU & Resource Investigation
 * 
 * Note: Uses process_cpu_seconds_total (rate) instead of jvm_process_cpu_load
 * for compatibility with OpenJ9 JVM which doesn't export standard CPU metrics
 */
@ApplicationScoped
public class CpuResourceTools {
    
    private static final Logger LOG = Logger.getLogger(CpuResourceTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = """
            Show JVM CPU consumption using process metrics (OpenJ9 compatible).
            Returns CPU usage in cores (1.0 = 1 full CPU core) with 5-minute averages.
            
            No parameters required.
            
            Use this tool when:
            - Investigating high CPU usage issues
            - Checking if JVM is CPU-bound
            - Monitoring CPU consumption trends
            - Initial CPU health assessment
            
            Note: Uses process_cpu_seconds_total rate for OpenJ9 compatibility.
            Returns: Current CPU cores, 5m avg/max/min CPU cores. Multiply by 100 for percentage on single-core systems.
            """)
    public Map<String, Object> getCpuUsage() {
        LOG.info("Executing getCpuUsage tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get current CPU rate (1-minute rate of CPU seconds)
            // This is compatible with OpenJ9 which exports process_cpu_seconds_total
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> cpuData =
                metricsService.executeQuery("rate(process_cpu_seconds_total[1m])");
            
            if (cpuData.isEmpty() || cpuData.get(0).getValues().isEmpty()) {
                result.put("error", "Unable to fetch CPU metrics from data source");
                result.put("note", "Ensure process_cpu_seconds_total metric is available");
                return result;
            }
            
            double processCpuRate = cpuData.get(0).getValues().get(0).getValue();
            
            // CPU rate is in cores (1.0 = 1 full CPU core)
            // Convert to percentage (assuming single core for compatibility)
            result.put("process_cpu_cores", Math.round(processCpuRate * 1000.0) / 1000.0);
            result.put("process_cpu_percent", Math.round(processCpuRate * 100.0 * 10.0) / 10.0);
            result.put("system_cpu_percent", null); // Not available from process metrics
            result.put("note", "CPU usage in cores (1.0 = 1 full CPU core). Percentage assumes single-core system.");
            
            // Get 5-minute averages
            List<MetricValue> processValues = metricsService.getMetricRange(
                "rate(process_cpu_seconds_total[1m])", "5m", "30s");
            
            if (!processValues.isEmpty()) {
                Map<String, Object> recent5m = new HashMap<>();
                
                Map<String, Double> processStats = metricsService.calculateStatistics(processValues);
                recent5m.put("avg_cpu_cores", 
                    Math.round(processStats.get("avg") * 1000.0) / 1000.0);
                recent5m.put("max_cpu_cores", 
                    Math.round(processStats.get("max") * 1000.0) / 1000.0);
                recent5m.put("min_cpu_cores", 
                    Math.round(processStats.get("min") * 1000.0) / 1000.0);
                
                result.put("recent_5m", recent5m);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getCpuUsage", e);
            result.put("error", "Error fetching CPU usage: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show process-level resource usage including resident/virtual memory and file descriptors (OpenJ9 compatible).
            Provides system resource metrics beyond JVM heap.
            
            No parameters required.
            
            Use this tool when:
            - Investigating native memory issues
            - Checking file descriptor exhaustion
            - Monitoring process memory vs heap memory
            - Analyzing off-heap memory usage
            
            Returns: Resident memory (physical RAM), virtual memory (address space), file descriptors (open/max/utilization).
            """)
    public Map<String, Object> getSystemResources() {
        LOG.info("Executing getSystemResources tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get process memory metrics (available in OpenJ9)
            Double residentMemory = metricsService.getCurrentMetricValue("process_resident_memory_bytes");
            Double virtualMemory = metricsService.getCurrentMetricValue("process_virtual_memory_bytes");
            
            // Get file descriptors
            Double openFds = metricsService.getCurrentMetricValue("process_open_fds");
            Double maxFds = metricsService.getCurrentMetricValue("process_max_fds");
            
            if (residentMemory == null || virtualMemory == null) {
                result.put("error", "Unable to fetch process resource metrics from data source");
                return result;
            }
            
            Map<String, Object> processMemory = new HashMap<>();
            processMemory.put("resident_bytes", residentMemory.longValue());
            processMemory.put("virtual_bytes", virtualMemory.longValue());
            processMemory.put("note", "Resident = physical RAM used, Virtual = total address space");
            
            result.put("process_memory", processMemory);
            
            if (openFds != null && maxFds != null) {
                Map<String, Object> fileDescriptors = new HashMap<>();
                fileDescriptors.put("open", openFds.intValue());
                fileDescriptors.put("max", maxFds.intValue());
                fileDescriptors.put("utilization_percent", 
                    Math.round((openFds / maxFds) * 100 * 10.0) / 10.0);
                result.put("file_descriptors", fileDescriptors);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getSystemResources", e);
            result.put("error", "Error fetching system resources: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show CPU cores, resident memory, and file descriptor trends over time (OpenJ9 compatible).
            Provides time-series data for resource usage pattern analysis.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Analyzing CPU usage patterns over time
            - Detecting CPU spikes or sustained high usage
            - Monitoring native memory growth
            - Tracking file descriptor leaks
            
            Returns: Time-series of CPU cores, resident memory bytes, and file descriptor counts.
            """)
    public Map<String, Object> getResourceUsageOverTime(
            @Nullable String lookback,
            @Nullable String step) {
        
        LOG.infof("Executing getResourceUsageOverTime tool with lookback=%s, step=%s", lookback, step);
        
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
            
            // Get CPU rate over time (1-minute rate)
            List<MetricValue> cpuValues = metricsService.getMetricRange(
                "rate(process_cpu_seconds_total[1m])", lookback, step);
            
            if (!cpuValues.isEmpty()) {
                Map<String, Object> cpu = new HashMap<>();
                
                cpu.put("samples", cpuValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "cores", Math.round(v.getValue() * 1000.0) / 1000.0
                    ))
                    .toList());
                
                result.put("cpu_cores", cpu);
                result.put("start_time", cpuValues.get(0).getTimestamp().toString());
            }
            
            // Get resident memory over time
            List<MetricValue> residentMemoryValues = metricsService.getMetricRange(
                "process_resident_memory_bytes", lookback, step);
            
            if (!residentMemoryValues.isEmpty()) {
                Map<String, Object> memory = new HashMap<>();
                
                memory.put("samples", residentMemoryValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "bytes", v.getValue().longValue()
                    ))
                    .toList());
                
                result.put("resident_memory", memory);
            }
            
            // Get file descriptors over time
            List<MetricValue> fdValues = metricsService.getMetricRange(
                "process_open_fds", lookback, step);
            
            if (!fdValues.isEmpty()) {
                Map<String, Object> fds = new HashMap<>();
                
                fds.put("samples", fdValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "count", v.getValue().intValue()
                    ))
                    .toList());
                
                result.put("file_descriptors", fds);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getResourceUsageOverTime", e);
            result.put("error", "Error fetching resource usage over time: " + e.getMessage());
        }
        
        return result;
    }
}

// Made with Bob
