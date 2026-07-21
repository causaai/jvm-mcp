package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced RCA (Root Cause Analysis) Tools for JVM Debugging
 * These tools provide deep insights for troubleshooting JVM issues
 */
@ApplicationScoped
public class AdvancedRcaTools {
    
    private static final Logger LOG = Logger.getLogger(AdvancedRcaTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    /**
     * Tool: getMemoryAllocationRate
     * Calculate memory allocation rate and identify allocation pressure
     * Critical for: Memory leaks, high GC frequency, OOM issues
     */
    @Tool(description = """
            Calculate memory allocation rate to identify allocation pressure and potential memory issues.
            Measures how fast the application is allocating memory (MB/sec, GB/hour).
            
            Parameters:
            - lookback: Time window for calculation (e.g., "5m", "10m", "30m", "1h"). Default: "5m"
            
            Use this tool when:
            - Investigating high GC frequency
            - Detecting memory allocation hotspots
            - Analyzing application memory behavior
            - Predicting when heap will fill up
            
            Returns: Allocation rate (MB/sec, GB/hour), total allocated, interpretation (CRITICAL/WARNING/MODERATE/LOW).
            """)
    public Map<String, Object> getMemoryAllocationRate(@Nullable String lookback) {
        LOG.infof("Executing getMemoryAllocationRate with lookback=%s", lookback);
        
        if (lookback == null || lookback.isEmpty()) {
            lookback = "5m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get allocation rate for nursery (young gen)
            List<MetricValue> nurseryAllocated = metricsService.getMetricRange(
                "jvm_memory_pool_allocated_bytes_total{pool=\"nursery-allocate\"}", lookback, "30s");
            
            if (!nurseryAllocated.isEmpty() && nurseryAllocated.size() > 1) {
                MetricValue first = nurseryAllocated.get(0);
                MetricValue last = nurseryAllocated.get(nurseryAllocated.size() - 1);
                
                long timeDiffSeconds = last.getTimestamp().getEpochSecond() - first.getTimestamp().getEpochSecond();
                double bytesDiff = last.getValue() - first.getValue();
                
                if (timeDiffSeconds > 0) {
                    double allocationRateMBPerSec = (bytesDiff / timeDiffSeconds) / (1024 * 1024);
                    double allocationRateGBPerHour = (allocationRateMBPerSec * 3600) / 1024;
                    
                    result.put("allocation_rate_mb_per_sec", Math.round(allocationRateMBPerSec * 100.0) / 100.0);
                    result.put("allocation_rate_gb_per_hour", Math.round(allocationRateGBPerHour * 100.0) / 100.0);
                    result.put("total_allocated_mb", Math.round((bytesDiff / (1024 * 1024)) * 100.0) / 100.0);
                    result.put("time_period_seconds", timeDiffSeconds);
                    
                    // Provide interpretation
                    String interpretation;
                    if (allocationRateMBPerSec > 100) {
                        interpretation = "CRITICAL: Very high allocation rate. Likely causing frequent GC and performance issues.";
                    } else if (allocationRateMBPerSec > 50) {
                        interpretation = "WARNING: High allocation rate. Monitor for GC pressure.";
                    } else if (allocationRateMBPerSec > 20) {
                        interpretation = "MODERATE: Normal allocation rate for busy applications.";
                    } else {
                        interpretation = "LOW: Low allocation rate. Application is memory-efficient.";
                    }
                    result.put("interpretation", interpretation);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in getMemoryAllocationRate", e);
            result.put("error", "Error calculating allocation rate: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Tool: getGcPressureAnalysis
     * Analyze GC pressure and identify if GC is keeping up with allocation
     * Critical for: GC tuning, performance degradation, OOM prediction
     */
    @Tool(description = """
            Analyze GC pressure to determine if garbage collection is keeping up with memory allocation.
            Calculates GC pressure score and provides expert recommendations.
            
            Parameters:
            - lookback: Time window for analysis (e.g., "5m", "10m", "30m", "1h"). Default: "5m"
            
            Use this tool when:
            - Determining if GC is struggling
            - Predicting OOM conditions
            - Evaluating GC tuning effectiveness
            - Assessing heap size adequacy
            
            Returns: GC frequency, GC time %, heap growth, GC pressure score (0-100), interpretation, and specific recommendations.
            """)
    public Map<String, Object> getGcPressureAnalysis(@Nullable String lookback) {
        LOG.infof("Executing getGcPressureAnalysis with lookback=%s", lookback);
        
        if (lookback == null || lookback.isEmpty()) {
            lookback = "5m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get GC frequency (collections per minute)
            List<MetricValue> gcFrequency = metricsService.getMetricRange(
                "rate(jvm_gc_collection_seconds_count[1m]) * 60", lookback, "30s");
            
            // Get GC time percentage
            List<MetricValue> gcTimePercent = metricsService.getMetricRange(
                "rate(jvm_gc_collection_seconds_sum[1m]) * 100", lookback, "30s");
            
            // Get heap usage trend
            List<MetricValue> heapUsed = metricsService.getMetricRange(
                "jvm_memory_heap_used_bytes", lookback, "30s");
            
            Double heapMax = metricsService.getCurrentMetricValue("jvm_memory_heap_max_bytes");
            
            if (!gcFrequency.isEmpty() && !gcTimePercent.isEmpty() && !heapUsed.isEmpty()) {
                // Calculate averages
                double avgGcFreq = gcFrequency.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                double avgGcTime = gcTimePercent.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                double maxGcTime = gcTimePercent.stream()
                    .mapToDouble(MetricValue::getValue)
                    .max()
                    .orElse(0.0);
                
                result.put("avg_gc_frequency_per_min", Math.round(avgGcFreq * 10.0) / 10.0);
                result.put("avg_gc_time_percent", Math.round(avgGcTime * 10.0) / 10.0);
                result.put("max_gc_time_percent", Math.round(maxGcTime * 10.0) / 10.0);
                
                // Analyze heap usage trend
                if (heapUsed.size() > 1 && heapMax != null) {
                    MetricValue firstHeap = heapUsed.get(0);
                    MetricValue lastHeap = heapUsed.get(heapUsed.size() - 1);
                    
                    double heapGrowth = ((lastHeap.getValue() - firstHeap.getValue()) / heapMax) * 100;
                    double currentUtilization = (lastHeap.getValue() / heapMax) * 100;
                    
                    result.put("heap_growth_percent", Math.round(heapGrowth * 100.0) / 100.0);
                    result.put("current_heap_utilization_percent", Math.round(currentUtilization * 10.0) / 10.0);
                    
                    // GC Pressure Score (0-100, higher is worse)
                    double pressureScore = (avgGcTime * 2) + (avgGcFreq / 2) + (currentUtilization / 2);
                    result.put("gc_pressure_score", Math.round(pressureScore * 10.0) / 10.0);
                    
                    // Interpretation
                    String interpretation;
                    if (pressureScore > 80 || avgGcTime > 30) {
                        interpretation = "CRITICAL: Severe GC pressure. Application spending too much time in GC. Risk of OOM or severe performance degradation.";
                    } else if (pressureScore > 50 || avgGcTime > 15) {
                        interpretation = "WARNING: High GC pressure. GC is struggling to keep up. Consider heap tuning or reducing allocation rate.";
                    } else if (pressureScore > 30) {
                        interpretation = "MODERATE: Acceptable GC pressure for busy applications. Monitor trends.";
                    } else {
                        interpretation = "HEALTHY: Low GC pressure. GC is keeping up well with allocation.";
                    }
                    result.put("interpretation", interpretation);
                    
                    // Specific recommendations
                    List<String> recommendations = new ArrayList<>();
                    if (avgGcTime > 20) {
                        recommendations.add("GC time is high. Consider increasing heap size or optimizing allocation patterns.");
                    }
                    if (avgGcFreq > 60) {
                        recommendations.add("GC frequency is very high. Young generation may be too small.");
                    }
                    if (currentUtilization > 85) {
                        recommendations.add("Heap utilization is very high. Risk of OOM. Increase heap size or investigate memory leaks.");
                    }
                    if (heapGrowth > 10) {
                        recommendations.add("Heap usage is growing rapidly. Possible memory leak or insufficient GC.");
                    }
                    
                    if (!recommendations.isEmpty()) {
                        result.put("recommendations", recommendations);
                    }
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in getGcPressureAnalysis", e);
            result.put("error", "Error analyzing GC pressure: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Tool: getMemoryLeakIndicators
     * Identify potential memory leak indicators
     * Critical for: Memory leak detection, OOM troubleshooting
     */
    @Tool(description = """
            Identify potential memory leak indicators by analyzing post-GC heap growth and GC effectiveness.
            Uses linear regression to detect steadily increasing memory retention.
            
            Parameters:
            - lookback: Time window for leak detection (e.g., "30m", "1h", "2h", "24h"). Default: "30m"
            
            Use this tool when:
            - Suspecting memory leaks
            - Investigating gradual memory growth
            - Analyzing why heap doesn't reclaim after GC
            - Predicting OOM from memory leaks
            
            Returns: Post-GC heap trend, growth rate, full GC count, leak likelihood score (0-100), assessment (HIGH/MEDIUM/LOW/MINIMAL).
            """)
    public Map<String, Object> getMemoryLeakIndicators(@Nullable String lookback) {
        LOG.infof("Executing getMemoryLeakIndicators with lookback=%s", lookback);
        
        if (lookback == null || lookback.isEmpty()) {
            lookback = "30m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get heap used after GC (tenured space after collection)
            List<MetricValue> tenuredAfterGc = metricsService.getMetricRange(
                "jvm_memory_pool_collection_used_bytes{pool=\"tenured-SOA\"}", lookback, "1m");
            
            // Get full GC count
            List<MetricValue> fullGcCount = metricsService.getMetricRange(
                "jvm_gc_collection_seconds_count{gc=\"global\"}", lookback, "1m");
            
            if (!tenuredAfterGc.isEmpty() && tenuredAfterGc.size() > 5) {
                // Analyze trend in post-GC heap usage
                List<Double> values = tenuredAfterGc.stream()
                    .map(MetricValue::getValue)
                    .collect(Collectors.toList());
                
                // Calculate linear regression slope
                double slope = calculateTrendSlope(values);
                double avgValue = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double growthRate = (slope / avgValue) * 100; // Percentage growth per sample
                
                result.put("post_gc_heap_trend_bytes_per_sample", Math.round(slope));
                result.put("post_gc_heap_growth_rate_percent", Math.round(growthRate * 1000.0) / 1000.0);
                result.put("avg_post_gc_heap_bytes", Math.round(avgValue));
                
                // Check if full GCs are increasing
                if (!fullGcCount.isEmpty() && fullGcCount.size() > 1) {
                    MetricValue firstGc = fullGcCount.get(0);
                    MetricValue lastGc = fullGcCount.get(fullGcCount.size() - 1);
                    double gcIncrease = lastGc.getValue() - firstGc.getValue();
                    
                    result.put("full_gc_count_increase", Math.round(gcIncrease));
                }
                
                // Leak likelihood assessment
                List<String> indicators = new ArrayList<>();
                int leakScore = 0;
                
                if (slope > 1000000) { // 1MB per sample
                    indicators.add("Post-GC heap usage is steadily increasing");
                    leakScore += 40;
                }
                
                if (growthRate > 0.5) { // 0.5% growth per sample
                    indicators.add("Significant growth rate in retained memory");
                    leakScore += 30;
                }
                
                // Check if heap is not being reclaimed effectively
                Double currentHeap = metricsService.getCurrentMetricValue("jvm_memory_heap_used_bytes");
                Double maxHeap = metricsService.getCurrentMetricValue("jvm_memory_heap_max_bytes");
                
                if (currentHeap != null && maxHeap != null) {
                    double utilization = (currentHeap / maxHeap) * 100;
                    if (utilization > 85 && slope > 0) {
                        indicators.add("High heap utilization with growing trend");
                        leakScore += 30;
                    }
                }
                
                result.put("leak_likelihood_score", leakScore);
                result.put("leak_indicators", indicators);
                
                String assessment;
                if (leakScore >= 70) {
                    assessment = "HIGH: Strong indicators of memory leak. Immediate investigation recommended.";
                } else if (leakScore >= 40) {
                    assessment = "MEDIUM: Possible memory leak. Monitor closely and investigate if trend continues.";
                } else if (leakScore >= 20) {
                    assessment = "LOW: Some concerning patterns but may be normal for growing workload.";
                } else {
                    assessment = "MINIMAL: No significant memory leak indicators detected.";
                }
                result.put("assessment", assessment);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getMemoryLeakIndicators", e);
            result.put("error", "Error analyzing memory leak indicators: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Tool: getThreadContentionAnalysis
     * Analyze thread contention and blocking
     * Critical for: Performance issues, deadlocks, thread starvation
     */
    @Tool(description = """
            Analyze thread contention and blocking to identify synchronization issues and deadlocks.
            Examines thread states (BLOCKED, WAITING, RUNNABLE) to detect lock contention.
            
            Parameters:
            - lookback: Time window for analysis (e.g., "5m", "10m", "30m", "1h"). Default: "5m"
            
            Use this tool when:
            - Investigating performance degradation
            - Detecting deadlocks or lock contention
            - Analyzing thread pool efficiency
            - Identifying synchronization bottlenecks
            
            Returns: Thread state distribution, contention %, contention score, interpretation, identified issues, CRITICAL deadlock alerts.
            """)
    public Map<String, Object> getThreadContentionAnalysis(@Nullable String lookback) {
        LOG.infof("Executing getThreadContentionAnalysis with lookback=%s", lookback);
        
        if (lookback == null || lookback.isEmpty()) {
            lookback = "5m";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get thread states over time
            List<MetricValue> blockedThreads = metricsService.getMetricRange(
                "jvm_threads_state{state=\"BLOCKED\"}", lookback, "30s");
            
            List<MetricValue> waitingThreads = metricsService.getMetricRange(
                "jvm_threads_state{state=\"WAITING\"}", lookback, "30s");
            
            List<MetricValue> timedWaitingThreads = metricsService.getMetricRange(
                "jvm_threads_state{state=\"TIMED_WAITING\"}", lookback, "30s");
            
            List<MetricValue> runnableThreads = metricsService.getMetricRange(
                "jvm_threads_state{state=\"RUNNABLE\"}", lookback, "30s");
            
            Double totalThreads = metricsService.getCurrentMetricValue("jvm_threads_current");
            
            if (!blockedThreads.isEmpty() && !waitingThreads.isEmpty() && totalThreads != null) {
                double avgBlocked = blockedThreads.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                double maxBlocked = blockedThreads.stream()
                    .mapToDouble(MetricValue::getValue)
                    .max()
                    .orElse(0.0);
                
                double avgWaiting = waitingThreads.stream()
                    .mapToDouble(MetricValue::getValue)
                    .average()
                    .orElse(0.0);
                
                double avgTimedWaiting = timedWaitingThreads.isEmpty() ? 0.0 : 
                    timedWaitingThreads.stream()
                        .mapToDouble(MetricValue::getValue)
                        .average()
                        .orElse(0.0);
                
                double avgRunnable = runnableThreads.isEmpty() ? 0.0 :
                    runnableThreads.stream()
                        .mapToDouble(MetricValue::getValue)
                        .average()
                        .orElse(0.0);
                
                result.put("avg_blocked_threads", Math.round(avgBlocked * 10.0) / 10.0);
                result.put("max_blocked_threads", Math.round(maxBlocked));
                result.put("avg_waiting_threads", Math.round(avgWaiting * 10.0) / 10.0);
                result.put("avg_timed_waiting_threads", Math.round(avgTimedWaiting * 10.0) / 10.0);
                result.put("avg_runnable_threads", Math.round(avgRunnable * 10.0) / 10.0);
                
                // Calculate contention metrics
                double blockedPercent = (avgBlocked / totalThreads) * 100;
                double waitingPercent = (avgWaiting / totalThreads) * 100;
                double contentionPercent = blockedPercent + waitingPercent;
                
                result.put("blocked_thread_percent", Math.round(blockedPercent * 10.0) / 10.0);
                result.put("waiting_thread_percent", Math.round(waitingPercent * 10.0) / 10.0);
                result.put("total_contention_percent", Math.round(contentionPercent * 10.0) / 10.0);
                
                // Contention score
                int contentionScore = (int)((blockedPercent * 3) + waitingPercent);
                result.put("contention_score", contentionScore);
                
                String interpretation;
                List<String> issues = new ArrayList<>();
                
                if (avgBlocked > 5) {
                    interpretation = "CRITICAL: High number of blocked threads. Severe lock contention detected.";
                    issues.add("Multiple threads blocked on locks - investigate synchronized blocks and lock usage");
                } else if (avgBlocked > 2) {
                    interpretation = "WARNING: Moderate thread blocking. Some lock contention present.";
                    issues.add("Some thread blocking detected - review synchronization patterns");
                } else if (contentionPercent > 30) {
                    interpretation = "MODERATE: High percentage of threads waiting. May indicate I/O or coordination overhead.";
                    issues.add("Many threads in waiting state - review thread pool sizing and coordination mechanisms");
                } else {
                    interpretation = "HEALTHY: Low thread contention. Good synchronization patterns.";
                }
                
                result.put("interpretation", interpretation);
                if (!issues.isEmpty()) {
                    result.put("identified_issues", issues);
                }
                
                // Check for deadlocks
                Double deadlocked = metricsService.getCurrentMetricValue("jvm_threads_deadlocked");
                if (deadlocked != null && deadlocked > 0) {
                    result.put("CRITICAL_ALERT", "DEADLOCK DETECTED: " + deadlocked.intValue() + " threads in deadlock");
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in getThreadContentionAnalysis", e);
            result.put("error", "Error analyzing thread contention: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Tool: getHeapFragmentationAnalysis
     * Analyze heap fragmentation in OpenJ9
     * Critical for: OOM issues, GC performance, heap tuning
     */
    @Tool(description = """
            Analyze heap fragmentation by comparing committed vs used memory across all memory pools.
            Identifies wasted committed memory that could cause allocation failures.
            
            No parameters required.
            
            Use this tool when:
            - Investigating allocation failures despite available heap
            - Analyzing memory pool efficiency
            - Detecting heap fragmentation issues
            - Optimizing heap configuration
            
            Returns: Per-pool analysis (used, committed, utilization, fragmentation %), avg heap fragmentation, assessment (HIGH/MODERATE/LOW).
            """)
    public Map<String, Object> getHeapFragmentationAnalysis() {
        LOG.info("Executing getHeapFragmentationAnalysis");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get all memory pools
            var poolsUsed = metricsService.executeQuery("jvm_memory_pool_used_bytes");
            var poolsCommitted = metricsService.executeQuery("jvm_memory_pool_committed_bytes");
            
            if (!poolsUsed.isEmpty() && !poolsCommitted.isEmpty()) {
                List<Map<String, Object>> poolAnalysis = new ArrayList<>();
                double totalFragmentation = 0.0;
                int poolCount = 0;
                
                for (var usedSeries : poolsUsed) {
                    String poolName = usedSeries.getLabels().get("pool");
                    if (poolName == null) continue;
                    
                    // Find corresponding committed value
                    var committedSeries = poolsCommitted.stream()
                        .filter(s -> poolName.equals(s.getLabels().get("pool")))
                        .findFirst();
                    
                    if (committedSeries.isPresent() && !usedSeries.getValues().isEmpty() && 
                        !committedSeries.get().getValues().isEmpty()) {
                        
                        double used = usedSeries.getValues().get(0).getValue();
                        double committed = committedSeries.get().getValues().get(0).getValue();
                        
                        if (committed > 0) {
                            double utilization = (used / committed) * 100;
                            double fragmentation = 100 - utilization;
                            
                            Map<String, Object> poolInfo = new HashMap<>();
                            poolInfo.put("pool", poolName);
                            poolInfo.put("used_bytes", Math.round(used));
                            poolInfo.put("committed_bytes", Math.round(committed));
                            poolInfo.put("utilization_percent", Math.round(utilization * 10.0) / 10.0);
                            poolInfo.put("fragmentation_percent", Math.round(fragmentation * 10.0) / 10.0);
                            
                            poolAnalysis.add(poolInfo);
                            
                            if (poolName.contains("heap") || poolName.contains("tenured") || poolName.contains("nursery")) {
                                totalFragmentation += fragmentation;
                                poolCount++;
                            }
                        }
                    }
                }
                
                result.put("pool_analysis", poolAnalysis);
                
                if (poolCount > 0) {
                    double avgFragmentation = totalFragmentation / poolCount;
                    result.put("avg_heap_fragmentation_percent", Math.round(avgFragmentation * 10.0) / 10.0);
                    
                    String assessment;
                    if (avgFragmentation > 40) {
                        assessment = "HIGH: Significant heap fragmentation. May cause allocation failures even with available memory.";
                    } else if (avgFragmentation > 25) {
                        assessment = "MODERATE: Some heap fragmentation present. Monitor for allocation issues.";
                    } else {
                        assessment = "LOW: Minimal heap fragmentation. Heap is well-utilized.";
                    }
                    result.put("assessment", assessment);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in getHeapFragmentationAnalysis", e);
            result.put("error", "Error analyzing heap fragmentation: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Helper method to calculate linear regression slope
     */
    private double calculateTrendSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0.0;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }
}

