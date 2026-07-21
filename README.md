# JVM Observability MCP Server

A Model Context Protocol (MCP) server that provides JVM observability data from Prometheus to AI agents for intelligent investigation and root cause analysis.

## Overview

This MCP server exposes specialized tools that enable AI agents to investigate JVM performance issues, memory problems, garbage collection behavior, and other runtime concerns. Built with a pluggable data source architecture, it currently supports Prometheus with the ability to add other data sources in the future.

**Key Features:**
- 🔍 AI-optimized investigation tools across multiple categories
- 🔌 Pluggable data source architecture (Prometheus, future: InfluxDB, OpenTelemetry)
- 🚀 **OpenJ9 JVM support** (IBM Semeru Runtime)
- 📊 Advanced RCA tools with statistical analysis
- 🎯 Time-window and correlation analysis
- 📈 Real-time and historical metrics

> **⚠️ Important:** This server is currently optimized for **OpenJ9 JVM (IBM Semeru Runtime)**. HotSpot JVM support is planned for future releases.

## Quick Start

### Prerequisites

- **Java 21+** (tested with Java 21)
- **Maven 3.9+**
- **Prometheus** server with JVM metrics (JMX Exporter)
- **RHEL VM** or compatible Linux distribution
- **OpenJ9 JVM** (IBM Semeru Runtime) - HotSpot not currently supported

### Build & Run

```bash
# Clone the repository
git clone https://github.com/causaai/jvm-mcp.git
cd jvm-mcp

# Build the project
./mvnw clean package -DskipTests

# Run the server
java -jar target/quarkus-app/quarkus-run.jar
```

The server will start on **port 8088** by default.

### Configuration

Set environment variables or edit `src/main/resources/application.properties`:

```bash
# Prometheus connection
export PROMETHEUS_URL=http://your-prometheus:9090
export PROMETHEUS_JOB=liberty-jmx

# Optional: Change server port
export QUARKUS_HTTP_PORT=8088
```

## Health Check Endpoints

The server provides health check endpoints for monitoring:

- **Liveness**: `http://localhost:8088/q/health/live` - Checks if the application is running
- **Readiness**: `http://localhost:8088/q/health/ready` - Checks if the application is ready to accept requests (includes Prometheus connectivity check)
- **Overall Health**: `http://localhost:8088/q/health` - Combined liveness and readiness checks

Example health check response:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Prometheus data source",
      "status": "UP",
      "data": {
        "type": "prometheus",
        "status": "connected"
      }
    }
  ]
}
```

## Available MCP Tools

### 📊 Memory Investigation (3 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getHeapStatus` | None | Current heap utilization, max/committed/used bytes, 5m growth rate |
| `getMemoryPoolsBreakdown` | None | Detailed breakdown of all memory pools (nursery, tenured, metaspace, JIT caches) |
| `getMemoryOverTime` | `lookback`, `step` | Heap usage time-series with min/max/avg statistics |

### 🗑️ Garbage Collection (3 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getGcActivity` | None | Current GC counts, pause times, avg pause per collector |
| `getGcBehaviorOverTime` | `lookback`, `step` | GC frequency (per min) and time overhead (%) trends |
| `getGcEfficiency` | `window` | GC overhead %, collections/min, heap reclamation rate |

### 🧵 Thread Investigation (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getThreadState` | None | Current/daemon/peak thread counts, deadlock detection |
| `getThreadActivityOverTime` | `lookback`, `step` | Thread count trends to identify leaks or growth patterns |

### ⚡ CPU & Resource (3 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getCpuUsage` | None | Process CPU cores/%, 5m avg/max/min (OpenJ9 compatible) |
| `getSystemResources` | None | Physical memory total/free/used, file descriptors |
| `getResourceUsageOverTime` | `lookback`, `step` | CPU cores, resident memory, file descriptor trends |

### 📦 Application Behavior (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getClassLoadingStats` | None | Loaded classes, 5m min/max/avg, growth rate (classes/hour) |
| `getJvmRuntimeInfo` | None | JVM vendor/version/type (OpenJ9), uptime, runtime name |

### 📈 Comparative Analysis (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getCurrentVsBaseline` | `baseline_time` | Compare current metrics to baseline period (heap, GC, threads, CPU) |
| `getMetricPercentiles` | `metric_category`, `lookback` | P50/P75/P90/P95/P99 percentiles to identify anomalies |

### ⏱️ Time-Window Investigation (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getIncidentWindowData` | `start_time`, `end_time`, `step` | All metrics during incident timeframe |
| `getBeforeAfterSnapshot` | `event_time`, `window` | Compare metrics before/after an event (e.g., deployment) |

### 🔗 Correlation Analysis (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getMemoryGcCorrelation` | `lookback`, `step` | Synchronized heap usage and GC activity with correlation coefficient |
| `getCpuGcCorrelation` | `lookback`, `step` | CPU usage vs GC activity correlation (OpenJ9 compatible) |

### 🎯 Comprehensive Context (2 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getJvmHealthContext` | None | Complete health snapshot: current state + 5m trends + health indicators |
| `getInvestigationBundle` | `focus_area`, `lookback` | Deep-dive bundle for memory/gc/threads/cpu with correlations |

### 🔬 Advanced RCA (5 tools)

| Tool | Parameters | Description |
|------|------------|-------------|
| `getMemoryAllocationRate` | `lookback` | Allocation rate (MB/sec, GB/hour) with pressure interpretation |
| `getGcPressureAnalysis` | `lookback` | GC pressure score (0-100) with expert recommendations |
| `getMemoryLeakIndicators` | `lookback` | Linear regression analysis to detect memory leaks |
| `getThreadContentionAnalysis` | `lookback` | Lock contention, deadlock detection, blocked thread analysis |
| `getHeapFragmentationAnalysis` | None | Per-pool fragmentation % with optimization recommendations |

**📖 For detailed tool documentation, parameters, and sample responses, see [USER_GUIDE.md](docs/USER_GUIDE.md)**

## Build Instructions

### Development Mode (Hot Reload)

```bash
./mvnw quarkus:dev
```

Access the server at `http://localhost:8088`

### Production Build

```bash
# Clean and build
./mvnw clean package -DskipTests

# Run the JAR
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Build (GraalVM)

```bash
# Requires GraalVM installed
./mvnw clean package -Pnative

# Run native executable
./target/jvm-mcp-1.0.0-SNAPSHOT-runner
```

### Docker Build

```bash
# Build JVM image
docker build -f src/main/docker/Dockerfile.jvm -t jvm-mcp:latest .

# Run container
docker run -p 8088:8088 \
  -e PROMETHEUS_URL=http://prometheus:9090 \
  -e PROMETHEUS_JOB=liberty-jmx \
  jvm-mcp:latest
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PROMETHEUS_URL` | `http://localhost:9090` | Prometheus server URL |
| `PROMETHEUS_JOB` | `liberty-jmx` | Prometheus job name for JVM metrics |
| `PROMETHEUS_TIMEOUT` | `30s` | Timeout for Prometheus queries |
| `QUARKUS_HTTP_PORT` | `8088` | HTTP server port |

### Application Properties

Edit `src/main/resources/application.properties`:

```properties
# Server Configuration
quarkus.http.port=8088

# Prometheus Data Source
prometheus.url=${PROMETHEUS_URL:http://localhost:9090}
prometheus.job=${PROMETHEUS_JOB:liberty-jmx}
prometheus.timeout=${PROMETHEUS_TIMEOUT:30s}
```

### Prometheus Setup

Ensure your JVM is exporting metrics via JMX Exporter:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'liberty-jmx'
    static_configs:
      - targets: ['localhost:9404']
```

## Architecture

### Data Source Abstraction

```
┌─────────────────────────────────────────┐
│         MCP Tools Layer                 │
│  Memory, GC, Thread, CPU, RCA, etc.     │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│      JvmMetricsService                  │
│  Business Logic & Statistical Analysis  │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│      JvmDataSource Interface            │
│  getCurrentValue(), getRangeValues()    │
│  executeQuery(), executeRangeQuery()    │
└─────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
┌──────────────────┐   ┌──────────────────┐
│ PrometheusData   │   │  Future:         │
│    Source        │   │  - InfluxDB      │
│  (Current)       │   │  - OpenTelemetry │
└──────────────────┘   └──────────────────┘
```

### Project Structure

```
src/main/java/io/kruize/jvm/mcp/
├── datasource/              # Data source abstraction
│   ├── JvmDataSource.java   # Interface
│   └── prometheus/          # Prometheus implementation
├── model/                   # Domain models
├── service/                 # Business logic
└── tools/                   # MCP tools
    ├── MemoryTools.java
    ├── GarbageCollectionTools.java
    ├── ThreadTools.java
    ├── CpuResourceTools.java
    ├── ApplicationTools.java
    ├── ComparativeTools.java
    ├── TimeWindowTools.java
    ├── CorrelationTools.java
    ├── ContextTools.java
    └── AdvancedRcaTools.java
```

## JVM Compatibility

### ✅ Supported: OpenJ9 (IBM Semeru Runtime)

This server is **fully optimized for OpenJ9 JVM**:

- Uses `process_cpu_seconds_total` for CPU metrics
- Supports OpenJ9 memory pools:
  - `nursery-allocate` - Young generation allocation space
  - `nursery-survivor` - Young generation survivor space
  - `tenured-SOA` - Old generation Small Object Area
  - `tenured-LOA` - Old generation Large Object Area
  - `JIT code cache` - JIT compiled code
  - `JIT data cache` - JIT metadata
- Automatically detects JVM type via runtime metrics

### ❌ Not Currently Supported: HotSpot JVM

HotSpot JVM support is **planned for future releases**. Current limitations:

- Memory pool names differ (Eden, Survivor, Old Gen vs nursery/tenured)
- CPU metrics use different naming conventions
- Some tools may return incomplete data

See [METRICS_COMPATIBILITY.md](docs/METRICS_COMPATIBILITY.md) for detailed compatibility information.

## Troubleshooting

### Connection Issues

```bash
# Test Prometheus connectivity
curl http://localhost:9090/api/v1/query?query=up

# Check if JVM metrics are available
curl http://localhost:9090/api/v1/query?query=jvm_memory_heap_used_bytes
```

### No Data Returned

1. **Verify Prometheus scrape interval**: Tools work best with scrape intervals ≤1 minute
2. **Check job name**: Ensure `prometheus.job` matches your Prometheus configuration
3. **Sparse data**: Use appropriate `lookback` and `step` parameters (e.g., `lookback="24h"`, `step="1h"` for hourly scrapes)
4. **Verify OpenJ9 JVM**: Ensure you're running IBM Semeru Runtime (OpenJ9), not HotSpot

### Build Issues

```bash
# Clean Maven cache
./mvnw clean

# Rebuild with dependencies
./mvnw clean install -U

# Check Java version
java -version  # Should be 21+
```

## Documentation

- **[USER_GUIDE.md](docs/USER_GUIDE.md)** - Complete tool documentation with examples
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System design and architecture
- **[METRICS_COMPATIBILITY.md](docs/METRICS_COMPATIBILITY.md)** - OpenJ9 compatibility details
- **[IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md)** - Implementation tracking

## Extending the Server

### Adding a New Data Source

To add support for a new data source (e.g., InfluxDB, OpenTelemetry):

#### 1. Create Data Source Package

```bash
mkdir -p src/main/java/io/kruize/jvm/mcp/datasource/<datasource-name>
```

#### 2. Implement JvmDataSource Interface

Create a class that implements the `JvmDataSource` interface:

```java
package io.kruize.jvm.mcp.datasource.influxdb;

import io.kruize.jvm.mcp.datasource.JvmDataSource;
import io.kruize.jvm.mcp.model.MetricValue;
import io.kruize.jvm.mcp.model.MetricTimeSeries;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class InfluxDBDataSource implements JvmDataSource {
    
    @Override
    public MetricValue getCurrentValue(String metricName, Map<String, String> labels) {
        // Implement: Query current metric value from InfluxDB
        // Return MetricValue with timestamp and value
    }
    
    @Override
    public List<MetricValue> getRangeValues(String metricName, Map<String, String> labels,
                                           String lookback, String step) {
        // Implement: Query time-series data from InfluxDB
        // Return list of MetricValue objects
    }
    
    @Override
    public List<MetricTimeSeries> executeQuery(String query) {
        // Implement: Execute native InfluxDB query (Flux)
        // Return list of MetricTimeSeries
    }
    
    @Override
    public List<MetricTimeSeries> executeRangeQuery(String query, String lookback, String step) {
        // Implement: Execute time-range query
        // Return list of MetricTimeSeries
    }
    
    @Override
    public List<MetricTimeSeries> executeQueryAtTime(String query, Instant timestamp) {
        // Implement: Execute query at specific timestamp
        // Return list of MetricTimeSeries
    }
    
    @Override
    public boolean isAvailable() {
        // Implement: Health check for InfluxDB connection
        return true;
    }
}
```

#### 3. Add Dependencies

Update `pom.xml` with required dependencies:

```xml
<dependency>
    <groupId>com.influxdb</groupId>
    <artifactId>influxdb-client-java</artifactId>
    <version>6.10.0</version>
</dependency>
```

#### 4. Configure Properties

Add configuration in `src/main/resources/application.properties`:

```properties
# InfluxDB Configuration
influxdb.url=${INFLUXDB_URL:http://localhost:8086}
influxdb.token=${INFLUXDB_TOKEN:your-token}
influxdb.org=${INFLUXDB_ORG:your-org}
influxdb.bucket=${INFLUXDB_BUCKET:jvm-metrics}
```

#### 5. Inject and Use

The Quarkus CDI container will automatically discover your implementation. Tools will use it via dependency injection:

```java
@Inject
JvmDataSource dataSource; // Will inject your new implementation
```

### Adding a New MCP Tool

To add a new investigation tool:

#### 1. Choose or Create Tool Class

Add your tool to an existing category class or create a new one:

```java
package io.kruize.jvm.mcp.tools;

import io.kruize.jvm.mcp.service.JvmMetricsService;
import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class CustomTools {
    
    @Inject
    JvmMetricsService metricsService;
    
    // Add your tools here
}
```

#### 2. Implement Tool Method

Use the `@Tool` annotation with comprehensive description:

```java
@Tool(description = """
    Analyzes JVM network I/O patterns and identifies potential bottlenecks.
    
    This tool examines network socket usage, connection pools, and I/O wait times
    to help diagnose network-related performance issues.
    
    **Use Cases:**
    - Investigate slow API response times
    - Detect connection pool exhaustion
    - Identify network I/O bottlenecks
    
    **Parameters:**
    - lookback: Time window to analyze (e.g., "5m", "1h", "24h")
    - threshold: Alert threshold for connection pool usage (0-100%)
    
    **Returns:**
    - Current socket connections (active/idle)
    - Connection pool utilization
    - Network I/O wait time statistics
    - Recommendations for optimization
    
    **Example:**
    Input: lookback="1h", threshold=80
    Output: {
      "active_connections": 45,
      "pool_utilization_percent": 75.0,
      "avg_io_wait_ms": 12.5,
      "recommendations": ["Consider increasing connection pool size"]
    }
    """)
public Map<String, Object> analyzeNetworkIO(
    String lookback,
    Double threshold
) {
    // 1. Validate parameters
    if (lookback == null || lookback.isEmpty()) {
        lookback = "5m";
    }
    if (threshold == null) {
        threshold = 80.0;
    }
    
    // 2. Query metrics using JvmMetricsService
    Map<String, Object> result = new HashMap<>();
    
    // 3. Perform analysis
    // ... your implementation
    
    // 4. Return structured results
    return result;
}
```

#### 3. Key Guidelines for New Tools

**Tool Description Best Practices:**
- Start with a clear one-line summary
- Explain what the tool analyzes and why it's useful
- List specific use cases
- Document all parameters with types and examples
- Describe the return structure with example output
- Make descriptions verbose for AI agent understanding

**Implementation Guidelines:**
- Use `JvmMetricsService` for data access (don't access `JvmDataSource` directly)
- Provide sensible defaults for optional parameters
- Return structured `Map<String, Object>` for complex results
- Include error handling and validation
- Add statistical analysis where appropriate
- Consider time-series data for trend analysis

**Testing Your Tool:**
```bash
# Rebuild and run
./mvnw clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# Tool will be automatically discovered and registered
# Test via MCP client or API
```

#### 4. Tool Categories

Organize tools into logical categories:

- **Diagnostic Tools**: Current state analysis
- **Trend Tools**: Time-series analysis
- **Comparative Tools**: Baseline comparisons
- **Correlation Tools**: Multi-metric relationships
- **RCA Tools**: Root cause analysis with recommendations
- **Predictive Tools**: Forecasting and anomaly detection

## Contributing

Contributions are welcome! Please see the sections above on:
- [Adding a New Data Source](#adding-a-new-data-source)
- [Adding a New MCP Tool](#adding-a-new-mcp-tool)

### Adding HotSpot Support

Contributions to add HotSpot JVM support are welcome! Key areas:

1. Memory pool name mapping (Eden/Survivor/Old → nursery/tenured)
2. CPU metrics compatibility
3. GC collector name mapping

