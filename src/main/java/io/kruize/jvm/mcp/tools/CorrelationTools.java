package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
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
    
    @Tool(description = "Show synchronized heap usage and GC activity for cause-effect analysis")
    public Map<String, Object> getMemoryGcCorrelation(
            String lookback,
            String step) {
        
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
    
    @Tool(description = "Show if GC is consuming CPU resources")
    public Map<String, Object> getCpuGcCorrelation(
            String lookback,
            String step) {
        
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
            
            // Get CPU usage over time
            List<MetricValue> cpuValues = metricsService.getMetricRange(
                "jvm_process_cpu_load", lookback, step);
            
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
                    
                    double processCpuPercent = cpu.getValue() * 100;
                    double gcTimePercent = gcTime.getValue();
                    double nonGcCpuPercent = Math.max(0, processCpuPercent - gcTimePercent);
                    
                    dataPoint.put("timestamp", cpu.getTimestamp().toString());
                    dataPoint.put("process_cpu_percent", Math.round(processCpuPercent * 10.0) / 10.0);
                    dataPoint.put("gc_time_percent", Math.round(gcTimePercent * 10.0) / 10.0);
                    dataPoint.put("non_gc_cpu_percent", Math.round(nonGcCpuPercent * 10.0) / 10.0);
                    
                    synchronizedData.add(dataPoint);
                }
                
                result.put("synchronized_data", synchronizedData);
                result.put("start_time", cpuValues.get(0).getTimestamp().toString());
                
                // Calculate correlation analysis
                Map<String, Object> correlationAnalysis = new HashMap<>();
                
                // CPU vs GC time correlation
                double cpuGcCorr = calculateCorrelation(
                    cpuValues.stream().map(v -> v.getValue() * 100).toList(),
                    gcTimeValues.stream().map(MetricValue::getValue).toList()
                );
                correlationAnalysis.put("cpu_gc_correlation", 
                    Math.round(cpuGcCorr * 100.0) / 100.0);
                
                // Calculate average GC contribution to CPU
                double totalCpu = cpuValues.stream()
                    .mapToDouble(v -> v.getValue() * 100)
                    .average()
                    .orElse(0.0);
                double totalGcTime = gcTimeValues.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                if (totalCpu > 0) {
                    double gcContribution = (totalGcTime / totalCpu) * 100;
                    correlationAnalysis.put("gc_cpu_contribution_avg_percent", 
                        Math.round(gcContribution * 10.0) / 10.0);
                }
                
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

