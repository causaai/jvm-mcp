package io.kruize.jvm.mcp.service;

import io.kruize.jvm.mcp.datasource.JvmDataSource;
import io.kruize.jvm.mcp.model.MetricTimeSeries;
import io.kruize.jvm.mcp.model.MetricValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving and processing JVM metrics
 */
@ApplicationScoped
public class JvmMetricsService {
    
    private static final Logger LOG = Logger.getLogger(JvmMetricsService.class);
    
    @Inject
    JvmDataSource dataSource;
    
    /**
     * Get current value of a metric
     */
    public Double getCurrentMetricValue(String metricName) {
        MetricValue value = dataSource.getCurrentValue(metricName, null);
        return value != null ? value.getValue() : null;
    }
    
    /**
     * Get current value of a metric with specific labels
     */
    public Double getCurrentMetricValue(String metricName, Map<String, String> labels) {
        MetricValue value = dataSource.getCurrentValue(metricName, labels);
        return value != null ? value.getValue() : null;
    }
    
    /**
     * Get metric values over a time range (from now - lookback to now)
     */
    public List<MetricValue> getMetricRange(String metricName, String lookback, String step) {
        Instant end = Instant.now();
        Instant start = end.minus(parseDuration(lookback));
        
        MetricTimeSeries series = dataSource.getRangeValues(metricName, null, start, end, step);
        return series != null ? series.getValues() : Collections.emptyList();
    }
    
    /**
     * Get metric values over a specific time range with explicit start and end times
     */
    public List<MetricValue> getMetricRangeWithTimes(String metricName, Instant start, Instant end, String step) {
        MetricTimeSeries series = dataSource.getRangeValues(metricName, null, start, end, step);
        return series != null ? series.getValues() : Collections.emptyList();
    }
    
    /**
     * Execute a PromQL query (instant query)
     */
    public List<MetricTimeSeries> executeQuery(String query) {
        return dataSource.executeQuery(query);
    }
    
    /**
     * Execute a PromQL range query over a time window
     */
    public List<MetricTimeSeries> executeRangeQuery(String query, String lookback, String step) {
        Instant end = Instant.now();
        Instant start = end.minus(parseDuration(lookback));
        return dataSource.executeRangeQuery(query, start, end, step);
    }
    
    /**
     * Execute a query at a specific time
     */
    public List<MetricValue> executeQueryAtTime(String query, Instant timestamp) {
        return dataSource.executeQueryAtTime(query, timestamp);
    }
    
    /**
     * Calculate statistics for a list of metric values
     */
    public Map<String, Double> calculateStatistics(List<MetricValue> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<Double> numericValues = values.stream()
            .map(MetricValue::getValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (numericValues.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, Double> stats = new HashMap<>();
        stats.put("min", numericValues.stream().min(Double::compare).orElse(0.0));
        stats.put("max", numericValues.stream().max(Double::compare).orElse(0.0));
        stats.put("avg", numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        stats.put("current", numericValues.get(numericValues.size() - 1));
        
        return stats;
    }
    
    /**
     * Calculate growth rate (bytes per minute)
     */
    public Double calculateGrowthRate(List<MetricValue> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        
        MetricValue first = values.get(0);
        MetricValue last = values.get(values.size() - 1);
        
        long timeDiffSeconds = Duration.between(first.getTimestamp(), last.getTimestamp()).getSeconds();
        if (timeDiffSeconds == 0) {
            return 0.0;
        }
        
        double valueDiff = last.getValue() - first.getValue();
        return (valueDiff / timeDiffSeconds) * 60; // per minute
    }
    
    /**
     * Parse duration string (e.g., "5m", "1h", "24h")
     */
    private Duration parseDuration(String duration) {
        try {
            String unit = duration.substring(duration.length() - 1);
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
            
            return switch (unit) {
                case "s" -> Duration.ofSeconds(value);
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                case "d" -> Duration.ofDays(value);
                default -> Duration.ofMinutes(value);
            };
        } catch (Exception e) {
            LOG.warnf("Invalid duration format: %s, defaulting to 5 minutes", duration);
            return Duration.ofMinutes(5);
        }
    }
    
    /**
     * Check if data source is available
     */
    public boolean isDataSourceAvailable() {
        return dataSource.isAvailable();
    }
    
    /**
     * Get data source metadata
     */
    public Map<String, String> getDataSourceMetadata() {
        return dataSource.getMetadata();
    }
}

