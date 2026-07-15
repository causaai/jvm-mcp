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
 * MCP Tools for JVM Thread Investigation
 */
@ApplicationScoped
public class ThreadTools {
    
    private static final Logger LOG = Logger.getLogger(ThreadTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = """
            Show current thread counts including total, daemon, non-daemon, and peak threads.
            Provides thread health metrics and peak utilization percentage.
            
            No parameters required.
            
            Use this tool when:
            - Investigating thread-related performance issues
            - Checking for thread leaks (growing thread count)
            - Monitoring thread pool health
            - Initial thread health assessment
            
            Returns: Current threads, daemon threads, non-daemon threads, peak threads, and peak utilization %.
            """)
    public Map<String, Object> getThreadState() {
        LOG.info("Executing getThreadState tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get current thread metrics
            Double currentThreads = metricsService.getCurrentMetricValue("jvm_threads_current");
            Double daemonThreads = metricsService.getCurrentMetricValue("jvm_threads_daemon");
            Double peakThreads = metricsService.getCurrentMetricValue("jvm_threads_peak");
            
            if (currentThreads == null || daemonThreads == null || peakThreads == null) {
                result.put("error", "Unable to fetch thread metrics from data source");
                return result;
            }
            
            long current = currentThreads.longValue();
            long daemon = daemonThreads.longValue();
            long peak = peakThreads.longValue();
            long nonDaemon = current - daemon;
            
            result.put("current_threads", current);
            result.put("daemon_threads", daemon);
            result.put("non_daemon_threads", nonDaemon);
            result.put("peak_threads", peak);
            
            if (peak > 0) {
                double peakUtilization = (current * 100.0) / peak;
                result.put("peak_utilization_percent", Math.round(peakUtilization * 10.0) / 10.0);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getThreadState", e);
            result.put("error", "Error fetching thread state: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = """
            Show thread count trends over time to identify thread leaks, growth patterns, or thread pool issues.
            Provides time-series data with total, daemon, and non-daemon thread counts plus growth rate.
            
            Parameters:
            - lookback: Time window to analyze (e.g., "5m", "1h", "2h", "24h"). Default: "1h"
            - step: Sampling interval (e.g., "30s", "1m", "5m"). Default: "1m"
            
            Use this tool when:
            - Detecting thread leaks (steadily increasing thread count)
            - Analyzing thread pool behavior over time
            - Correlating thread changes with application events
            - Investigating thread exhaustion issues
            
            Returns: Time-series samples with total/daemon/non-daemon counts, min/max/avg statistics, and growth rate per hour.
            """)
    public Map<String, Object> getThreadActivityOverTime(
            String lookback,
            String step) {
        
        LOG.infof("Executing getThreadActivityOverTime tool with lookback=%s, step=%s", lookback, step);
        
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
            
            // Get thread count over time
            List<MetricValue> currentThreadValues = metricsService.getMetricRange(
                "jvm_threads_current", lookback, step);
            List<MetricValue> daemonThreadValues = metricsService.getMetricRange(
                "jvm_threads_daemon", lookback, step);
            
            if (!currentThreadValues.isEmpty() && !daemonThreadValues.isEmpty()) {
                Map<String, Object> threadCount = new HashMap<>();
                
                // Build samples with total, daemon, and non-daemon counts
                List<Map<String, Object>> samples = new java.util.ArrayList<>();
                for (int i = 0; i < Math.min(currentThreadValues.size(), daemonThreadValues.size()); i++) {
                    MetricValue current = currentThreadValues.get(i);
                    MetricValue daemon = daemonThreadValues.get(i);
                    
                    Map<String, Object> sample = new HashMap<>();
                    sample.put("timestamp", current.getTimestamp().toString());
                    sample.put("total", current.getValue().longValue());
                    sample.put("daemon", daemon.getValue().longValue());
                    sample.put("non_daemon", current.getValue().longValue() - daemon.getValue().longValue());
                    samples.add(sample);
                }
                
                threadCount.put("samples", samples);
                
                // Calculate statistics
                Map<String, Double> stats = metricsService.calculateStatistics(currentThreadValues);
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("min_total", stats.get("min").longValue());
                statistics.put("max_total", stats.get("max").longValue());
                statistics.put("avg_total", stats.get("avg").longValue());
                
                // Calculate growth rate (threads per hour)
                if (currentThreadValues.size() >= 2) {
                    MetricValue first = currentThreadValues.get(0);
                    MetricValue last = currentThreadValues.get(currentThreadValues.size() - 1);
                    long timeDiffMinutes = java.time.Duration.between(
                        first.getTimestamp(), last.getTimestamp()).toMinutes();
                    
                    if (timeDiffMinutes > 0) {
                        double threadDiff = last.getValue() - first.getValue();
                        double growthRatePerHour = (threadDiff / timeDiffMinutes) * 60;
                        statistics.put("growth_rate_per_hour", Math.round(growthRatePerHour * 10.0) / 10.0);
                    }
                }
                
                threadCount.put("statistics", statistics);
                result.put("thread_count", threadCount);
                result.put("start_time", currentThreadValues.get(0).getTimestamp().toString());
            }
            
        } catch (Exception e) {
            LOG.error("Error in getThreadActivityOverTime", e);
            result.put("error", "Error fetching thread activity over time: " + e.getMessage());
        }
        
        return result;
    }
}

