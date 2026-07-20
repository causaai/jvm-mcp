# JVM JMX MCP Server - Architecture Documentation

## Overview

This document describes the architecture of the JVM Observability MCP Server, which provides JVM metrics to AI agents through the Model Context Protocol (MCP).

## Design Principles

### 1. Data Source Abstraction

The core architectural principle is **data source abstraction**. The system is designed to work with any data source that provides JVM metrics, not just Prometheus.

**Key Benefits:**
- **Flexibility**: Switch between data sources without changing business logic
- **Extensibility**: Add new data sources by implementing a single interface
- **Testability**: Mock data sources for testing
- **Future-proof**: Support multiple concurrent data sources

### 2. Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MCP Tools Layer                          │
│  - Memory Tools                                             │
│  - GC Tools                                                 │
│  - Thread Tools                                             │
│  - CPU/Resource Tools                                       │
│  - Alert Tools                                              │
│  - Comparative Analysis Tools                               │
│  - Time Window Tools                                        │
│  - Correlation Tools                                        │
│  - Context Tools                                            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Service Layer                              │
│  - JvmMetricsService                                        │
│    * Metric retrieval                                       │
│    * Statistical calculations                               │
│    * Data aggregation                                       │
│    * Business logic                                         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Data Source Abstraction Layer                  │
│  - JvmDataSource Interface                                  │
│    * getCurrentValue()                                      │
│    * getRangeValues()                                       │
│    * executeQuery()                                         │
│    * executeQueryAtTime()                                   │
│    * isAvailable()                                          │
│    * getMetadata()                                          │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
┌──────────────────────┐            ┌──────────────────────┐
│ Prometheus           │            │ Future Data Sources  │
│ Implementation       │            │ - InfluxDB           │
│                      │            │ - OpenTelemetry      │
│ - PrometheusClient   │            │ - Custom Metrics DB  │
│ - PrometheusDataSrc  │            │ - Multiple Sources   │
│ - REST API calls     │            │   Aggregation        │
└──────────────────────┘            └──────────────────────┘
```

## Component Details

### 1. Data Models

#### MetricValue
Represents a single metric value at a specific timestamp.

```java
public class MetricValue {
    private Instant timestamp;
    private Double value;
    private String metricName;
}
```

#### MetricTimeSeries
Represents a time series of metric values with labels.

```java
public class MetricTimeSeries {
    private String metricName;
    private Map<String, String> labels;
    private List<MetricValue> values;
}
```

### 2. Data Source Abstraction

#### JvmDataSource Interface

The core abstraction that all data sources must implement:

```java
public interface JvmDataSource {
    MetricValue getCurrentValue(String metricName, Map<String, String> labels);
    MetricTimeSeries getRangeValues(String metricName, Map<String, String> labels, 
                                    Instant start, Instant end, String step);
    List<MetricTimeSeries> executeQuery(String query);
    List<MetricValue> executeQueryAtTime(String query, Instant timestamp);
    String getDataSourceType();
    boolean isAvailable();
    Map<String, String> getMetadata();
}
```

**Design Rationale:**
- **getCurrentValue**: For instant metric queries
- **getRangeValues**: For time-series data over a range
- **executeQuery**: For complex queries (PromQL, InfluxQL, etc.)
- **executeQueryAtTime**: For historical point-in-time queries
- **isAvailable**: Health check for the data source
- **getMetadata**: Information about the data source configuration

### 3. Prometheus Implementation

#### PrometheusDataSource

Implements `JvmDataSource` for Prometheus:

**Key Features:**
- REST client integration using Quarkus REST Client
- Automatic label injection (job name)
- Error handling and logging
- Query building utilities
- Response parsing

**Query Patterns:**
```promql
# Instant query
jvm_memory_heap_used_bytes{job="liberty-jmx"}

# Range query
jvm_memory_heap_used_bytes{job="liberty-jmx"}[5m:30s]

# Rate calculation
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m]) * 60
```

### 4. Service Layer

#### JvmMetricsService

Provides business logic and data processing:

**Responsibilities:**
- Metric retrieval coordination
- Statistical calculations (min, max, avg)
- Growth rate calculations
- Duration parsing
- Data aggregation

**Key Methods:**
```java
Double getCurrentMetricValue(String metricName)
List<MetricValue> getMetricRange(String metricName, String lookback, String step)
Map<String, Double> calculateStatistics(List<MetricValue> values)
Double calculateGrowthRate(List<MetricValue> values)
```

### 5. MCP Tools Layer

Each tool class focuses on a specific investigation area:

#### MemoryTools
- `getHeapStatus()`: Current heap status with trends
- `getMemoryPoolsBreakdown()`: Detailed pool analysis
- `getMemoryOverTime()`: Historical memory trends

#### GarbageCollectionTools
- `getGcActivity()`: Current GC metrics
- `getGcBehaviorOverTime()`: GC trends
- `getGcEfficiency()`: GC effectiveness analysis

#### Additional Tool Classes (to be implemented)
- ThreadTools
- CpuResourceTools
- ApplicationTools
- AlertTools
- ComparativeTools
- TimeWindowTools
- CorrelationTools
- ContextTools

## Data Flow

### Example: getHeapStatus Tool

```
1. AI Agent calls getHeapStatus()
   │
   ▼
2. MemoryTools.getHeapStatus()
   │
   ▼
3. JvmMetricsService.getCurrentMetricValue("jvm_memory_heap_used_bytes")
   │
   ▼
4. PrometheusDataSource.getCurrentValue("jvm_memory_heap_used_bytes", labels)
   │
   ▼
5. PrometheusClient.query("jvm_memory_heap_used_bytes{job='liberty-jmx'}")
   │
   ▼
6. Prometheus HTTP API
   │
   ▼
7. Response parsing and transformation
   │
   ▼
8. Statistical calculations in JvmMetricsService
   │
   ▼
9. JSON response to AI Agent
```

## Configuration Management

### Environment-Based Configuration

```properties
# Data Source Selection
datasource.type=prometheus  # Future: influxdb, opentelemetry, etc.

# Prometheus Configuration
prometheus.url=http://localhost:9091
prometheus.job=liberty-jmx
prometheus.timeout=30s
```

### Multi-Source Support (Future)

```properties
# Enable multiple data sources
datasource.multi.enabled=true
datasource.primary=prometheus
datasource.fallback=influxdb

# Prometheus config
datasource.prometheus.url=http://prometheus:9091
datasource.prometheus.job=liberty-jmx

# InfluxDB config
datasource.influxdb.url=http://influxdb:8086
datasource.influxdb.database=jvm_metrics
```

## Extensibility Patterns

### Adding a New Data Source

1. **Create Implementation Package**
   ```
   src/main/java/io/kruize/jvm/mcp/datasource/influxdb/
   ```

2. **Implement JvmDataSource Interface**
   ```java
   @ApplicationScoped
   public class InfluxDBDataSource implements JvmDataSource {
       // Implementation
   }
   ```

3. **Add Configuration**
   ```properties
   datasource.type=influxdb
   influxdb.url=http://localhost:8086
   influxdb.database=jvm_metrics
   ```

4. **No Changes Required To:**
   - MCP Tools
   - Service Layer
   - Business Logic

### Supporting Multiple Data Sources

Future enhancement to aggregate data from multiple sources:

```java
@ApplicationScoped
public class AggregatedDataSource implements JvmDataSource {
    @Inject
    @Named("prometheus")
    JvmDataSource prometheusSource;
    
    @Inject
    @Named("influxdb")
    JvmDataSource influxdbSource;
    
    @Override
    public MetricValue getCurrentValue(String metricName, Map<String, String> labels) {
        // Try primary source
        MetricValue value = prometheusSource.getCurrentValue(metricName, labels);
        
        // Fallback to secondary
        if (value == null) {
            value = influxdbSource.getCurrentValue(metricName, labels);
        }
        
        return value;
    }
}
```

## Error Handling Strategy

### Graceful Degradation

1. **Data Source Unavailable**: Return error in tool response, don't crash
2. **Missing Metrics**: Return null/empty, log warning
3. **Query Errors**: Catch exceptions, return error message
4. **Partial Data**: Return what's available with data quality indicator

### Example Error Response

```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "error": "Unable to fetch heap metrics from data source",
  "data_quality": "partial",
  "available_metrics": ["heap_used", "heap_max"]
}
```

## Performance Considerations

### Query Optimization

1. **Step Intervals**: Use larger steps for longer lookback periods
2. **Sample Limits**: Limit time-series samples to prevent memory issues
3. **Caching**: Cache frequently accessed current values (short TTL)
4. **Batch Queries**: Combine related queries when possible

### Resource Management

- Connection pooling for REST clients
- Timeout configuration for long-running queries
- Async query execution for multiple metrics (future)

## Security Considerations

1. **Authentication**: Support for Prometheus basic auth (future)
2. **TLS/SSL**: HTTPS connections to data sources
3. **Secrets Management**: Environment variables for credentials
4. **Query Injection**: Parameterized queries, input validation

## Testing Strategy

### Unit Tests
- Mock JvmDataSource for testing tools
- Test statistical calculations
- Test query building logic

### Integration Tests
- Test with real Prometheus instance
- Test error scenarios
- Test data parsing

### Example Test

```java
@QuarkusTest
public class MemoryToolsTest {
    @InjectMock
    JvmDataSource mockDataSource;
    
    @Inject
    MemoryTools memoryTools;
    
    @Test
    public void testGetHeapStatus() {
        // Mock data source responses
        when(mockDataSource.getCurrentValue("jvm_memory_heap_used_bytes", null))
            .thenReturn(MetricValue.builder()
                .value(524288000.0)
                .timestamp(Instant.now())
                .build());
        
        // Test tool
        Map<String, Object> result = memoryTools.getHeapStatus();
        
        // Assertions
        assertNotNull(result.get("heap"));
    }
}
```

## Deployment Architecture

### Standalone Deployment

```
┌─────────────────┐
│   AI Agent      │
└────────┬────────┘
         │ MCP Protocol
         ▼
┌─────────────────┐
│  MCP Server     │
│  (Port 8080)    │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────┐
│  Prometheus     │
│  (Port 9091)    │
└────────┬────────┘
         │ Scrape
         ▼
┌─────────────────┐
│  JVM with       │
│  JMX Exporter   │
└─────────────────┘
```

### Container Deployment

```yaml
version: '3.8'
services:
  mcp-server:
    image: jvm-mcp:latest
    ports:
      - "8080:8080"
    environment:
      - PROMETHEUS_URL=http://prometheus:9090
      - PROMETHEUS_JOB=liberty-jmx
    depends_on:
      - prometheus
  
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

## Future Enhancements

1. **Multi-Source Aggregation**: Combine data from multiple sources
2. **Caching Layer**: Redis/Memcached for frequently accessed metrics
3. **Async Queries**: Non-blocking query execution
4. **Metric Predictions**: ML-based trend predictions
5. **Custom Metrics**: Support for application-specific metrics
6. **Alert Integration**: Direct integration with alerting systems
7. **Metric Correlation**: Automatic correlation analysis
8. **Performance Profiling**: Integration with profiling tools

## Conclusion

This architecture provides a solid foundation for JVM observability with:
- Clean separation of concerns
- Easy extensibility for new data sources
- Maintainable and testable code
- Production-ready error handling
- Future-proof design for multiple data sources