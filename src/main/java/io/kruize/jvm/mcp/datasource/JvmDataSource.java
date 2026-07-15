package io.kruize.jvm.mcp.datasource;

import io.kruize.jvm.mcp.model.MetricTimeSeries;
import io.kruize.jvm.mcp.model.MetricValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Abstract interface for JVM data sources.
 * This allows the MCP server to connect to different data sources (Prometheus, InfluxDB, etc.)
 * while maintaining a consistent interface for retrieving JVM metrics.
 */
public interface JvmDataSource {
    
    /**
     * Get the current value of a metric
     * @param metricName The name of the metric
     * @param labels Optional labels to filter the metric
     * @return The current metric value
     */
    MetricValue getCurrentValue(String metricName, Map<String, String> labels);
    
    /**
     * Get a time series of metric values over a range
     * @param metricName The name of the metric
     * @param labels Optional labels to filter the metric
     * @param start Start time
     * @param end End time
     * @param step Sample interval (e.g., "1m")
     * @return Time series data
     */
    MetricTimeSeries getRangeValues(String metricName, Map<String, String> labels, 
                                    Instant start, Instant end, String step);
    
    /**
     * Execute a custom query (for complex aggregations)
     * @param query The query string (format depends on data source)
     * @return List of time series results
     */
    List<MetricTimeSeries> executeQuery(String query);
    
    /**
     * Execute a query at a specific timestamp
     * @param query The query string
     * @param timestamp The timestamp to query at
     * @return List of metric values
     */
    List<MetricValue> executeQueryAtTime(String query, Instant timestamp);
    
    /**
     * Get the data source type
     * @return The type of data source (e.g., "prometheus", "influxdb")
     */
    String getDataSourceType();
    
    /**
     * Check if the data source is available
     * @return true if the data source is reachable and healthy
     */
    boolean isAvailable();
    
    /**
     * Get metadata about the data source
     * @return Map of metadata key-value pairs
     */
    Map<String, String> getMetadata();
}

