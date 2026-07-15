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
 * MCP Tools for CPU & Resource Investigation
 */
@ApplicationScoped
public class CpuResourceTools {
    
    private static final Logger LOG = Logger.getLogger(CpuResourceTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = "Show JVM and system CPU consumption")
    public Map<String, Object> getCpuUsage() {
        LOG.info("Executing getCpuUsage tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get current CPU metrics (values are 0.0-1.0, convert to percentage)
            Double processCpu = metricsService.getCurrentMetricValue("jvm_process_cpu_load");
            Double systemCpu = metricsService.getCurrentMetricValue("jvm_system_cpu_load");
            
            if (processCpu == null || systemCpu == null) {
                result.put("error", "Unable to fetch CPU metrics from data source");
                return result;
            }
            
            double processCpuPercent = processCpu * 100;
            double systemCpuPercent = systemCpu * 100;
            
            result.put("process_cpu_percent", Math.round(processCpuPercent * 10.0) / 10.0);
            result.put("system_cpu_percent", Math.round(systemCpuPercent * 10.0) / 10.0);
            
            if (systemCpuPercent > 0) {
                double processShare = (processCpuPercent / systemCpuPercent) * 100;
                result.put("process_cpu_share_of_system", Math.round(processShare * 10.0) / 10.0);
            }
            
            // Get 5-minute averages
            List<MetricValue> processValues = metricsService.getMetricRange(
                "jvm_process_cpu_load", "5m", "30s");
            List<MetricValue> systemValues = metricsService.getMetricRange(
                "jvm_system_cpu_load", "5m", "30s");
            
            if (!processValues.isEmpty() && !systemValues.isEmpty()) {
                Map<String, Object> recent5m = new HashMap<>();
                
                Map<String, Double> processStats = metricsService.calculateStatistics(processValues);
                recent5m.put("avg_process_cpu_percent", 
                    Math.round(processStats.get("avg") * 100 * 10.0) / 10.0);
                recent5m.put("max_process_cpu_percent", 
                    Math.round(processStats.get("max") * 100 * 10.0) / 10.0);
                
                Map<String, Double> systemStats = metricsService.calculateStatistics(systemValues);
                recent5m.put("avg_system_cpu_percent", 
                    Math.round(systemStats.get("avg") * 100 * 10.0) / 10.0);
                
                result.put("recent_5m", recent5m);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getCpuUsage", e);
            result.put("error", "Error fetching CPU usage: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Show host-level memory availability")
    public Map<String, Object> getSystemResources() {
        LOG.info("Executing getSystemResources tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get system memory metrics
            Double totalMemory = metricsService.getCurrentMetricValue("jvm_os_total_physical_memory_bytes");
            Double freeMemory = metricsService.getCurrentMetricValue("jvm_os_free_physical_memory_bytes");
            
            if (totalMemory == null || freeMemory == null) {
                result.put("error", "Unable to fetch system resource metrics from data source");
                return result;
            }
            
            long total = totalMemory.longValue();
            long free = freeMemory.longValue();
            long used = total - free;
            
            Map<String, Object> physicalMemory = new HashMap<>();
            physicalMemory.put("total_bytes", total);
            physicalMemory.put("free_bytes", free);
            physicalMemory.put("used_bytes", used);
            
            if (total > 0) {
                double utilization = (used * 100.0) / total;
                physicalMemory.put("utilization_percent", Math.round(utilization * 10.0) / 10.0);
            }
            
            result.put("physical_memory", physicalMemory);
            
        } catch (Exception e) {
            LOG.error("Error in getSystemResources", e);
            result.put("error", "Error fetching system resources: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Show CPU and system memory trends over time")
    public Map<String, Object> getResourceUsageOverTime(
            String lookback,
            String step) {
        
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
            
            // Get CPU usage over time
            List<MetricValue> processCpuValues = metricsService.getMetricRange(
                "jvm_process_cpu_load", lookback, step);
            List<MetricValue> systemCpuValues = metricsService.getMetricRange(
                "jvm_system_cpu_load", lookback, step);
            
            if (!processCpuValues.isEmpty() && !systemCpuValues.isEmpty()) {
                Map<String, Object> cpu = new HashMap<>();
                
                cpu.put("process_samples", processCpuValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "percent", Math.round(v.getValue() * 100 * 10.0) / 10.0
                    ))
                    .toList());
                
                cpu.put("system_samples", systemCpuValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "percent", Math.round(v.getValue() * 100 * 10.0) / 10.0
                    ))
                    .toList());
                
                result.put("cpu", cpu);
                result.put("start_time", processCpuValues.get(0).getTimestamp().toString());
            }
            
            // Get system memory over time
            List<MetricValue> freeMemoryValues = metricsService.getMetricRange(
                "jvm_os_free_physical_memory_bytes", lookback, step);
            
            if (!freeMemoryValues.isEmpty()) {
                Map<String, Object> systemMemory = new HashMap<>();
                
                systemMemory.put("free_memory_samples", freeMemoryValues.stream()
                    .map(v -> Map.of(
                        "timestamp", v.getTimestamp().toString(),
                        "bytes", v.getValue().longValue()
                    ))
                    .toList());
                
                result.put("system_memory", systemMemory);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getResourceUsageOverTime", e);
            result.put("error", "Error fetching resource usage over time: " + e.getMessage());
        }
        
        return result;
    }
}

