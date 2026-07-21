package io.kruize.jvm.mcp.datasource.prometheus;

import io.kruize.jvm.mcp.datasource.JvmDataSource;
import io.kruize.jvm.mcp.datasource.prometheus.model.PrometheusResponse;
import io.kruize.jvm.mcp.datasource.prometheus.model.PrometheusResult;
import io.kruize.jvm.mcp.model.MetricTimeSeries;
import io.kruize.jvm.mcp.model.MetricValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prometheus implementation of JvmDataSource
 */
@ApplicationScoped
public class PrometheusDataSource implements JvmDataSource {
    
    private static final Logger LOG = Logger.getLogger(PrometheusDataSource.class);
    
    @Inject
    @RestClient
    PrometheusClient prometheusClient;
    
    @ConfigProperty(name = "prometheus.job")
    String prometheusJob;
    
    @ConfigProperty(name = "prometheus.url")
    String prometheusUrl;
    
    @Override
    public MetricValue getCurrentValue(String metricName, Map<String, String> labels) {
        try {
            String query = buildQuery(metricName, labels);
            PrometheusResponse response = prometheusClient.query(query, null);
            
            if (response == null || response.getData() == null || 
                response.getData().getResult() == null || 
                response.getData().getResult().isEmpty()) {
                LOG.warnf("No data returned for metric: %s", metricName);
                return null;
            }
            
            PrometheusResult result = response.getData().getResult().get(0);
            return parseInstantValue(result, metricName);
            
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching current value for metric: %s", metricName);
            return null;
        }
    }
    
    @Override
    public MetricTimeSeries getRangeValues(String metricName, Map<String, String> labels,
                                          Instant start, Instant end, String step) {
        try {
            String query = buildQuery(metricName, labels);
            String startStr = String.valueOf(start.getEpochSecond());
            String endStr = String.valueOf(end.getEpochSecond());
            
            PrometheusResponse response = prometheusClient.queryRange(query, startStr, endStr, step);
            
            if (response == null || response.getData() == null || 
                response.getData().getResult() == null || 
                response.getData().getResult().isEmpty()) {
                LOG.warnf("No data returned for metric range: %s", metricName);
                return null;
            }
            
            PrometheusResult result = response.getData().getResult().get(0);
            return parseRangeValues(result, metricName);
            
        } catch (Exception e) {
            LOG.errorf(e, "Error fetching range values for metric: %s", metricName);
            return null;
        }
    }
    
    @Override
    public List<MetricTimeSeries> executeQuery(String query) {
        try {
            PrometheusResponse response = prometheusClient.query(query, null);
            
            if (response == null || response.getData() == null || 
                response.getData().getResult() == null) {
                LOG.warnf("No data returned for query: %s", query);
                return Collections.emptyList();
            }
            
            return response.getData().getResult().stream()
                .map(result -> parseRangeValues(result, extractMetricName(result)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            LOG.errorf(e, "Error executing query: %s", query);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<MetricTimeSeries> executeRangeQuery(String query, Instant start, Instant end, String step) {
        try {
            String startStr = String.valueOf(start.getEpochSecond());
            String endStr = String.valueOf(end.getEpochSecond());
            
            PrometheusResponse response = prometheusClient.queryRange(query, startStr, endStr, step);
            
            if (response == null || response.getData() == null ||
                response.getData().getResult() == null) {
                LOG.warnf("No data returned for range query: %s", query);
                return Collections.emptyList();
            }
            
            return response.getData().getResult().stream()
                .map(result -> parseRangeValues(result, extractMetricName(result)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            LOG.errorf(e, "Error executing range query: %s", query);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<MetricValue> executeQueryAtTime(String query, Instant timestamp) {
        try {
            String timeStr = String.valueOf(timestamp.getEpochSecond());
            PrometheusResponse response = prometheusClient.query(query, timeStr);
            
            if (response == null || response.getData() == null || 
                response.getData().getResult() == null) {
                LOG.warnf("No data returned for query at time: %s", query);
                return Collections.emptyList();
            }
            
            return response.getData().getResult().stream()
                .map(result -> parseInstantValue(result, extractMetricName(result)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            LOG.errorf(e, "Error executing query at time: %s", query);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getDataSourceType() {
        return "prometheus";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            PrometheusResponse response = prometheusClient.query("up", null);
            return response != null && "success".equals(response.getStatus());
        } catch (Exception e) {
            LOG.errorf(e, "Prometheus is not available");
            return false;
        }
    }
    
    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "prometheus");
        metadata.put("url", prometheusUrl);
        metadata.put("job", prometheusJob);
        metadata.put("available", String.valueOf(isAvailable()));
        return metadata;
    }
    
    /**
     * Build a PromQL query with labels
     */
    private String buildQuery(String metricName, Map<String, String> labels) {
        StringBuilder query = new StringBuilder(metricName);
        
        Map<String, String> allLabels = new HashMap<>();
        allLabels.put("job", prometheusJob);
        if (labels != null) {
            allLabels.putAll(labels);
        }
        
        if (!allLabels.isEmpty()) {
            query.append("{");
            query.append(allLabels.entrySet().stream()
                .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                .collect(Collectors.joining(",")));
            query.append("}");
        }
        
        return query.toString();
    }
    
    /**
     * Parse instant value from Prometheus result
     */
    private MetricValue parseInstantValue(PrometheusResult result, String metricName) {
        if (result.getValue() == null || result.getValue().size() < 2) {
            return null;
        }
        
        try {
            long timestamp = ((Number) result.getValue().get(0)).longValue();
            double value = Double.parseDouble((String) result.getValue().get(1));
            
            return MetricValue.builder()
                .timestamp(Instant.ofEpochSecond(timestamp))
                .value(value)
                .metricName(metricName)
                .build();
        } catch (Exception e) {
            LOG.errorf(e, "Error parsing instant value");
            return null;
        }
    }
    
    /**
     * Parse range values from Prometheus result
     */
    private MetricTimeSeries parseRangeValues(PrometheusResult result, String metricName) {
        if (result.getValues() == null || result.getValues().isEmpty()) {
            // Try to parse as instant value
            if (result.getValue() != null) {
                MetricValue value = parseInstantValue(result, metricName);
                if (value != null) {
                    return MetricTimeSeries.builder()
                        .metricName(metricName)
                        .labels(result.getMetric())
                        .values(Collections.singletonList(value))
                        .build();
                }
            }
            return null;
        }
        
        List<MetricValue> values = result.getValues().stream()
            .map(valueArray -> {
                try {
                    long timestamp = ((Number) valueArray.get(0)).longValue();
                    double value = Double.parseDouble((String) valueArray.get(1));
                    
                    return MetricValue.builder()
                        .timestamp(Instant.ofEpochSecond(timestamp))
                        .value(value)
                        .metricName(metricName)
                        .build();
                } catch (Exception e) {
                    LOG.errorf(e, "Error parsing value");
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return MetricTimeSeries.builder()
            .metricName(metricName)
            .labels(result.getMetric())
            .values(values)
            .build();
    }
    
    /**
     * Extract metric name from result
     */
    private String extractMetricName(PrometheusResult result) {
        if (result.getMetric() != null && result.getMetric().containsKey("__name__")) {
            return result.getMetric().get("__name__");
        }
        return "unknown";
    }
}

