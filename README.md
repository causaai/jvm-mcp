# JVM Observability MCP Server

A Model Context Protocol (MCP) server that provides JVM observability data from Prometheus to AI agents for investigation and root cause analysis.

## Overview

This MCP server exposes JVM metrics through a set of tools that AI agents can use to investigate performance issues, memory problems, garbage collection behavior, and other JVM-related concerns. The server is built with a pluggable data source architecture, currently supporting Prometheus, with the ability to add other data sources in the future.

## Architecture

### Data Source Abstraction Layer

The project uses an abstraction layer to support multiple data sources:

```
┌─────────────────────────────────────────┐
│         MCP Tools Layer                 │
│  (Memory, GC, Thread, CPU, etc.)        │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│      JvmMetricsService                  │
│  (Business Logic & Calculations)        │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│      JvmDataSource Interface            │
│  (Abstract data source operations)      │
└─────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
┌──────────────────┐   ┌──────────────────┐
│ PrometheusData   │   │  Future: InfluxDB│
│    Source        │   │  OpenTelemetry   │
└──────────────────┘   └──────────────────┘
```

### Current Data Source: Prometheus

The server currently connects to Prometheus (default port 9091) to fetch JVM metrics exported via JMX Exporter.

### Future Extensibility

The architecture supports adding new data sources by:
1. Implementing the `JvmDataSource` interface
2. Configuring the data source type via environment variables
3. No changes required to MCP tools or business logic

## Prerequisites

- Java 25+
- Maven 3.9+
- Prometheus server running with JVM metrics (JMX Exporter)
- RHEL VM (or compatible Linux distribution)

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PROMETHEUS_URL` | `http://localhost:9091` | Prometheus server URL |
| `PROMETHEUS_JOB` | `liberty-jmx` | Prometheus job name for JVM metrics |
| `PROMETHEUS_TIMEOUT` | `30s` | Timeout for Prometheus queries |
| `DATASOURCE_TYPE` | `prometheus` | Type of data source (currently only prometheus) |
| `DATASOURCE_ENABLED` | `true` | Enable/disable data source |

### Application Properties

Edit `src/main/resources/application.properties` to configure:

```properties
# Prometheus Data Source Configuration
prometheus.url=${PROMETHEUS_URL:http://localhost:9091}
prometheus.job=${PROMETHEUS_JOB:liberty-jmx}
prometheus.timeout=${PROMETHEUS_TIMEOUT:30s}

# Data Source Configuration
datasource.type=${DATASOURCE_TYPE:prometheus}
datasource.enabled=${DATASOURCE_ENABLED:true}
```

## Building the Project

### Development Mode

```bash
./mvnw quarkus:dev
```

### Production Build

```bash
./mvnw clean package
```

### Native Build (GraalVM)

```bash
./mvnw clean package -Pnative
```

## Running the Server

### Using Maven

```bash
./mvnw quarkus:dev
```

### Using JAR

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Using Docker

```bash
# Build the image
docker build -f src/main/docker/Dockerfile.jvm -t jvm-jmx-mcp:latest .

# Run the container
docker run -p 8080:8080 \
  -e PROMETHEUS_URL=http://prometheus:9091 \
  -e PROMETHEUS_JOB=liberty-jmx \
  jvm-jmx-mcp:latest
```

## Available MCP Tools

### Memory Investigation Tools

1. **getHeapStatus** - Get current heap memory status including utilization and recent trends
2. **getMemoryPoolsBreakdown** - Show which specific memory pools are under pressure
3. **getMemoryOverTime** - Show memory behavior trends over a specified time window
   - Parameters: `lookback` (e.g., "1h", "6h"), `step` (e.g., "1m")

### Garbage Collection Tools

4. **getGcActivity** - Show current GC activity including collection counts and pause times
5. **getGcBehaviorOverTime** - Show GC frequency and pause time trends
   - Parameters: `lookback`, `step`
6. **getGcEfficiency** - Assess if GC is reclaiming memory effectively
   - Parameters: `window` (e.g., "5m")

### Thread Investigation Tools

7. **getThreadState** - Show current thread counts and health
8. **getThreadActivityOverTime** - Show thread count trends to identify leaks or growth
   - Parameters: `lookback`, `step`

### CPU & Resource Tools

9. **getCpuUsage** - Show JVM and system CPU consumption
10. **getSystemResources** - Show host-level memory availability
11. **getResourceUsageOverTime** - Show CPU and system memory trends
    - Parameters: `lookback`, `step`

### Application Behavior Tools

12. **getClassLoadingStats** - Show class loading activity
13. **getJvmRuntimeInfo** - Provide JVM configuration context

### Alert & Incident Context Tools

14. **getCurrentAlerts** - Show what monitoring systems think is wrong
15. **getRecentAlertHistory** - Show alert patterns over time
    - Parameters: `lookback`

### Comparative Analysis Tools

16. **getCurrentVsBaseline** - Compare current state to a baseline period
    - Parameters: `baseline_time`
17. **getMetricPercentiles** - Show what's normal vs exceptional for metrics
    - Parameters: `metric_category`, `lookback`

### Time-Window Investigation Tools

18. **getIncidentWindowData** - Get all metrics during a specific incident timeframe
    - Parameters: `start_time`, `end_time`, `step`
19. **getBeforeAfterSnapshot** - Compare metrics before and after an event
    - Parameters: `event_time`, `window`

### Correlation Data Tools

20. **getMemoryGcCorrelation** - Show synchronized heap usage and GC activity
    - Parameters: `lookback`, `step`
21. **getCpuGcCorrelation** - Show if GC is consuming CPU resources
    - Parameters: `lookback`, `step`

### Comprehensive Context Tools

22. **getJvmHealthContext** - Provide complete current state + recent trends in one call
23. **getInvestigationBundle** - Deep dive into one specific area with all related data
    - Parameters: `focus_area` (memory/gc/threads/cpu), `lookback`

## Metrics Supported

The server supports all JVM metrics exported by JMX Exporter:

- **Memory**: heap, non-heap, memory pools
- **Garbage Collection**: collection counts, pause times
- **Threads**: current, daemon, peak counts
- **CPU**: process and system CPU load
- **Classes**: loaded class counts
- **Operating System**: physical memory stats

## Adding New Data Sources

To add support for a new data source (e.g., InfluxDB, OpenTelemetry):

1. Create a new package: `io.kruize.jvm.mcp.datasource.<datasource-name>`
2. Implement the `JvmDataSource` interface
3. Add necessary dependencies to `pom.xml`
4. Configure the data source in `application.properties`
5. Update `DATASOURCE_TYPE` environment variable

Example:

```java
@ApplicationScoped
public class InfluxDBDataSource implements JvmDataSource {
    @Override
    public MetricValue getCurrentValue(String metricName, Map<String, String> labels) {
        // Implementation for InfluxDB
    }
    // ... other methods
}
```

## Project Structure

```
src/main/java/io/kruize/jvm/mcp/
├── datasource/              # Data source abstraction layer
│   ├── JvmDataSource.java   # Interface for data sources
│   └── prometheus/          # Prometheus implementation
│       ├── PrometheusClient.java
│       ├── PrometheusDataSource.java
│       └── model/           # Prometheus API models
├── model/                   # Domain models
│   ├── MetricValue.java
│   └── MetricTimeSeries.java
├── service/                 # Business logic layer
│   └── JvmMetricsService.java
└── tools/                   # MCP tool implementations
    ├── MemoryTools.java
    ├── GarbageCollectionTools.java
    ├── ThreadTools.java
    ├── CpuResourceTools.java
    ├── ApplicationTools.java
    ├── AlertTools.java
    ├── ComparativeTools.java
    ├── TimeWindowTools.java
    ├── CorrelationTools.java
    └── ContextTools.java
```

## Development

### Running Tests

```bash
./mvnw test
```

### Code Style

The project uses:
- Lombok for reducing boilerplate
- Jakarta EE annotations for dependency injection
- Quarkus MCP Server framework

## Troubleshooting

### Prometheus Connection Issues

1. Verify Prometheus is running: `curl http://localhost:9091/api/v1/query?query=up`
2. Check the job name matches your JMX exporter configuration
3. Verify network connectivity from the MCP server to Prometheus

### No Metrics Returned

1. Ensure JMX Exporter is attached to your JVM
2. Verify metrics are being scraped by Prometheus
3. Check the `prometheus.job` configuration matches your setup

### Performance Issues

1. Adjust query step intervals for large time ranges
2. Use appropriate lookback windows
3. Consider caching frequently accessed metrics


