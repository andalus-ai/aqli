# GGUF daemon cache metrics

The standalone GGUF fast path can expose the daemon-owned session-cache metrics
through a small Prometheus-compatible HTTP endpoint. This endpoint is separate
from the inference daemon socket and is intended for local monitoring.

## Enable the endpoint

The endpoint is **disabled by default**. Enable it with JVM system properties:

```bash
JAVA_TOOL_OPTIONS="-Daqli.gguf.fast_run.daemon_metrics_enabled=true \
  -Daqli.gguf.fast_run.daemon_metrics_port=9464" \
  aqli run --model <gguf-model> --prompt "Hello"
```

The JVM applies `JAVA_TOOL_OPTIONS` to the CLI process and its detached Java
daemon. The runner also forwards AQLI GGUF system properties when it launches
the daemon.

| System property | Default | Meaning |
| --- | ---: | --- |
| `aqli.gguf.fast_run.daemon_metrics_enabled` | `false` | Start the metrics endpoint |
| `aqli.gguf.fast_run.daemon_metrics_port` | `9464` | Loopback TCP port; use `0` to request an ephemeral port |

After startup, the daemon log reports `metricsPort=<port>`. When the endpoint
is disabled it reports `metricsPort=disabled`. The daemon log is normally
written to `~/.aqli/logs/gguf-fast-daemon.log`.

Choose a fixed, unused port when configuring a static Prometheus scrape target.
An ephemeral port (`0`) is useful for local diagnostics, but the selected port
must be read from the daemon log before scraping.

## Scrape locally

With the default configured port:

```bash
curl --fail http://localhost:9464/metrics
```

Example Prometheus scrape configuration:

```yaml
scrape_configs:
  - job_name: aqli-gguf-daemon
    metrics_path: /metrics
    static_configs:
      - targets: ["localhost:9464"]
```

The endpoint serves only `GET /metrics`. Other paths return `404`, and
non-GET methods return `405`.

## Exported cache measurements

The cache binder exports gauges for resident entries and active borrowed leases,
plus cumulative hit, miss and eviction counters. Each measurement has a
`pool` label with either `native` (llama.cpp) or `java` (Java-native GGUF).
No model path, model name, prompt, or request content is attached as a label.

The endpoint is a process-local view of the standalone GGUF daemon's cache. It
does not expose the CLI parent's Micrometer registry, general request metrics,
or metrics for other runners.

## Network and lifecycle constraints

- The listener binds to the local loopback interface only.
- It is unauthenticated; any local process that can connect to the port can
  scrape it. Do not expose it through a public interface, reverse proxy, or
  port-forward.
- The listener starts with the daemon and is closed during orderly daemon
  shutdown. It does not add routes to the inference control socket.
- If the configured port is already occupied, daemon startup fails rather than
  silently selecting a different fixed port.
- Leave the feature disabled when local scraping is not required.

This endpoint is an operational aid for the standalone GGUF daemon; it is not
a general AQLI observability or remote monitoring API.
