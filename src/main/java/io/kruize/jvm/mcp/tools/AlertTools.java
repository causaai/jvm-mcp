package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.model.MetricTimeSeries;
import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;

/**
 * MCP Tools for Alert & Incident Context Investigation
 */
@ApplicationScoped
public class AlertTools {
    
    private static final Logger LOG = Logger.getLogger(AlertTools.class);
    
    @Inject
    JvmMetricsService metricsService;
    
    @Tool(description = "Show what monitoring systems think is wrong - active alerts from Prometheus")
    public Map<String, Object> getCurrentAlerts() {
        LOG.info("Executing getCurrentAlerts tool");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        
        try {
            // Query ALERTS metric from Prometheus
            List<MetricTimeSeries> alertsData = metricsService.executeQuery("ALERTS");
            
            List<Map<String, Object>> activeAlerts = new ArrayList<>();
            int firingCount = 0;
            int pendingCount = 0;
            
            for (MetricTimeSeries series : alertsData) {
                if (series.getValues().isEmpty()) {
                    continue;
                }
                
                Map<String, String> labels = series.getLabels();
                double alertValue = series.getValues().get(0).getValue();
                
                // Alert is firing if value is 1
                if (alertValue == 1.0) {
                    Map<String, Object> alert = new HashMap<>();
                    
                    String alertName = labels.getOrDefault("alertname", "unknown");
                    String alertState = labels.getOrDefault("alertstate", "firing");
                    String severity = labels.getOrDefault("severity", "unknown");
                    
                    alert.put("name", alertName);
                    alert.put("state", alertState);
                    alert.put("severity", severity);
                    
                    // Add all labels
                    Map<String, String> alertLabels = new HashMap<>();
                    labels.forEach((key, value) -> {
                        if (!key.equals("__name__") && !key.equals("alertname") && 
                            !key.equals("alertstate") && !key.equals("severity")) {
                            alertLabels.put(key, value);
                        }
                    });
                    alert.put("labels", alertLabels);
                    
                    // Try to get annotations if available
                    String description = labels.getOrDefault("description", "");
                    String summary = labels.getOrDefault("summary", "");
                    if (!description.isEmpty() || !summary.isEmpty()) {
                        Map<String, String> annotations = new HashMap<>();
                        if (!description.isEmpty()) annotations.put("description", description);
                        if (!summary.isEmpty()) annotations.put("summary", summary);
                        alert.put("annotations", annotations);
                    }
                    
                    alert.put("current_value", alertValue);
                    
                    activeAlerts.add(alert);
                    
                    if ("firing".equals(alertState)) {
                        firingCount++;
                    } else if ("pending".equals(alertState)) {
                        pendingCount++;
                    }
                }
            }
            
            result.put("active_alerts", activeAlerts);
            
            Map<String, Integer> alertCount = new HashMap<>();
            alertCount.put("firing", firingCount);
            alertCount.put("pending", pendingCount);
            result.put("alert_count", alertCount);
            
            if (activeAlerts.isEmpty()) {
                result.put("message", "No active alerts found");
            }
            
        } catch (Exception e) {
            LOG.error("Error in getCurrentAlerts", e);
            result.put("error", "Error fetching current alerts: " + e.getMessage());
            result.put("note", "ALERTS metric may not be available in Prometheus");
        }
        
        return result;
    }
    
    @Tool(description = "Show alert patterns over time")
    public Map<String, Object> getRecentAlertHistory(String lookback) {
        LOG.infof("Executing getRecentAlertHistory tool with lookback=%s", lookback);
        
        // Default value
        if (lookback == null || lookback.isEmpty()) {
            lookback = "24h";
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lookback", lookback);
        
        try {
            Instant end = Instant.now();
            result.put("end_time", end.toString());
            
            // Query ALERTS metric over time
            String query = "ALERTS";
            List<MetricTimeSeries> alertsData = metricsService.executeQuery(query);
            
            Map<String, Map<String, Object>> alertTimeline = new HashMap<>();
            
            for (MetricTimeSeries series : alertsData) {
                Map<String, String> labels = series.getLabels();
                String alertName = labels.getOrDefault("alertname", "unknown");
                
                if (!alertTimeline.containsKey(alertName)) {
                    Map<String, Object> alertInfo = new HashMap<>();
                    alertInfo.put("alert_name", alertName);
                    alertInfo.put("occurrences", 0);
                    alertInfo.put("total_firing_duration_seconds", 0);
                    alertInfo.put("events", new ArrayList<Map<String, Object>>());
                    alertTimeline.put(alertName, alertInfo);
                }
                
                Map<String, Object> alertInfo = alertTimeline.get(alertName);
                
                // Count firing instances
                long firingCount = series.getValues().stream()
                    .filter(v -> v.getValue() == 1.0)
                    .count();
                
                if (firingCount > 0) {
                    alertInfo.put("occurrences", (int) alertInfo.get("occurrences") + 1);
                    
                    // Create event entry
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> events = (List<Map<String, Object>>) alertInfo.get("events");
                    
                    if (!series.getValues().isEmpty()) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("state", "firing");
                        event.put("started_at", series.getValues().get(0).getTimestamp().toString());
                        
                        if (series.getValues().size() > 1) {
                            event.put("ended_at", 
                                series.getValues().get(series.getValues().size() - 1).getTimestamp().toString());
                        } else {
                            event.put("ended_at", null);
                        }
                        
                        events.add(event);
                    }
                }
            }
            
            result.put("alert_timeline", new ArrayList<>(alertTimeline.values()));
            
            if (alertTimeline.isEmpty()) {
                result.put("message", "No alert history found in the specified time range");
            }
            
            // Calculate start time
            if (!alertsData.isEmpty() && !alertsData.get(0).getValues().isEmpty()) {
                result.put("start_time", alertsData.get(0).getValues().get(0).getTimestamp().toString());
            }
            
        } catch (Exception e) {
            LOG.error("Error in getRecentAlertHistory", e);
            result.put("error", "Error fetching recent alert history: " + e.getMessage());
            result.put("note", "ALERTS metric may not be available in Prometheus");
        }
        
        return result;
    }
}

