# AQLI GGUF integration

This module contains format-level AQLI integration that is independent of any
specific GGUF execution provider.

## Responsibilities

- Generic GGUF daemon framing, request decoding, dispatch, and server-loop
  contracts.
- AQLI runner integration for GGUF-based non-text workflows where implemented.

## Execution providers

This module intentionally does **not** depend on llama.cpp and does not bundle
native llama.cpp build scripts or FFM bindings.

- Java-native GGUF execution is implemented by Aljabr's
  `aljabr-gguf-java` provider and is the target default after its model-family
  correctness and performance gates are met.
- llama.cpp is optional and is isolated in
  `aqli-runner-gguf-llamacpp`. Its native build helper and ABI shim live there.
- Select an execution provider independently from the model format. The format
  remains `gguf`; provider IDs identify the executor (for example `java` or
  `llamacpp`).

The generic runner must remain usable when llama.cpp headers, libraries,
artifacts, and optional modules are absent.

## Standalone daemon cache metrics

The standalone GGUF daemon can optionally expose its session-cache metrics in
Prometheus text format. The endpoint is owned by the daemon process (the process
that owns the cache), rather than the parent CLI process.

Metrics are disabled by default. Enable them with the standalone GGUF
configuration properties:

| Property | Environment variable | Default | Meaning |
| --- | --- | --- | --- |
| `aqli.gguf.fast_run.daemon_metrics_enabled` | `AQLI_GGUF_FAST_RUN_DAEMON_METRICS_ENABLED` | `false` | Start the metrics endpoint |
| `aqli.gguf.fast_run.daemon_metrics_port` | `AQLI_GGUF_FAST_RUN_DAEMON_METRICS_PORT` | `9464` | Loopback TCP port; `0` selects an ephemeral port |

System properties take precedence over environment variables. For example:

```bash
export AQLI_GGUF_FAST_RUN_DAEMON_METRICS_ENABLED=true
export AQLI_GGUF_FAST_RUN_DAEMON_METRICS_PORT=9464

aqli run --model ./models/model.gguf --prompt "Hello"
```

The daemon startup log includes `metricsPort=9464` when enabled. Scrape the
endpoint locally:

```bash
curl --fail http://127.0.0.1:9464/metrics
```

Only `GET /metrics` is served. The listener binds to the loopback interface;
it is not remotely accessible from another machine or container network
namespace. Keep Prometheus on the same host/network namespace, or use a
deliberate local forwarding mechanism rather than changing the bind address.

A minimal Prometheus scrape configuration for a daemon using the default port:

```yaml
scrape_configs:
  - job_name: aqli-gguf-daemon
    metrics_path: /metrics
    static_configs:
      - targets: ["127.0.0.1:9464"]
```

The endpoint exports the native and Java backend cache pools separately:
resident entries, active borrowed leases, hits, misses, and evictions. Cache
counters are process-local and reset when the daemon restarts. Metrics do not
include model paths or other per-model labels. Setting the port to `0` is
useful for isolated tests, but requires reading the selected port from the
daemon startup log and is not suitable for a fixed Prometheus target.
