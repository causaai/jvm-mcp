package io.kruize.jvm.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a time series of metric values with labels
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricTimeSeries {
    private String metricName;
    private Map<String, String> labels;
    private List<MetricValue> values;
}


