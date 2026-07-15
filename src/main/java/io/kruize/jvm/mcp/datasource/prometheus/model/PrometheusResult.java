package io.kruize.jvm.mcp.datasource.prometheus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Prometheus result containing metric labels and values
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusResult {
    private Map<String, String> metric;
    private List<Object> value;  // [timestamp, value]
    private List<List<Object>> values;  // [[timestamp, value], ...]
}

