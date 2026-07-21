package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for Correlation Data Analysis
 */
@ApplicationScoped
public class CorrelationTools {
    
    private static final Logger LOG = Logger.getLogger(CorrelationTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = """
            Show synchronized heap usage and GC activity for cause-effect analysis.
            Correlates heap utilization with GC frequency and time to identify if GC is triggered by memory pressure.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Determining if high heap usage causes frequent GC
            - Analyzing GC effectiveness in reclaiming memory
            - Identifying memory pressure patterns
            - Understanding heap-GC relationship
            
            Returns: Synchronized time-series with heap usage/utilization, GC frequency, GC time, plus correlation coefficients.
            """)
    public Map<String, Object> getMemoryGcCorrelation(
            @Nullable String lookback,
            @Nullable String step) {
        
        LOG.infof("Executing getMemoryGcCorrelation tool with lookback=%s, step=%s", lookback, step);
        
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
            
            // Get heap max for utilization calculation
            Double heapMax = metricsService.getCurrentMetricValue("jvm_memory_heap_max_bytes");
            
            // Get GC frequency (collections per minute)
            String gcFreqQuery = "rate(jvm_gc_collection_seconds_count[1m]) * 60";
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> gcFreqData = metricsService.executeQuery(gcFreqQuery);
            
            // Get GC time percentage
            String gcTimeQuery = "rate(jvm_gc_collection_seconds_sum[1m]) * 100";
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> gcTimeData = metricsService.executeQuery(gcTimeQuery);
            
            if (!heapValues.isEmpty() && !gcFreqData.isEmpty() && !gcTimeData.isEmpty()) {
                List<MetricValue> gcFreqValues = gcFreqData.get(0).getValues();
                List<MetricValue> gcTimeValues = gcTimeData.get(0).getValues();
                
                // Synchronize data points
                List<Map<String, Object>> synchronizedData = new ArrayList<>();
                int minSize = Math.min(Math.min(heapValues.size(), gcFreqValues.size()), gcTimeValues.size());
                
                for (int i = 0; i < minSize; i++) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    
                    MetricValue heap = heapValues.get(i);
                    MetricValue gcFreq = gcFreqValues.get(i);
                    MetricValue gcTime = gcTimeValues.get(i);
                    
                    dataPoint.put("timestamp", heap.getTimestamp().toString());
                    dataPoint.put("heap_used_bytes", heap.getValue().longValue());
                    
                    if (heapMax != null && heapMax > 0) {
                        double utilization = (heap.getValue() / heapMax) * 100;
                        dataPoint.put("heap_utilization_percent", Math.round(utilization * 10.0) / 10.0);
                    }
                    
                    dataPoint.put("gc_collections_per_min", Math.round(gcFreq.getValue() * 10.0) / 10.0);
                    dataPoint.put("gc_time_percent", Math.round(gcTime.getValue() * 10.0) / 10.0);
                    
                    synchronizedData.add(dataPoint);
                }
                
                result.put("synchronized_data", synchronizedData);
                result.put("start_time", heapValues.get(0).getTimestamp().toString());
                
                // Calculate correlation coefficients
                Map<String, Object> correlationAnalysis = new HashMap<>();
                
                // Heap vs GC frequency correlation
                double heapGcFreqCorr = calculateCorrelation(
                    heapValues.stream().map(MetricValue::getValue).toList(),
                    gcFreqValues.stream().map(MetricValue::getValue).toList()
                );
                correlationAnalysis.put("heap_gc_frequency_correlation", 
                    Math.round(heapGcFreqCorr * 100.0) / 100.0);
                
                // Heap vs GC time correlation
                double heapGcTimeCorr = calculateCorrelation(
                    heapValues.stream().map(MetricValue::getValue).toList(),
                    gcTimeValues.stream().map(MetricValue::getValue).toList()
                );
                correlationAnalysis.put("heap_gc_time_correlation", 
                    Math.round(heapGcTimeCorr * 100.0) / 100.0);
                
                result.put("correlation_analysis", correlationAnalysis);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getMemoryGcCorrelation", e);
            result.put("error", "Error fetching memory-GC correlation: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show if garbage collection is consuming CPU resources (OpenJ9 compatible).
            Correlates CPU usage with GC time to identify if GC is a major CPU consumer.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Investigating high CPU usage issues
            - Determining if GC is CPU-bound
            - Analyzing CPU overhead from garbage collection
            - Identifying if GC tuning could reduce CPU usage
            
            Note: Uses process_cpu_seconds_total for OpenJ9 compatibility.
            Returns: Synchronized time-series with CPU cores and GC time %, plus correlation coefficient and averages.
            """)
    public Map<String, Object> getCpuGcCorrelation(
            @Nullable String lookback,
            @Nullable String step) {
        
        LOG.infof("Executing getCpuGcCorrelation tool with lookback=%s, step=%s", lookback, step);
        
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
            
            // Get CPU rate over time (compatible with OpenJ9)
            List<MetricValue> cpuValues = metricsService.getMetricRange(
                "rate(process_cpu_seconds_total[1m])", lookback, step);
            
            // Get GC time percentage
            String gcTimeQuery = "rate(jvm_gc_collection_seconds_sum[1m]) * 100";
            List<io.kruize.jvm.mcp.model.MetricTimeSeries> gcTimeData = metricsService.executeQuery(gcTimeQuery);
            
            if (!cpuValues.isEmpty() && !gcTimeData.isEmpty()) {
                List<MetricValue> gcTimeValues = gcTimeData.get(0).getValues();
                
                // Synchronize data points
                List<Map<String, Object>> synchronizedData = new ArrayList<>();
                int minSize = Math.min(cpuValues.size(), gcTimeValues.size());
                
                for (int i = 0; i < minSize; i++) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    
                    MetricValue cpu = cpuValues.get(i);
                    MetricValue gcTime = gcTimeValues.get(i);
                    
                    // CPU is in cores, GC time is in percent
                    double cpuCores = cpu.getValue();
                    double gcTimePercent = gcTime.getValue();
                    
                    dataPoint.put("timestamp", cpu.getTimestamp().toString());
                    dataPoint.put("cpu_cores", Math.round(cpuCores * 1000.0) / 1000.0);
                    dataPoint.put("gc_time_percent", Math.round(gcTimePercent * 10.0) / 10.0);
                    
                    synchronizedData.add(dataPoint);
                }
                
                result.put("synchronized_data", synchronizedData);
                result.put("start_time", cpuValues.get(0).getTimestamp().toString());
                
                // Calculate correlation analysis
                Map<String, Object> correlationAnalysis = new HashMap<>();
                
                // CPU cores vs GC time correlation
                double cpuGcCorr = calculateCorrelation(
                    cpuValues.stream().map(MetricValue::getValue).toList(),
                    gcTimeValues.stream().map(MetricValue::getValue).toList()
                );
                correlationAnalysis.put("cpu_gc_correlation",
                    Math.round(cpuGcCorr * 100.0) / 100.0);
                
                // Calculate average CPU and GC time
                double avgCpuCores = cpuValues.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                double avgGcTimePercent = gcTimeValues.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                correlationAnalysis.put("avg_cpu_cores", Math.round(avgCpuCores * 1000.0) / 1000.0);
                correlationAnalysis.put("avg_gc_time_percent", Math.round(avgGcTimePercent * 10.0) / 10.0);
                correlationAnalysis.put("note", "CPU in cores, GC time as percentage of total time");
                
                result.put("correlation_analysis", correlationAnalysis);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getCpuGcCorrelation", e);
            result.put("error", "Error fetching CPU-GC correlation: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Calculate Pearson correlation coefficient between two datasets
     */
    private double calculateCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) {
            return 0.0;
        }
        
        int n = x.size();
        
        // Calculate means
        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        // Calculate correlation
        double numerator = 0.0;
        double sumXSquared = 0.0;
        double sumYSquared = 0.0;
        
        for (int i = 0; i < n; i++) {
            double xDiff = x.get(i) - meanX;
            double yDiff = y.get(i) - meanY;
            
            numerator += xDiff * yDiff;
            sumXSquared += xDiff * xDiff;
            sumYSquared += yDiff * yDiff;
        }
        
        double denominator = Math.sqrt(sumXSquared * sumYSquared);
        
        if (denominator == 0.0) {
            return 0.0;
        }
        
        return numerator / denominator;
    }
}

