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
 * MCP Tools for Comparative Analysis
 */
@ApplicationScoped
public class ComparativeTools {
    
    private static final Logger LOG = Logger.getLogger(ComparativeTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = "Compare current state to a baseline period")
    public Map<String, Object> getCurrentVsBaseline(String baselineTime) {
        LOG.infof("Executing getCurrentVsBaseline tool with baselineTime=%s", baselineTime);
        
        // Default to 1 hour ago
        if (baselineTime == null || baselineTime.isEmpty()) {
            baselineTime = "1h";
        }
        
        Map<String, Object> result = new HashMap<>();
        Instant currentTime = Instant.now();
        result.put("current_time", currentTime.toString());
        
        try {
            // Parse baseline time (support "1h ago" format or duration)
            Duration baselineDuration;
            if (baselineTime.contains("ago")) {
                baselineTime = baselineTime.replace(" ago", "").trim();
            }
            baselineDuration = parseDuration(baselineTime);
            
            Instant baselineTimestamp = currentTime.minus(baselineDuration);
            result.put("baseline_time", baselineTimestamp.toString());
            
            Map<String, Object> comparison = new HashMap<>();
            
            // Compare heap usage
            Double currentHeap = metricsService.getCurrentMetricValue("jvm_memory_heap_used_bytes");
            List<MetricValue> baselineHeapValues = metricsService.executeQueryAtTime(
                "jvm_memory_heap_used_bytes", baselineTimestamp);
            
            if (currentHeap != null && !baselineHeapValues.isEmpty()) {
                double baselineHeap = baselineHeapValues.get(0).getValue();
                Map<String, Object> heapComparison = new HashMap<>();
                heapComparison.put("baseline_bytes", (long) baselineHeap);
                heapComparison.put("current_bytes", currentHeap.longValue());
                heapComparison.put("delta_bytes", currentHeap.longValue() - (long) baselineHeap);
                
                double deltaPercent = ((currentHeap - baselineHeap) / baselineHeap) * 100;
                heapComparison.put("delta_percent", Math.round(deltaPercent * 10.0) / 10.0);
                heapComparison.put("trend", deltaPercent > 0 ? "increasing" : "decreasing");
                
                comparison.put("heap_usage", heapComparison);
            }
            
            // Compare GC frequency
            String gcFreqQuery = "rate(jvm_gc_collection_seconds_count[1m]) * 60";
            List<MetricValue> currentGcFreq = metricsService.executeQueryAtTime(gcFreqQuery, currentTime);
            List<MetricValue> baselineGcFreq = metricsService.executeQueryAtTime(gcFreqQuery, baselineTimestamp);
            
            if (!currentGcFreq.isEmpty() && !baselineGcFreq.isEmpty()) {
                double currentFreq = currentGcFreq.get(0).getValue();
                double baselineFreq = baselineGcFreq.get(0).getValue();
                
                Map<String, Object> gcComparison = new HashMap<>();
                gcComparison.put("baseline_per_min", Math.round(baselineFreq * 10.0) / 10.0);
                gcComparison.put("current_per_min", Math.round(currentFreq * 10.0) / 10.0);
                gcComparison.put("delta_per_min", Math.round((currentFreq - baselineFreq) * 10.0) / 10.0);
                
                if (baselineFreq > 0) {
                    double deltaPercent = ((currentFreq - baselineFreq) / baselineFreq) * 100;
                    gcComparison.put("delta_percent", Math.round(deltaPercent * 10.0) / 10.0);
                }
                gcComparison.put("trend", currentFreq > baselineFreq ? "increasing" : "decreasing");
                
                comparison.put("gc_frequency", gcComparison);
            }
            
            // Compare thread count
            Double currentThreads = metricsService.getCurrentMetricValue("jvm_threads_current");
            List<MetricValue> baselineThreads = metricsService.executeQueryAtTime(
                "jvm_threads_current", baselineTimestamp);
            
            if (currentThreads != null && !baselineThreads.isEmpty()) {
                long current = currentThreads.longValue();
                long baseline = baselineThreads.get(0).getValue().longValue();
                
                Map<String, Object> threadComparison = new HashMap<>();
                threadComparison.put("baseline", baseline);
                threadComparison.put("current", current);
                threadComparison.put("delta", current - baseline);
                
                if (baseline > 0) {
                    double deltaPercent = ((current - baseline) * 100.0) / baseline;
                    threadComparison.put("delta_percent", Math.round(deltaPercent * 10.0) / 10.0);
                }
                threadComparison.put("trend", current > baseline ? "increasing" : "decreasing");
                
                comparison.put("thread_count", threadComparison);
            }
            
            // Compare CPU usage
            Double currentCpu = metricsService.getCurrentMetricValue("jvm_process_cpu_load");
            List<MetricValue> baselineCpu = metricsService.executeQueryAtTime(
                "jvm_process_cpu_load", baselineTimestamp);
            
            if (currentCpu != null && !baselineCpu.isEmpty()) {
                double currentPercent = currentCpu * 100;
                double baselinePercent = baselineCpu.get(0).getValue() * 100;
                
                Map<String, Object> cpuComparison = new HashMap<>();
                cpuComparison.put("baseline_percent", Math.round(baselinePercent * 10.0) / 10.0);
                cpuComparison.put("current_percent", Math.round(currentPercent * 10.0) / 10.0);
                cpuComparison.put("delta_percent", Math.round((currentPercent - baselinePercent) * 10.0) / 10.0);
                cpuComparison.put("trend", currentPercent > baselinePercent ? "increasing" : "decreasing");
                
                comparison.put("cpu_usage", cpuComparison);
            }
            
            result.put("comparison", comparison);
            
        } catch (Exception e) {
            LOG.error("Error in getCurrentVsBaseline", e);
            result.put("error", "Error comparing current vs baseline: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Show what's normal vs exceptional for metrics using percentile analysis")
    public Map<String, Object> getMetricPercentiles(
            String metricCategory,
            String lookback) {
        
        LOG.infof("Executing getMetricPercentiles tool with category=%s, lookback=%s", 
            metricCategory, lookback);
        
        // Default values
        if (metricCategory == null || metricCategory.isEmpty()) {
            metricCategory = "memory";
        }
        if (lookback == null || lookback.isEmpty()) {
            lookback = "24h";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("category", metricCategory);
        result.put("lookback", lookback);
        
        try {
            Instant end = Instant.now();
            result.put("end_time", end.toString());
            
            switch (metricCategory.toLowerCase()) {
                case "memory":
                    analyzeMemoryPercentiles(result, lookback);
                    break;
                case "gc":
                    analyzeGcPercentiles(result, lookback);
                    break;
                case "threads":
                    analyzeThreadPercentiles(result, lookback);
                    break;
                case "cpu":
                    analyzeCpuPercentiles(result, lookback);
                    break;
                default:
                    result.put("error", "Unknown metric category: " + metricCategory);
                    result.put("supported_categories", List.of("memory", "gc", "threads", "cpu"));
            }
            
        } catch (Exception e) {
            LOG.error("Error in getMetricPercentiles", e);
            result.put("error", "Error calculating metric percentiles: " + e.getMessage());
        }
        
        return result;
    }
    
    private void analyzeMemoryPercentiles(Map<String, Object> result, String lookback) {
        List<MetricValue> heapValues = metricsService.getMetricRange(
            "jvm_memory_heap_used_bytes", lookback, "5m");
        
        if (!heapValues.isEmpty()) {
            result.put("start_time", heapValues.get(0).getTimestamp().toString());
            
            Map<String, Object> heapPercentiles = calculatePercentiles(heapValues);
            result.put("heap_usage_bytes", heapPercentiles);
        }
    }
    
    private void analyzeGcPercentiles(Map<String, Object> result, String lookback) {
        String query = "rate(jvm_gc_collection_seconds_count[1m]) * 60";
        // Note: For percentiles over time, we'd need range query support
        // For now, we'll use current samples
        List<MetricValue> gcValues = metricsService.getMetricRange(
            "jvm_gc_collection_seconds_count", lookback, "5m");
        
        if (!gcValues.isEmpty()) {
            result.put("start_time", gcValues.get(0).getTimestamp().toString());
            
            Map<String, Object> gcPercentiles = calculatePercentiles(gcValues);
            result.put("gc_frequency_per_min", gcPercentiles);
        }
    }
    
    private void analyzeThreadPercentiles(Map<String, Object> result, String lookback) {
        List<MetricValue> threadValues = metricsService.getMetricRange(
            "jvm_threads_current", lookback, "5m");
        
        if (!threadValues.isEmpty()) {
            result.put("start_time", threadValues.get(0).getTimestamp().toString());
            
            Map<String, Object> threadPercentiles = calculatePercentiles(threadValues);
            result.put("thread_count", threadPercentiles);
        }
    }
    
    private void analyzeCpuPercentiles(Map<String, Object> result, String lookback) {
        List<MetricValue> cpuValues = metricsService.getMetricRange(
            "jvm_process_cpu_load", lookback, "5m");
        
        if (!cpuValues.isEmpty()) {
            result.put("start_time", cpuValues.get(0).getTimestamp().toString());
            
            // Convert to percentages
            List<MetricValue> cpuPercent = cpuValues.stream()
                .map(v -> MetricValue.builder()
                    .timestamp(v.getTimestamp())
                    .value(v.getValue() * 100)
                    .metricName(v.getMetricName())
                    .build())
                .toList();
            
            Map<String, Object> cpuPercentiles = calculatePercentiles(cpuPercent);
            result.put("cpu_usage_percent", cpuPercentiles);
        }
    }
    
    private Map<String, Object> calculatePercentiles(List<MetricValue> values) {
        Map<String, Object> percentiles = new HashMap<>();
        
        if (values.isEmpty()) {
            return percentiles;
        }
        
        List<Double> sortedValues = values.stream()
            .map(MetricValue::getValue)
            .sorted()
            .toList();
        
        int size = sortedValues.size();
        
        // Calculate percentiles
        percentiles.put("p50", getPercentile(sortedValues, 50));
        percentiles.put("p95", getPercentile(sortedValues, 95));
        percentiles.put("p99", getPercentile(sortedValues, 99));
        
        // Current value
        double currentValue = values.get(values.size() - 1).getValue();
        percentiles.put("current", Math.round(currentValue * 10.0) / 10.0);
        
        // Calculate current percentile
        long belowCurrent = sortedValues.stream().filter(v -> v <= currentValue).count();
        int currentPercentile = (int) ((belowCurrent * 100.0) / size);
        percentiles.put("current_percentile", currentPercentile);
        
        return percentiles;
    }
    
    private double getPercentile(List<Double> sortedValues, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return Math.round(sortedValues.get(index) * 10.0) / 10.0;
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
                default -> Duration.ofHours(value);
            };
        } catch (Exception e) {
            LOG.warnf("Invalid duration format: %s, defaulting to 1 hour", duration);
            return Duration.ofHours(1);
        }
    }
}

