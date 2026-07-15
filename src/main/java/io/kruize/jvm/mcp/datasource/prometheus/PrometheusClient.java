package io.kruize.jvm.mcp.datasource.prometheus;

import io.kruize.jvm.mcp.datasource.prometheus.model.PrometheusResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Prometheus HTTP API
 */
@RegisterRestClient(configKey = "prometheus")
@Path("/api/v1")
public interface PrometheusClient {
    
    @GET
    @Path("/query")
    PrometheusResponse query(@QueryParam("query") String query,
                            @QueryParam("time") String time);
    
    @GET
    @Path("/query_range")
    PrometheusResponse queryRange(@QueryParam("query") String query,
                                 @QueryParam("start") String start,
                                 @QueryParam("end") String end,
                                 @QueryParam("step") String step);
    
    @GET
    @Path("/alerts")
    PrometheusResponse getAlerts();
}

