# JVM Observability MCP Server - Tool Specification

## Document Purpose
This document defines the tools for a Model Context Protocol (MCP) server that provides JVM observability data from Prometheus to AI agents for investigation and root cause analysis.

---

## Available Prometheus Metrics

Based on the JMX Exporter configuration in [`vmdemo/modules/03_dynamic_attach_jmx.sh`](modules/03_dynamic_attach_jmx.sh), the following metrics are exported:

### Memory Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `jvm_memory_heap_used_bytes` | GAUGE | JVM heap memory used in bytes | - |
| `jvm_memory_heap_max_bytes` | GAUGE | JVM heap memory max in bytes | - |
| `jvm_memory_heap_committed_bytes` | GAUGE | JVM heap memory committed in bytes | - |
| `jvm_memory_heap_init_bytes` | GAUGE | JVM heap memory init in bytes | - |
| `jvm_memory_nonheap_used_bytes` | GAUGE | JVM non-heap memory used in bytes | - |
| `jvm_memory_nonheap_max_bytes` | GAUGE | JVM non-heap memory max in bytes | - |
| `jvm_memory_nonheap_committed_bytes` | GAUGE | JVM non-heap memory committed in bytes | - |
| `jvm_memory_nonheap_init_bytes` | GAUGE | JVM non-heap memory init in bytes | - |
| `jvm_memory_pool_used_bytes` | GAUGE | JVM memory pool used in bytes | `pool` (pool name) |
| `jvm_memory_pool_max_bytes` | GAUGE | JVM memory pool max in bytes | `pool` (pool name) |
| `jvm_memory_pool_committed_bytes` | GAUGE | JVM memory pool committed in bytes | `pool` (pool name) |

### Garbage Collection Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `jvm_gc_collection_seconds_count` | COUNTER | Total number of GC collections | `gc` (GC name) |
| `jvm_gc_collection_seconds_sum` | COUNTER | Total GC collection time in seconds | `gc` (GC name) |

### Thread Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `jvm_threads_current` | GAUGE | Current number of live threads | - |
| `jvm_threads_daemon` | GAUGE | Current number of daemon threads | - |
| `jvm_threads_peak` | GAUGE | Peak live thread count since JVM start | - |

### Class Loading Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `jvm_classes_loaded` | GAUGE | Number of currently loaded classes | - |

### CPU & Operating System Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `jvm_process_cpu_load` | GAUGE | JVM process CPU load (0.0–1.0) | - |
| `jvm_system_cpu_load` | GAUGE | System CPU load (0.0–1.0) | - |
| `jvm_os_free_physical_memory_bytes` | GAUGE | Free physical memory on the OS in bytes | - |
| `jvm_os_total_physical_memory_bytes` | GAUGE | Total physical memory on the OS in bytes | - |

### Prometheus Meta Metrics
| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `up` | GAUGE | Target scrape status (1=up, 0=down) | `job`, `instance` |

---

## Tool Specifications

### 1. Memory Investigation Tools

#### Tool: `get_heap_status`

**Purpose:** Provide current heap memory status for AI to assess memory pressure.

**Metrics Used:**
- `jvm_memory_heap_used_bytes` (current value)
- `jvm_memory_heap_max_bytes` (current value)
- `jvm_memory_heap_committed_bytes` (current value)
- `jvm_memory_heap_used_bytes` (last 5 minutes for trend)

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "heap": {
    "used_bytes": 524288000,
    "max_bytes": 1073741824,
    "committed_bytes": 1073741824,
    "utilization_percent": 48.8,
    "available_bytes": 549453824
  },
  "recent_trend": {
    "window": "5m",
    "min_used_bytes": 498073600,
    "max_used_bytes": 536870912,
    "avg_used_bytes": 515899392,
    "growth_rate_bytes_per_min": 5242880
  }
}
```

**PromQL Queries:**
```promql
# Current values
jvm_memory_heap_used_bytes{job="liberty-jmx"}
jvm_memory_heap_max_bytes{job="liberty-jmx"}
jvm_memory_heap_committed_bytes{job="liberty-jmx"}

# 5-minute trend
jvm_memory_heap_used_bytes{job="liberty-jmx"}[5m]
```

---

#### Tool: `get_memory_pools_breakdown`

**Purpose:** Show which specific memory pools are under pressure.

**Metrics Used:**
- `jvm_memory_pool_used_bytes` (all pools)
- `jvm_memory_pool_max_bytes` (all pools)
- `jvm_memory_pool_committed_bytes` (all pools)

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "pools": [
    {
      "name": "G1 Eden Space",
      "type": "heap",
      "used_bytes": 104857600,
      "max_bytes": 536870912,
      "committed_bytes": 209715200,
      "utilization_percent": 19.5
    },
    {
      "name": "G1 Old Gen",
      "type": "heap",
      "used_bytes": 419430400,
      "max_bytes": 1073741824,
      "committed_bytes": 864026624,
      "utilization_percent": 39.1
    },
    {
      "name": "Metaspace",
      "type": "non-heap",
      "used_bytes": 52428800,
      "max_bytes": -1,
      "committed_bytes": 54525952,
      "utilization_percent": null
    }
  ]
}
```

**PromQL Queries:**
```promql
jvm_memory_pool_used_bytes{job="liberty-jmx"}
jvm_memory_pool_max_bytes{job="liberty-jmx"}
jvm_memory_pool_committed_bytes{job="liberty-jmx"}
```

**Note:** Pool names and types vary by JVM (HotSpot vs OpenJ9). Common pools include:
- **HotSpot/G1GC:** G1 Eden Space, G1 Survivor Space, G1 Old Gen, Metaspace, CodeHeap
- **OpenJ9:** nursery-allocate, nursery-survivor, tenured, class storage, JIT code cache

---

#### Tool: `get_memory_over_time`

**Purpose:** Show memory behavior trends over a specified time window.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h", "24h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_memory_heap_used_bytes` (range query)
- `jvm_memory_heap_max_bytes` (range query)
- `jvm_memory_nonheap_used_bytes` (range query)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "heap": {
    "samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "used_bytes": 498073600},
      {"timestamp": "2026-07-15T03:01:00Z", "used_bytes": 503316480},
      ...
    ],
    "statistics": {
      "min_bytes": 498073600,
      "max_bytes": 536870912,
      "avg_bytes": 515899392,
      "current_bytes": 524288000
    }
  },
  "non_heap": {
    "samples": [...],
    "statistics": {...}
  }
}
```

**PromQL Queries:**
```promql
# Range query for time series
jvm_memory_heap_used_bytes{job="liberty-jmx"}[1h:1m]
jvm_memory_heap_max_bytes{job="liberty-jmx"}[1h:1m]
jvm_memory_nonheap_used_bytes{job="liberty-jmx"}[1h:1m]
```

---

### 2. Garbage Collection Investigation Tools

#### Tool: `get_gc_activity`

**Purpose:** Show how hard GC is working currently.

**Metrics Used:**
- `jvm_gc_collection_seconds_count` (all GC types)
- `jvm_gc_collection_seconds_sum` (all GC types)

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "collectors": [
    {
      "name": "G1 Young Generation",
      "total_collections": 1523,
      "total_time_seconds": 45.234,
      "avg_pause_ms": 29.7
    },
    {
      "name": "G1 Old Generation",
      "total_collections": 3,
      "total_time_seconds": 1.856,
      "avg_pause_ms": 618.7
    }
  ],
  "summary": {
    "total_collections": 1526,
    "total_time_seconds": 47.09,
    "overall_avg_pause_ms": 30.9
  }
}
```

**PromQL Queries:**
```promql
jvm_gc_collection_seconds_count{job="liberty-jmx"}
jvm_gc_collection_seconds_sum{job="liberty-jmx"}
```

**Calculations:**
- `avg_pause_ms = (total_time_seconds / total_collections) * 1000`

---

#### Tool: `get_gc_behavior_over_time`

**Purpose:** Show GC frequency and pause time trends.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h", "24h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_gc_collection_seconds_count` (range query with rate)
- `jvm_gc_collection_seconds_sum` (range query with rate)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "gc_frequency": {
    "samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "collections_per_min": 2.1},
      {"timestamp": "2026-07-15T03:01:00Z", "collections_per_min": 2.3},
      ...
    ],
    "statistics": {
      "min_per_min": 1.8,
      "max_per_min": 4.5,
      "avg_per_min": 2.4
    }
  },
  "gc_time_overhead": {
    "samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "gc_time_percent": 3.2},
      {"timestamp": "2026-07-15T03:01:00Z", "gc_time_percent": 3.5},
      ...
    ],
    "statistics": {
      "min_percent": 2.8,
      "max_percent": 8.1,
      "avg_percent": 3.6
    }
  }
}
```

**PromQL Queries:**
```promql
# GC collections per minute
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])

# GC time as percentage of wall-clock time
rate(jvm_gc_collection_seconds_sum{job="liberty-jmx"}[1m]) * 100
```

---

#### Tool: `get_gc_efficiency`

**Purpose:** Assess if GC is reclaiming memory effectively.

**Parameters:**
- `window`: Time window for analysis (default: "5m")

**Metrics Used:**
- `jvm_memory_heap_used_bytes` (before/after GC cycles)
- `jvm_gc_collection_seconds_count` (rate)
- `jvm_gc_collection_seconds_sum` (rate)

**Data Returned:**
```json
{
  "window": "5m",
  "timestamp": "2026-07-15T04:00:00Z",
  "gc_overhead_percent": 3.8,
  "collections_per_minute": 2.4,
  "heap_reclamation": {
    "avg_heap_before_gc_bytes": 734003200,
    "avg_heap_after_gc_bytes": 524288000,
    "avg_reclaimed_bytes": 209715200,
    "reclamation_rate_percent": 28.6
  }
}
```

**PromQL Queries:**
```promql
# GC overhead
rate(jvm_gc_collection_seconds_sum{job="liberty-jmx"}[5m]) * 100

# GC frequency
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[5m]) * 60

# Heap usage (need to correlate with GC events)
jvm_memory_heap_used_bytes{job="liberty-jmx"}[5m]
```

**Note:** Calculating exact before/after GC heap values requires correlating heap metrics with GC timestamps, which may need additional logic to identify GC event boundaries in the time series.

---

### 3. Thread Investigation Tools

#### Tool: `get_thread_state`

**Purpose:** Show current thread counts and health.

**Metrics Used:**
- `jvm_threads_current`
- `jvm_threads_daemon`
- `jvm_threads_peak`

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "current_threads": 45,
  "daemon_threads": 38,
  "non_daemon_threads": 7,
  "peak_threads": 52,
  "peak_utilization_percent": 86.5
}
```

**PromQL Queries:**
```promql
jvm_threads_current{job="liberty-jmx"}
jvm_threads_daemon{job="liberty-jmx"}
jvm_threads_peak{job="liberty-jmx"}
```

**Calculations:**
- `non_daemon_threads = current_threads - daemon_threads`
- `peak_utilization_percent = (current_threads / peak_threads) * 100`

---

#### Tool: `get_thread_activity_over_time`

**Purpose:** Show thread count trends to identify leaks or growth.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h", "24h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_threads_current` (range query)
- `jvm_threads_daemon` (range query)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "thread_count": {
    "samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "total": 42, "daemon": 36, "non_daemon": 6},
      {"timestamp": "2026-07-15T03:01:00Z", "total": 43, "daemon": 37, "non_daemon": 6},
      ...
    ],
    "statistics": {
      "min_total": 42,
      "max_total": 52,
      "avg_total": 45,
      "growth_rate_per_hour": 3.2
    }
  }
}
```

**PromQL Queries:**
```promql
jvm_threads_current{job="liberty-jmx"}[1h:1m]
jvm_threads_daemon{job="liberty-jmx"}[1h:1m]
```

---

### 4. CPU & Resource Investigation Tools

#### Tool: `get_cpu_usage`

**Purpose:** Show JVM and system CPU consumption.

**Metrics Used:**
- `jvm_process_cpu_load`
- `jvm_system_cpu_load`

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "process_cpu_percent": 12.5,
  "system_cpu_percent": 45.3,
  "process_cpu_share_of_system": 27.6,
  "recent_5m": {
    "avg_process_cpu_percent": 11.8,
    "max_process_cpu_percent": 18.2,
    "avg_system_cpu_percent": 43.1
  }
}
```

**PromQL Queries:**
```promql
# Current values (multiply by 100 for percentage)
jvm_process_cpu_load{job="liberty-jmx"} * 100
jvm_system_cpu_load{job="liberty-jmx"} * 100

# 5-minute averages
avg_over_time(jvm_process_cpu_load{job="liberty-jmx"}[5m]) * 100
avg_over_time(jvm_system_cpu_load{job="liberty-jmx"}[5m]) * 100
max_over_time(jvm_process_cpu_load{job="liberty-jmx"}[5m]) * 100
```

---

#### Tool: `get_system_resources`

**Purpose:** Show host-level memory availability.

**Metrics Used:**
- `jvm_os_total_physical_memory_bytes`
- `jvm_os_free_physical_memory_bytes`

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "physical_memory": {
    "total_bytes": 17179869184,
    "free_bytes": 4294967296,
    "used_bytes": 12884901888,
    "utilization_percent": 75.0
  }
}
```

**PromQL Queries:**
```promql
jvm_os_total_physical_memory_bytes{job="liberty-jmx"}
jvm_os_free_physical_memory_bytes{job="liberty-jmx"}
```

**Calculations:**
- `used_bytes = total_bytes - free_bytes`
- `utilization_percent = (used_bytes / total_bytes) * 100`

---

#### Tool: `get_resource_usage_over_time`

**Purpose:** Show CPU and system memory trends.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h", "24h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_process_cpu_load` (range query)
- `jvm_system_cpu_load` (range query)
- `jvm_os_free_physical_memory_bytes` (range query)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "cpu": {
    "process_samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "percent": 11.2},
      ...
    ],
    "system_samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "percent": 42.5},
      ...
    ]
  },
  "system_memory": {
    "free_memory_samples": [
      {"timestamp": "2026-07-15T03:00:00Z", "bytes": 4563402752},
      ...
    ]
  }
}
```

**PromQL Queries:**
```promql
jvm_process_cpu_load{job="liberty-jmx"}[1h:1m] * 100
jvm_system_cpu_load{job="liberty-jmx"}[1h:1m] * 100
jvm_os_free_physical_memory_bytes{job="liberty-jmx"}[1h:1m]
```

---

### 5. Application Behavior Tools

#### Tool: `get_class_loading_stats`

**Purpose:** Show class loading activity.

**Metrics Used:**
- `jvm_classes_loaded`

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "loaded_classes": 12543,
  "recent_5m": {
    "min_loaded": 12480,
    "max_loaded": 12543,
    "avg_loaded": 12512,
    "growth_rate_per_hour": 75.6
  }
}
```

**PromQL Queries:**
```promql
jvm_classes_loaded{job="liberty-jmx"}
jvm_classes_loaded{job="liberty-jmx"}[5m]
```

---

#### Tool: `get_jvm_runtime_info`

**Purpose:** Provide JVM configuration context.

**Metrics Used:**
- `jvm_memory_heap_max_bytes` (to infer Xmx)
- `jvm_memory_heap_init_bytes` (to infer Xms)
- Prometheus target labels (for version info if available)

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "heap_config": {
    "initial_heap_bytes": 268435456,
    "max_heap_bytes": 1073741824,
    "initial_heap_mb": 256,
    "max_heap_mb": 1024
  },
  "target_info": {
    "job": "liberty-jmx",
    "instance": "127.0.0.1:9404",
    "app": "liberty",
    "host": "vm-demo-01"
  }
}
```

**PromQL Queries:**
```promql
jvm_memory_heap_max_bytes{job="liberty-jmx"}
jvm_memory_heap_init_bytes{job="liberty-jmx"}

# Target metadata
up{job="liberty-jmx"}
```

**Note:** JVM version and GC algorithm are not directly exposed by JMX Exporter. These would need to be added as custom metrics or obtained from Liberty logs/configuration.

---

### 6. Alert & Incident Context Tools

#### Tool: `get_current_alerts`

**Purpose:** Show what monitoring systems think is wrong.

**Metrics Used:**
- Prometheus Alerts API: `/api/v1/alerts`

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "active_alerts": [
    {
      "name": "LibertyHighHeapUsage",
      "state": "firing",
      "severity": "warning",
      "started_at": "2026-07-15T03:45:00Z",
      "duration_seconds": 900,
      "labels": {
        "app": "liberty",
        "host": "vm-demo-01",
        "severity": "warning"
      },
      "annotations": {
        "description": "Heap usage is above 60% for more than 1 minute",
        "summary": "Liberty heap usage: 65%"
      },
      "current_value": 65.2
    }
  ],
  "alert_count": {
    "firing": 1,
    "pending": 0
  }
}
```

**API Endpoint:**
```
GET http://prometheus:9090/api/v1/alerts
```

---

#### Tool: `get_recent_alert_history`

**Purpose:** Show alert patterns over time.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "24h")

**Metrics Used:**
- `ALERTS{alertname="..."}` (Prometheus special metric)
- `ALERTS_FOR_STATE{alertname="..."}` (time in current state)

**Data Returned:**
```json
{
  "lookback": "24h",
  "start_time": "2026-07-14T04:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "alert_timeline": [
    {
      "alert_name": "LibertyHighHeapUsage",
      "occurrences": 3,
      "total_firing_duration_seconds": 2700,
      "events": [
        {
          "state": "firing",
          "started_at": "2026-07-14T10:15:00Z",
          "ended_at": "2026-07-14T10:30:00Z",
          "duration_seconds": 900
        },
        {
          "state": "firing",
          "started_at": "2026-07-14T16:20:00Z",
          "ended_at": "2026-07-14T16:35:00Z",
          "duration_seconds": 900
        },
        {
          "state": "firing",
          "started_at": "2026-07-15T03:45:00Z",
          "ended_at": null,
          "duration_seconds": 900
        }
      ]
    }
  ]
}
```

**PromQL Queries:**
```promql
ALERTS{job="liberty-jmx"}[24h]
ALERTS_FOR_STATE{job="liberty-jmx"}[24h]
```

---

### 7. Comparative Analysis Tools

#### Tool: `get_current_vs_baseline`

**Purpose:** Compare current state to a baseline period.

**Parameters:**
- `baseline_time`: Time reference (e.g., "1h ago", "2026-07-15T03:00:00Z")

**Metrics Used:**
- All key metrics at current time vs baseline time

**Data Returned:**
```json
{
  "current_time": "2026-07-15T04:00:00Z",
  "baseline_time": "2026-07-15T03:00:00Z",
  "comparison": {
    "heap_usage": {
      "baseline_bytes": 419430400,
      "current_bytes": 524288000,
      "delta_bytes": 104857600,
      "delta_percent": 25.0,
      "trend": "increasing"
    },
    "gc_frequency": {
      "baseline_per_min": 1.8,
      "current_per_min": 2.4,
      "delta_per_min": 0.6,
      "delta_percent": 33.3,
      "trend": "increasing"
    },
    "thread_count": {
      "baseline": 42,
      "current": 45,
      "delta": 3,
      "delta_percent": 7.1,
      "trend": "increasing"
    },
    "cpu_usage": {
      "baseline_percent": 10.5,
      "current_percent": 12.5,
      "delta_percent": 2.0,
      "trend": "increasing"
    }
  }
}
```

**PromQL Queries:**
```promql
# Current values
jvm_memory_heap_used_bytes{job="liberty-jmx"}
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m]) * 60
jvm_threads_current{job="liberty-jmx"}
jvm_process_cpu_load{job="liberty-jmx"} * 100

# Baseline values (offset by 1h)
jvm_memory_heap_used_bytes{job="liberty-jmx"} offset 1h
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m] offset 1h) * 60
jvm_threads_current{job="liberty-jmx"} offset 1h
jvm_process_cpu_load{job="liberty-jmx"} offset 1h * 100
```

---

#### Tool: `get_metric_percentiles`

**Purpose:** Show what's normal vs exceptional for metrics.

**Parameters:**
- `metric_category`: One of "memory", "gc", "threads", "cpu"
- `lookback`: Duration for percentile calculation (e.g., "24h", "7d")

**Metrics Used:**
- Relevant metrics based on category

**Data Returned:**
```json
{
  "category": "memory",
  "lookback": "24h",
  "start_time": "2026-07-14T04:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "heap_usage_bytes": {
    "p50": 471859200,
    "p95": 629145600,
    "p99": 681574400,
    "current": 524288000,
    "current_percentile": 68
  },
  "gc_frequency_per_min": {
    "p50": 2.0,
    "p95": 3.8,
    "p99": 4.5,
    "current": 2.4,
    "current_percentile": 62
  }
}
```

**PromQL Queries:**
```promql
# Percentiles using histogram_quantile or direct quantile_over_time
quantile_over_time(0.50, jvm_memory_heap_used_bytes{job="liberty-jmx"}[24h])
quantile_over_time(0.95, jvm_memory_heap_used_bytes{job="liberty-jmx"}[24h])
quantile_over_time(0.99, jvm_memory_heap_used_bytes{job="liberty-jmx"}[24h])

quantile_over_time(0.50, rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[24h:1m]) * 60
quantile_over_time(0.95, rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[24h:1m]) * 60
quantile_over_time(0.99, rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[24h:1m]) * 60
```

---

### 8. Time-Window Investigation Tools

#### Tool: `get_incident_window_data`

**Purpose:** Get all metrics during a specific incident timeframe.

**Parameters:**
- `start_time`: Incident start (ISO 8601 timestamp)
- `end_time`: Incident end (ISO 8601 timestamp)
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- All available metrics within the time window

**Data Returned:**
```json
{
  "start_time": "2026-07-15T02:00:00Z",
  "end_time": "2026-07-15T02:30:00Z",
  "step": "1m",
  "duration_seconds": 1800,
  "metrics": {
    "heap_usage": {
      "samples": [...],
      "min": 419430400,
      "max": 734003200,
      "avg": 576716800,
      "start_value": 419430400,
      "end_value": 734003200,
      "change_percent": 75.0
    },
    "gc_activity": {
      "total_collections": 45,
      "total_gc_time_seconds": 2.3,
      "avg_collections_per_min": 1.5,
      "max_collections_per_min": 3.2
    },
    "thread_count": {
      "samples": [...],
      "min": 42,
      "max": 58,
      "avg": 48,
      "start_value": 42,
      "end_value": 58,
      "change_percent": 38.1
    },
    "cpu_usage": {
      "samples": [...],
      "avg_process_percent": 15.2,
      "max_process_percent": 28.5
    }
  }
}
```

**PromQL Queries:**
```promql
# Range queries for the incident window
jvm_memory_heap_used_bytes{job="liberty-jmx"}[2026-07-15T02:00:00Z:2026-07-15T02:30:00Z:1m]
jvm_gc_collection_seconds_count{job="liberty-jmx"}[2026-07-15T02:00:00Z:2026-07-15T02:30:00Z:1m]
jvm_threads_current{job="liberty-jmx"}[2026-07-15T02:00:00Z:2026-07-15T02:30:00Z:1m]
jvm_process_cpu_load{job="liberty-jmx"}[2026-07-15T02:00:00Z:2026-07-15T02:30:00Z:1m]
```

---

#### Tool: `get_before_after_snapshot`

**Purpose:** Compare metrics before and after an event (deployment, restart, etc.).

**Parameters:**
- `event_time`: Event timestamp (ISO 8601)
- `window`: Time window before/after (e.g., "5m", "15m")

**Metrics Used:**
- All key metrics

**Data Returned:**
```json
{
  "event_time": "2026-07-15T02:15:00Z",
  "window": "5m",
  "before": {
    "time_range": "2026-07-15T02:10:00Z to 2026-07-15T02:15:00Z",
    "heap_usage_bytes": {
      "avg": 419430400,
      "min": 398458880,
      "max": 440401920
    },
    "gc_collections_per_min": 1.8,
    "thread_count": 42,
    "cpu_percent": 10.5
  },
  "after": {
    "time_range": "2026-07-15T02:15:00Z to 2026-07-15T02:20:00Z",
    "heap_usage_bytes": {
      "avg": 209715200,
      "min": 188743680,
      "max": 230686720
    },
    "gc_collections_per_min": 0.8,
    "thread_count": 45,
    "cpu_percent": 8.2
  },
  "changes": {
    "heap_usage_delta_percent": -50.0,
    "gc_frequency_delta_percent": -55.6,
    "thread_count_delta": 3,
    "cpu_delta_percent": -2.3
  }
}
```

**PromQL Queries:**
```promql
# Before window (5m before event)
avg_over_time(jvm_memory_heap_used_bytes{job="liberty-jmx"}[5m] @ 1720924500)
avg_over_time(rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[5m] @ 1720924500) * 60

# After window (5m after event)
avg_over_time(jvm_memory_heap_used_bytes{job="liberty-jmx"}[5m] @ 1720924800)
avg_over_time(rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[5m] @ 1720924800) * 60
```

**Note:** The `@` modifier in PromQL allows querying at a specific timestamp.

---

### 9. Correlation Data Tools

#### Tool: `get_memory_gc_correlation`

**Purpose:** Show synchronized heap usage and GC activity for cause-effect analysis.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_memory_heap_used_bytes`
- `jvm_gc_collection_seconds_count` (rate)
- `jvm_gc_collection_seconds_sum` (rate)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "synchronized_data": [
    {
      "timestamp": "2026-07-15T03:00:00Z",
      "heap_used_bytes": 419430400,
      "heap_utilization_percent": 39.1,
      "gc_collections_per_min": 1.8,
      "gc_time_percent": 2.9
    },
    {
      "timestamp": "2026-07-15T03:01:00Z",
      "heap_used_bytes": 440401920,
      "heap_utilization_percent": 41.0,
      "gc_collections_per_min": 2.1,
      "gc_time_percent": 3.2
    },
    ...
  ],
  "correlation_analysis": {
    "heap_gc_frequency_correlation": 0.87,
    "heap_gc_time_correlation": 0.82
  }
}
```

**PromQL Queries:**
```promql
jvm_memory_heap_used_bytes{job="liberty-jmx"}[1h:1m]
rate(jvm_gc_collection_seconds_count{job="liberty-jmx"}[1m])[1h:1m] * 60
rate(jvm_gc_collection_seconds_sum{job="liberty-jmx"}[1m])[1h:1m] * 100
```

**Note:** Correlation coefficient calculation would be done in the MCP server logic, not in PromQL.

---

#### Tool: `get_cpu_gc_correlation`

**Purpose:** Show if GC is consuming CPU resources.

**Parameters:**
- `lookback`: Duration string (e.g., "1h", "6h")
- `step`: Sample interval (optional, default: "1m")

**Metrics Used:**
- `jvm_process_cpu_load`
- `jvm_gc_collection_seconds_sum` (rate)

**Data Returned:**
```json
{
  "lookback": "1h",
  "step": "1m",
  "start_time": "2026-07-15T03:00:00Z",
  "end_time": "2026-07-15T04:00:00Z",
  "synchronized_data": [
    {
      "timestamp": "2026-07-15T03:00:00Z",
      "process_cpu_percent": 10.5,
      "gc_time_percent": 2.9,
      "non_gc_cpu_percent": 7.6
    },
    {
      "timestamp": "2026-07-15T03:01:00Z",
      "process_cpu_percent": 12.8,
      "gc_time_percent": 3.2,
      "non_gc_cpu_percent": 9.6
    },
    ...
  ],
  "correlation_analysis": {
    "cpu_gc_correlation": 0.76,
    "gc_cpu_contribution_avg_percent": 25.3
  }
}
```

**PromQL Queries:**
```promql
jvm_process_cpu_load{job="liberty-jmx"}[1h:1m] * 100
rate(jvm_gc_collection_seconds_sum{job="liberty-jmx"}[1m])[1h:1m] * 100
```

---

### 10. Comprehensive Context Tools

#### Tool: `get_jvm_health_context`

**Purpose:** Provide complete current state + recent trends in one call.

**Metrics Used:**
- All current metrics
- 5-minute trends for all metrics

**Data Returned:**
```json
{
  "timestamp": "2026-07-15T04:00:00Z",
  "current_state": {
    "heap": { /* from get_heap_status */ },
    "gc": { /* from get_gc_activity */ },
    "threads": { /* from get_thread_state */ },
    "cpu": { /* from get_cpu_usage */ },
    "system_memory": { /* from get_system_resources */ },
    "classes": { /* from get_class_loading_stats */ }
  },
  "recent_trends_5m": {
    "heap_growth_rate_bytes_per_min": 5242880,
    "gc_frequency_trend": "increasing",
    "thread_count_trend": "stable",
    "cpu_trend": "stable"
  },
  "active_alerts": [ /* from get_current_alerts */ ],
  "health_indicators": {
    "heap_pressure": "moderate",
    "gc_pressure": "low",
    "thread_health": "healthy",
    "cpu_health": "healthy"
  }
}
```

**PromQL Queries:**
- Combination of all single-point and 5-minute trend queries from previous tools

**Note:** `health_indicators` are simple categorizations based on thresholds (e.g., heap > 80% = "high pressure"), not AI analysis.

---

#### Tool: `get_investigation_bundle`

**Purpose:** Deep dive into one specific area with all related data.

**Parameters:**
- `focus_area`: One of "memory", "gc", "threads", "cpu"
- `lookback`: Duration string (e.g., "1h", "6h")

**Metrics Used:**
- All metrics related to the focus area

**Data Returned (example for focus_area="memory"):**
```json
{
  "focus_area": "memory",
  "lookback": "1h",
  "timestamp": "2026-07-15T04:00:00Z",
  "current_state": {
    "heap": { /* from get_heap_status */ },
    "pools": { /* from get_memory_pools_breakdown */ }
  },
  "time_series": {
    "heap_usage": { /* from get_memory_over_time */ }
  },
  "correlations": {
    "memory_gc": { /* from get_memory_gc_correlation */ }
  },
  "statistics": {
    "heap_percentiles": { /* from get_metric_percentiles */ }
  },
  "alerts": {
    "memory_related_alerts": [ /* filtered from get_current_alerts */ ]
  }
}
```

**PromQL Queries:**
- Aggregation of all queries from tools related to the focus area

---

## Implementation Notes

### Prometheus Query Patterns

1. **Current Value:**
   ```promql
   metric_name{job="liberty-jmx"}
   ```

2. **Range Query (time series):**
   ```promql
   metric_name{job="liberty-jmx"}[1h:1m]
   ```

3. **Rate Calculation:**
   ```promql
   rate(counter_metric{job="liberty-jmx"}[1m])
   ```

4. **Aggregation Over Time:**
   ```promql
   avg_over_time(metric_name{job="liberty-jmx"}[5m])
   max_over_time(metric_name{job="liberty-jmx"}[5m])
   min_over_time(metric_name{job="liberty-jmx"}[5m])
   ```

5. **Percentiles:**
   ```promql
   quantile_over_time(0.95, metric_name{job="liberty-jmx"}[24h])
   ```

6. **Time Offset:**
   ```promql
   metric_name{job="liberty-jmx"} offset 1h
   ```

7. **Query at Specific Time:**
   ```promql
   metric_name{job="liberty-jmx"} @ 1720924500
   ```

### Data Consolidation Strategy

1. **Fetch raw data from Prometheus** using appropriate PromQL queries
2. **Calculate derived metrics** (percentages, rates, deltas) in MCP server
3. **Format as structured JSON** with clear field names
4. **Include metadata** (timestamps, units, labels) for context
5. **Aggregate related metrics** into logical groups
6. **Provide statistics** (min/max/avg) for time-series data

### Error Handling

- Return empty/null values if metrics are unavailable
- Include `data_quality` field indicating if data is complete
- Provide `query_errors` array if Prometheus queries fail
- Include `metric_availability` status for each metric

### Performance Considerations

- Use appropriate `step` intervals for range queries (larger steps for longer lookbacks)
- Limit time-series sample counts (e.g., max 1000 points)
- Cache frequently accessed current values (with short TTL)
- Use Prometheus query result caching where possible

---

## Tool Priority Recommendations

### High Priority (Core Investigation)
1. `get_heap_status` - Most common issue
2. `get_gc_activity` - Directly impacts performance
3. `get_current_alerts` - Immediate context
4. `get_jvm_health_context` - Quick overview
5. `get_memory_over_time` - Trend analysis

### Medium Priority (Deep Dive)
6. `get_memory_pools_breakdown` - Detailed memory analysis
7. `get_gc_behavior_over_time` - GC trend analysis
8. `get_thread_state` - Thread health
9. `get_cpu_usage` - Resource consumption
10. `get_incident_window_data` - RCA support

### Lower Priority (Advanced Analysis)
11. `get_memory_gc_correlation` - Pattern analysis
12. `get_current_vs_baseline` - Comparison
13. `get_metric_percentiles` - Statistical analysis
14. `get_investigation_bundle` - Comprehensive deep dive

---

## Conclusion

This specification provides a complete mapping of available Prometheus metrics to investigation-oriented MCP tools. Each tool is designed to answer specific questions an AI agent would ask during JVM troubleshooting, with clear metric sources and data formats.

The tools focus on **data provision** rather than analysis, allowing AI agents to perform their own reasoning while having access to well-structured, contextual JVM observability data.