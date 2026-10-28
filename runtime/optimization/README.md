# Aqli Runtime Optimization

This directory contains **inference-runtime optimizations**. It is not a general-purpose
kernel library, and a module existing here does not imply that it is enabled, integrated
with generation, or production-ready.

## Ownership boundary

Use this rule when adding or moving code:

- **Aqli owns runtime policy and lifecycle:** request scheduling, continuous batching,
  KV-cache allocation and reclamation, prefix/prompt-cache policy, admission control,
  prefill/decode orchestration, weight-offload decisions, and inference-time adapter
  selection.
- **Aljabr owns reusable compute capabilities:** tensor operations, attention/kernel
  implementations, device-memory primitives, backend dispatch, and low-level data
  movement that can be used outside Aqli.
- **Keep the boundary one-way:** Aqli may call Aljabr APIs. Aljabr must not depend on
  Aqli runtime modules.
- **Split mixed modules:** keep scheduling/cache policy and request state in Aqli; put
  reusable math, kernels, and device primitives in Aljabr behind stable APIs.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the placement matrix and cleanup rules.

## Module status

Gradle inclusion is the source of truth for the default build. Most directories below
are intentionally **not included in the default Gradle build**. Treat them as
experimental/unintegrated until they are included deliberately, compile, pass tests,
and are exercised by a real inference path.

| Module | Intended responsibility | Default build status |
|---|---|---|
| `aqli-plugin-kv-cache` | Runtime KV-cache ownership, block allocation, request lifecycle | Included; integration with generation still needs verification |
| `aqli-plugin-paged-attention` | Aqli-side paged-attention integration and dispatch | Not included |
| `aqli-plugin-prompt-cache` | Prefix/prompt reuse policy and cache-store lifecycle | Not included |
| `aqli-plugin-wait-scheduler` | Admission, waiting queues, and continuous batching policy | Not included |
| `aqli-plugin-prefill-decode` | Runtime orchestration for split prefill/decode deployments | Not included |
| `aqli-plugin-perfmode` | Runtime performance-policy configuration | Not included |
| `aqli-plugin-weight-offload` | Runtime weight-offload policy | Not included |
| `aqli-plugin-elastic-ep` | Runtime expert-parallel coordination | Not included |
| `aqli-plugin-evicpress` | KV-cache eviction/compression policy | Not included |
| `aqli-plugin-fa3`, `aqli-plugin-fa4`, `aqli-plugin-hybrid-attn` | Candidate attention integrations | Not included; reusable kernels belong in Aljabr |
| `aqli-plugin-qlora` | Inference-time adapter integration | Not included; quantized math belongs in Aljabr |

“Not included” is not a statement that a module is useless. It means it must not be
advertised as part of the default product until its build, tests, dependencies, and
runtime integration are demonstrated.

## Development rules

1. Do not add placeholder modules or duplicate SPI/configuration/exception types to make
   a module look complete.
2. Before enabling a module, verify its dependency graph, compile it, run its tests, and
   prove that the intended runtime path calls it.
3. Keep optional GPU/native integrations out of the default JVM build unless the
   dependency is genuinely optional and has a tested fallback.
4. Do not publish speedup numbers without reproducible benchmark results. Report hardware,
   model, workload, baseline, warm-up, concurrency, and measurement method.
5. Prefer one canonical implementation per responsibility. Extract shared contracts to
   the appropriate SPI; do not copy classes between plugins.
6. Remove a module only after checking source references, Gradle dependencies, service
   registrations, configuration keys, documentation links, and downstream consumers.

## Build and verification

Run commands from the repository root. First inspect the available project path:

```bash
./gradlew projects
```

The KV-cache module is currently included under `:optimization:aqli-plugin-kv-cache`.
Use the exact project path reported by Gradle for targeted checks, for example:

```bash
./gradlew :optimization:aqli-plugin-kv-cache:test
```

A successful module test does not prove end-to-end generation integration. Verify that
separately with an inference-level test or benchmark before describing the feature as
usable in production.
