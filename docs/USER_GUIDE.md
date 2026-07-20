# JVM JMX MCP Server - User Guide

Complete end-to-end guide for setting up, configuring, and using the JVM JMX MCP Server.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Build and Run](#build-and-run)
3. [Prometheus Configuration](#prometheus-configuration)
4. [Metrics Used](#metrics-used)
5. [Available Tools](#available-tools)
6. [Tool Examples and Sample Responses](#tool-examples-and-sample-responses)

---

## Prerequisites

### Required Software
- **Java 21** or higher
- **Maven 3.8+** for building
- **Prometheus** running and scraping JVM metrics
- **JVM application** with JMX metrics exposed (OpenJ9 or HotSpot)

### Prometheus Setup

#### 1. Install Prometheus
Download from [prometheus.io](https://prometheus.io/download/)

#### 2. Configure Prometheus to Scrape JVM Metrics

Edit `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'liberty-jmx'
    static_configs:
      - targets: ['localhost:9080']  # Your JVM application endpoint
    metrics_path: '/metrics'
```

#### 3. Start Prometheus
```bash
./prometheus --config.file=prometheus.yml
```

Prometheus will be available at `http://localhost:9090`

#### 4. Verify Metrics
Open Prometheus UI and query: `jvm_memory_heap_used_bytes`

---

## Build and Run

### Build the Project

```bash
# Clone the repository
cd jvm-mcp

# Build with Maven
./mvnw clean package -DskipTests

# Or on Windows
mvnw.cmd clean package -DskipTests
```

### Configure Application

Edit `src/main/resources/application.properties`:

```properties
# Prometheus connection
prometheus.url=http://localhost:9091
prometheus.job=liberty-jmx
prometheus.timeout=30s

# MCP Server configuration
quarkus.http.port=8088
```

### Run the Server

```bash
# Development mode (hot reload)
./mvnw quarkus:dev

# Production mode
java -jar target/quarkus-app/quarkus-run.jar
```

Server will start on `http://localhost:8088`

### Verify Server is Running

```bash
curl http://localhost:8088/q/health
```

### Change Port (Optional)

To run on a different port, either:

**Option 1:** Edit `src/main/resources/application.properties`
```properties
quarkus.http.port=YOUR_PORT
```

**Option 2:** Override at runtime
```bash
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.http.port=YOUR_PORT
```

**Option 3:** Use environment variable
```bash
export QUARKUS_HTTP_PORT=YOUR_PORT
./mvnw quarkus:dev
```

---

## Metrics Used

### Core JVM Metrics

#### Memory Metrics
- `jvm_memory_heap_used_bytes` - Current heap memory usage
- `jvm_memory_heap_max_bytes` - Maximum heap size
- `jvm_memory_heap_committed_bytes` - Committed heap memory
- `jvm_memory_heap_init_bytes` - Initial heap size
- `jvm_memory_nonheap_used_bytes` - Non-heap memory usage
- `jvm_memory_pool_used_bytes` - Per-pool memory usage
- `jvm_memory_pool_max_bytes` - Per-pool maximum
- `jvm_memory_pool_committed_bytes` - Per-pool committed
- `jvm_memory_pool_allocated_bytes_total` - Total allocated (OpenJ9)
- `jvm_memory_pool_collection_used_bytes` - Post-GC memory (OpenJ9)

#### Garbage Collection Metrics
- `jvm_gc_collection_seconds_count` - Total GC collections
- `jvm_gc_collection_seconds_sum` - Total GC time

#### Thread Metrics
- `jvm_threads_current` - Current thread count
- `jvm_threads_daemon` - Daemon thread count
- `jvm_threads_peak` - Peak thread count
- `jvm_threads_state` - Threads by state (BLOCKED, WAITING, etc.)
- `jvm_threads_deadlocked` - Deadlocked threads

#### CPU and Process Metrics (OpenJ9 Compatible)
- `process_cpu_seconds_total` - Total CPU time
- `process_resident_memory_bytes` - Physical RAM used
- `process_virtual_memory_bytes` - Virtual memory
- `process_open_fds` - Open file descriptors
- `process_max_fds` - Maximum file descriptors

#### Application Metrics
- `jvm_classes_loaded` - Loaded classes
- `jvm_runtime_info` - JVM vendor, version, runtime info
- `up` - Target availability

---

## Available Tools

### 1. Memory Investigation Tools (3 tools)

#### `getHeapStatus`
**Parameters:** None  
**Purpose:** Get current heap memory status with utilization and 5-minute trends

#### `getMemoryPoolsBreakdown`
**Parameters:** None  
**Purpose:** Detailed breakdown of all memory pools (heap and non-heap)

#### `getMemoryOverTime`
**Parameters:**
- `lookback` (optional): Time window (e.g., "5m", "1h", "2h"). Default: "1h"
- `step` (optional): Sampling interval (e.g., "30s", "1m"). Default: "1m"

**Purpose:** Memory usage trends over time

---

### 2. Garbage Collection Tools (3 tools)

#### `getGcActivity`
**Parameters:** None  
**Purpose:** Current GC activity with collection counts and pause times

#### `getGcBehaviorOverTime`
**Parameters:**
- `lookback` (optional): Time window. Default: "1h"
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** GC frequency and pause time trends

#### `getGcEfficiency`
**Parameters:**
- `window` (optional): Analysis window (e.g., "5m", "10m"). Default: "5m"

**Purpose:** Assess if GC is reclaiming memory effectively

---

### 3. Thread Investigation Tools (2 tools)

#### `getThreadState`
**Parameters:** None  
**Purpose:** Current thread counts and health metrics

#### `getThreadActivityOverTime`
**Parameters:**
- `lookback` (optional): Time window. Default: "1h"
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** Thread count trends to identify leaks or growth

---

### 4. CPU & Resource Tools (3 tools)

#### `getCpuUsage`
**Parameters:** None  
**Purpose:** JVM CPU consumption (OpenJ9 compatible)

#### `getSystemResources`
**Parameters:** None  
**Purpose:** Process memory and file descriptor usage

#### `getResourceUsageOverTime`
**Parameters:**
- `lookback` (optional): Time window. Default: "1h"
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** CPU and memory trends over time

---

### 5. Application Behavior Tools (2 tools)

#### `getClassLoadingStats`
**Parameters:** None  
**Purpose:** Class loading activity and trends

#### `getJvmRuntimeInfo`
**Parameters:** None  
**Purpose:** JVM configuration, vendor, version, and type

---

### 6. Comparative Analysis Tools (2 tools)

#### `getCurrentVsBaseline`
**Parameters:**
- `baselineTime` (optional): Baseline period (e.g., "1h", "2h ago"). Default: "1h"

**Purpose:** Compare current state to baseline period

#### `getMetricPercentiles`
**Parameters:**
- `metricCategory` (optional): Category ("memory", "gc", "threads", "cpu"). Default: "memory"
- `lookback` (optional): Time window. Default: "24h"

**Purpose:** Percentile analysis (p50, p95, p99)

---

### 7. Time-Window Investigation Tools (2 tools)

#### `getIncidentWindowData`
**Parameters:**
- `startTime` (required): ISO 8601 timestamp (e.g., "2024-01-15T10:30:00Z")
- `endTime` (required): ISO 8601 timestamp
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** All metrics during specific incident timeframe

#### `getBeforeAfterSnapshot`
**Parameters:**
- `eventTime` (required): ISO 8601 timestamp
- `window` (optional): Time before/after event (e.g., "5m"). Default: "5m"

**Purpose:** Compare metrics before and after an event

---

### 8. Correlation Analysis Tools (2 tools)

#### `getMemoryGcCorrelation`
**Parameters:**
- `lookback` (optional): Time window. Default: "1h"
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** Synchronized heap usage and GC activity

#### `getCpuGcCorrelation`
**Parameters:**
- `lookback` (optional): Time window. Default: "1h"
- `step` (optional): Sampling interval. Default: "1m"

**Purpose:** Show if GC is consuming CPU resources

---

### 9. Comprehensive Context Tools (2 tools)

#### `getJvmHealthContext`
**Parameters:** None  
**Purpose:** Complete JVM health snapshot with current state, trends, and health indicators

#### `getInvestigationBundle`
**Parameters:**
- `focusArea` (optional): Area to investigate ("memory", "gc", "threads", "cpu"). Default: "memory"
- `lookback` (optional): Time window. Default: "1h"

**Purpose:** Deep dive into specific area with all related data

---

### 10. Advanced RCA Tools (5 tools)

#### `getMemoryAllocationRate`
**Parameters:**
- `lookback` (optional): Time window. Default: "5m"

**Purpose:** Calculate memory allocation rate (MB/sec, GB/hour)

#### `getGcPressureAnalysis`
**Parameters:**
- `lookback` (optional): Time window. Default: "5m"

**Purpose:** Analyze GC pressure with score and recommendations

#### `getMemoryLeakIndicators`
**Parameters:**
- `lookback` (optional): Time window. Default: "30m"

**Purpose:** Identify potential memory leaks using trend analysis

#### `getThreadContentionAnalysis`
**Parameters:**
- `lookback` (optional): Time window. Default: "5m"

**Purpose:** Analyze thread contention and detect deadlocks

#### `getHeapFragmentationAnalysis`
**Parameters:** None  
**Purpose:** Analyze heap fragmentation across memory pools

---

## Tool Examples and Sample Responses

### Example 1: getHeapStatus

**Request:**
```json
{
  "tool": "getHeapStatus"
}
```

**Sample Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "heap": {
    "used_bytes": 536870912,
    "max_bytes": 2147483648,
    "committed_bytes": 1073741824,
    "utilization_percent": 25.0,
    "available_bytes": 1610612736
  },
  "recent_trend": {
    "window": "5m",
    "min_used_bytes": 503316480,
    "max_used_bytes": 570425344,
    "avg_used_bytes": 536870912,
    "growth_rate_bytes_per_min": 1048576
  }
}
```

---

### Example 2: getGcActivity

**Request:**
```json
{
  "tool": "getGcActivity"
}
```

**Sample Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "collectors": [
    {
      "name": "scavenge",
      "total_collections": 1523,
      "total_time_seconds": 12.45,
      "avg_pause_ms": 8.2
    },
    {
      "name": "global",
      "total_collections": 23,
      "total_time_seconds": 2.34,
      "avg_pause_ms": 101.7
    }
  ],
  "summary": {
    "total_collections": 1546,
    "total_time_seconds": 14.79,
    "overall_avg_pause_ms": 9.6
  }
}
```

---

### Example 3: getMemoryOverTime

**Request:**
```json
{
  "tool": "getMemoryOverTime",
  "parameters": {
    "lookback": "1h",
    "step": "5m"
  }
}
```

**Sample Response:**
```json
{
  "lookback": "1h",
  "step": "5m",
  "start_time": "2024-01-15T09:30:00Z",
  "end_time": "2024-01-15T10:30:00Z",
  "heap": {
    "samples": [
      {
        "timestamp": "2024-01-15T09:30:00Z",
        "used_bytes": 503316480
      },
      {
        "timestamp": "2024-01-15T09:35:00Z",
        "used_bytes": 520093696
      }
    ],
    "statistics": {
      "min_bytes": 503316480,
      "max_bytes": 570425344,
      "avg_bytes": 536870912,
      "current_bytes": 536870912
    }
  },
  "non_heap": {
    "samples": [...],
    "statistics": {...}
  }
}
```

---

### Example 4: getThreadState

**Request:**
```json
{
  "tool": "getThreadState"
}
```

**Sample Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "current_threads": 45,
  "daemon_threads": 38,
  "non_daemon_threads": 7,
  "peak_threads": 52,
  "peak_utilization_percent": 86.5
}
```

---

### Example 5: getJvmRuntimeInfo

**Request:**
```json
{
  "tool": "getJvmRuntimeInfo"
}
```

**Sample Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "heap_config": {
    "initial_heap_bytes": 536870912,
    "max_heap_bytes": 2147483648,
    "initial_heap_mb": 512,
    "max_heap_mb": 2048
  },
  "jvm_runtime_info": {
    "runtime": "Eclipse OpenJ9 VM",
    "vendor": "Eclipse OpenJ9",
    "version": "11.0.16",
    "jvm_type": "OpenJ9"
  },
  "target_info": {
    "job": "liberty-jmx",
    "instance": "localhost:9080"
  },
  "data_source": {
    "type": "prometheus",
    "url": "http://localhost:9091"
  }
}
```

---

### Example 6: getGcPressureAnalysis

**Request:**
```json
{
  "tool": "getGcPressureAnalysis",
  "parameters": {
    "lookback": "5m"
  }
}
```

**Sample Response:**
```json
{
  "lookback": "5m",
  "timestamp": "2024-01-15T10:30:00Z",
  "avg_gc_frequency_per_min": 2.3,
  "avg_gc_time_percent": 4.5,
  "max_gc_time_percent": 8.2,
  "heap_growth_percent": 2.1,
  "current_heap_utilization_percent": 65.3,
  "gc_pressure_score": 38.4,
  "interpretation": "MODERATE: Acceptable GC pressure for busy applications. Monitor trends.",
  "recommendations": []
}
```

---

### Example 7: getMemoryLeakIndicators

**Request:**
```json
{
  "tool": "getMemoryLeakIndicators",
  "parameters": {
    "lookback": "30m"
  }
}
```

**Sample Response:**
```json
{
  "lookback": "30m",
  "timestamp": "2024-01-15T10:30:00Z",
  "post_gc_heap_trend_bytes_per_sample": 524288,
  "post_gc_heap_growth_rate_percent": 0.15,
  "avg_post_gc_heap_bytes": 402653184,
  "full_gc_count_increase": 3,
  "leak_likelihood_score": 25,
  "leak_indicators": [
    "Post-GC heap usage is steadily increasing"
  ],
  "assessment": "LOW: Some concerning patterns but may be normal for growing workload."
}
```

---

### Example 8: getInvestigationBundle

**Request:**
```json
{
  "tool": "getInvestigationBundle",
  "parameters": {
    "focusArea": "memory",
    "lookback": "1h"
  }
}
```

**Sample Response:**
```json
{
  "focus_area": "memory",
  "lookback": "1h",
  "timestamp": "2024-01-15T10:30:00Z",
  "current_state": {
    "heap": {
      "used_bytes": 536870912,
      "max_bytes": 2147483648,
      "utilization_percent": 25.0
    },
    "pools": [...]
  },
  "time_series": {
    "heap_usage": {
      "samples": [...],
      "statistics": {...}
    }
  },
  "correlations": {
    "memory_gc": {
      "synchronized_data": [...],
      "correlation_analysis": {...}
    }
  }
}
```

---

### Example 9: getCurrentVsBaseline

**Request:**
```json
{
  "tool": "getCurrentVsBaseline",
  "parameters": {
    "baselineTime": "2h"
  }
}
```

**Sample Response:**
```json
{
  "current_time": "2024-01-15T10:30:00Z",
  "baseline_time": "2024-01-15T08:30:00Z",
  "comparison": {
    "heap_usage": {
      "baseline_bytes": 469762048,
      "current_bytes": 536870912,
      "delta_bytes": 67108864,
      "delta_percent": 14.3,
      "trend": "increasing"
    },
    "gc_frequency": {
      "baseline_per_min": 1.8,
      "current_per_min": 2.3,
      "delta_per_min": 0.5,
      "delta_percent": 27.8,
      "trend": "increasing"
    },
    "thread_count": {
      "baseline": 42,
      "current": 45,
      "delta": 3,
      "delta_percent": 7.1,
      "trend": "increasing"
    }
  }
}
```

---

### Example 10: getThreadContentionAnalysis

**Request:**
```json
{
  "tool": "getThreadContentionAnalysis",
  "parameters": {
    "lookback": "5m"
  }
}
```

**Sample Response:**
```json
{
  "lookback": "5m",
  "timestamp": "2024-01-15T10:30:00Z",
  "avg_blocked_threads": 1.2,
  "max_blocked_threads": 3,
  "avg_waiting_threads": 8.5,
  "avg_timed_waiting_threads": 12.3,
  "avg_runnable_threads": 23.0,
  "blocked_thread_percent": 2.7,
  "waiting_thread_percent": 18.9,
  "total_contention_percent": 21.6,
  "contention_score": 27,
  "interpretation": "HEALTHY: Low thread contention. Good synchronization patterns."
}
```

---

## Troubleshooting

### Common Issues

#### 1. Cannot Connect to Prometheus
**Error:** `Unable to fetch metrics from data source`

**Solution:**
- Verify Prometheus is running: `curl http://localhost:9091/-/healthy`
- Check `application.properties` has correct Prometheus URL
- Ensure Prometheus is scraping your JVM application

#### 2. No Metrics Available
**Error:** `Unable to fetch heap metrics from data source`

**Solution:**
- Verify JVM application is exposing metrics
- Check Prometheus targets: `http://localhost:9090/targets`
- Ensure job name matches in `application.properties`

#### 3. OpenJ9 Metrics Not Found
**Error:** Metrics like `jvm_process_cpu_load` not available

**Solution:**
- This is expected for OpenJ9 JVMs
- The server automatically uses OpenJ9-compatible metrics
- See `docs/METRICS_COMPATIBILITY.md` for details

---

## Next Steps

- Review [ARCHITECTURE.md](ARCHITECTURE.md) for system design
- Check [METRICS_COMPATIBILITY.md](METRICS_COMPATIBILITY.md) for JVM-specific metrics
- See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for feature status

---

## Support

For issues or questions:
1. Check existing documentation in `docs/` folder
2. Review tool descriptions in source code
3. Verify Prometheus configuration and metrics availability