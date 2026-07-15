package io.kruize.jvm.mcp.datasource.prometheus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Prometheus data section of the response
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusData {
    private String resultType;
    private List<PrometheusResult> result;
}

