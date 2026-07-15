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
 * MCP Tools for Application Behavior Investigation
 */
@ApplicationScoped
public class ApplicationTools {
    
    private static final Logger LOG = Logger.getLogger(ApplicationTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = "Show class loading activity")
    public Map<String, Object> getClassLoadingStats() {
        LOG.info("Executing getClassLoadingStats tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get current class loading metrics
            Double loadedClasses = metricsService.getCurrentMetricValue("jvm_classes_loaded");
            
            if (loadedClasses == null) {
                result.put("error", "Unable to fetch class loading metrics from data source");
                return result;
            }
            
            result.put("loaded_classes", loadedClasses.longValue());
            
            // Get 5-minute trend
            List<MetricValue> recentValues = metricsService.getMetricRange(
                "jvm_classes_loaded", "5m", "30s");
            
            if (!recentValues.isEmpty()) {
                Map<String, Object> recent5m = new HashMap<>();
                
                Map<String, Double> stats = metricsService.calculateStatistics(recentValues);
                recent5m.put("min_loaded", stats.get("min").longValue());
                recent5m.put("max_loaded", stats.get("max").longValue());
                recent5m.put("avg_loaded", stats.get("avg").longValue());
                
                // Calculate growth rate (classes per hour)
                if (recentValues.size() >= 2) {
                    MetricValue first = recentValues.get(0);
                    MetricValue last = recentValues.get(recentValues.size() - 1);
                    long timeDiffMinutes = java.time.Duration.between(
                        first.getTimestamp(), last.getTimestamp()).toMinutes();
                    
                    if (timeDiffMinutes > 0) {
                        double classDiff = last.getValue() - first.getValue();
                        double growthRatePerHour = (classDiff / timeDiffMinutes) * 60;
                        recent5m.put("growth_rate_per_hour", Math.round(growthRatePerHour * 10.0) / 10.0);
                    }
                }
                
                result.put("recent_5m", recent5m);
            }
            
        } catch (Exception e) {
            LOG.error("Error in getClassLoadingStats", e);
            result.put("error", "Error fetching class loading stats: " + e.getMessage());
        }
        
        return result;
    }
    
    @Tool(description = "Provide JVM configuration context")
    public Map<String, Object> getJvmRuntimeInfo() {
        LOG.info("Executing getJvmRuntimeInfo tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Get heap configuration
            Double heapMax = metricsService.getCurrentMetricValue("jvm_memory_heap_max_bytes");
            Double heapInit = metricsService.getCurrentMetricValue("jvm_memory_heap_init_bytes");
            
            if (heapMax != null && heapInit != null) {
                Map<String, Object> heapConfig = new HashMap<>();
                heapConfig.put("initial_heap_bytes", heapInit.longValue());
                heapConfig.put("max_heap_bytes", heapMax.longValue());
                heapConfig.put("initial_heap_mb", heapInit.longValue() / (1024 * 1024));
                heapConfig.put("max_heap_mb", heapMax.longValue() / (1024 * 1024));
                
                result.put("heap_config", heapConfig);
            }
            
            // Get target information from 'up' metric
            var upMetrics = metricsService.executeQuery("up");
            if (!upMetrics.isEmpty()) {
                var upMetric = upMetrics.get(0);
                Map<String, String> labels = upMetric.getLabels();
                
                Map<String, String> targetInfo = new HashMap<>();
                targetInfo.put("job", labels.getOrDefault("job", "unknown"));
                targetInfo.put("instance", labels.getOrDefault("instance", "unknown"));
                
                // Add any additional labels
                labels.forEach((key, value) -> {
                    if (!key.equals("job") && !key.equals("instance") && !key.equals("__name__")) {
                        targetInfo.put(key, value);
                    }
                });
                
                result.put("target_info", targetInfo);
            }
            
            // Get data source metadata
            Map<String, String> dataSourceMeta = metricsService.getDataSourceMetadata();
            result.put("data_source", dataSourceMeta);
            
        } catch (Exception e) {
            LOG.error("Error in getJvmRuntimeInfo", e);
            result.put("error", "Error fetching JVM runtime info: " + e.getMessage());
        }
        
        return result;
    }
}

