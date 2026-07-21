package io.kruize.jvm.mcp.health;

import io.kruize.jvm.mcp.datasource.JvmDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

/**
 * Health check for Prometheus data source connectivity
 */
@Readiness
@ApplicationScoped
public class PrometheusHealthCheck implements HealthCheck {
    
    private static final Logger LOG = Logger.getLogger(PrometheusHealthCheck.class);
    
    @Inject
    JvmDataSource dataSource;
    
    @Override
    public HealthCheckResponse call() {
        try {
            boolean available = dataSource.isAvailable();
            
            if (available) {
                return HealthCheckResponse.named("Prometheus data source")
                    .up()
                    .build();
            } else {
                return HealthCheckResponse.named("Prometheus data source")
                    .down()
                    .build();
            }
        } catch (Exception e) {
            LOG.error("Error checking Prometheus health", e);
            return HealthCheckResponse.named("Prometheus data source")
                .down()
                .build();
        }
    }
}

