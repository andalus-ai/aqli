# Aljabr–AQLI Module Ownership Matrix

**Baseline:** 2026-10-28  
**Status:** Target placement rules plus known audit candidates. This matrix is not a statement that all listed modules have already been migrated.

## 1. Ownership at a glance

| Capability | Canonical owner | AQLI may retain | Migration acceptance evidence |
|---|---|---|---|
| Tensor, shape, dtype, storage, device placement | Aljabr | Request-scoped references/adapters | Numerical, lifetime and concurrency tests |
| Generic operators and kernels | Aljabr | Scheduling and operator selection policy | Operator parity and backend conformance |
| Compute backend SPI and backend implementations | Aljabr | Provider selection/config adapter | Capability tests on each supported backend |
| FFM declarations, ABI layouts, low-level native handles | Aljabr | High-level runner lifecycle adapter | ABI/symbol/native smoke tests on supported platforms |
| GGUF parser, metadata, tensor access | Aljabr | Model selection/download and serving policy | Malformed-file and tensor parity tests |
| llama.cpp/ggml generated FFM bindings | Aljabr | No duplicate generated bindings | Duplicate-package check + compile/native smoke |
| Safetensors parser/validation/weight loading | Aljabr | Model-serving orchestration | Header/offset/shard/dtype/lifetime tests |
| Safetensors model forward path | Aljabr when reusable; migration is class-by-class | Serving loop and temporary adapter | End-to-end token parity, cache and backend tests |
| ONNX/LiteRT runtime adapter | Aljabr integration module, optional | Provider selection and serving errors | Operator/capability matrix + conformance fixtures |
| Generic tokenizer API/algorithms | Aljabr | Prompt assembly and serving protocol | Golden encode/decode tests |
| Chat template selection and request formatting | Boundary contract; reusable templates in Aljabr, request policy in AQLI | Template resolution/override policy | Golden prompt fixtures and special-token tests |
| Generic model topology/configuration | Aljabr | Model registry and deployment metadata | Model family fixtures and trait-based behavior |
| Generic inference execution contract | Aljabr if usable outside serving | AQLI serving request/response facade | API compatibility and no AQLI dependency from Aljabr |
| Sampling/stopping policy | AQLI | Canonical implementation | Seeded tests, stop-token and penalty fixtures |
| KV storage/layout primitives | Aljabr | — | Numerical/state parity and explicit resource ownership |
| KV reuse, admission, eviction and tenant/session policy | AQLI | Canonical implementation | Isolation, concurrency, cancellation and eviction tests |
| Model registry, downloads and deployment cache policy | AQLI | Canonical implementation | Digest, revision, concurrency and safe-path tests |
| CLI, HTTP/gRPC, OpenAI-compatible APIs | AQLI | Canonical implementation | API contract/integration tests |
| Authentication, quotas, tenant policy and audit | AQLI / application integration | Canonical serving policy | Security and isolation tests |
| Metrics, tracing, health/admin and lifecycle | AQLI | Canonical serving implementation | Telemetry assertions and shutdown tests |
| Training loops, optimizers and fine-tuning | Tafkir, using Aljabr | Optional integration only | Default AQLI build excludes training-only dependencies |
| Offline quantization/adaptation | Optional Aljabr/Tafkir integration, exposed through a stable port | AQLI may request a strategy through an optional contract | Missing optional provider does not break NONE/default inference |
| Agent orchestration, tools, RAG workflow, enterprise workflow | Andalus/Wayang/application layer | AQLI client integration | Contract tests without reverse dependency |

## 2. Known audit candidates

These paths are starting points for review, not automatic deletion instructions.

| Concern | AQLI candidate | Aljabr candidate | Required next action |
|---|---|---|---|
| Generated llama.cpp FFM | `runtime/runner/gguf/aqli-runner-gguf` generated `tech.kayys.aqli.llama` sources | `core/aljabr-gguf-llamacpp` | PR #25 attempted ownership migration; validate current main and required CI before declaring complete |
| Handwritten GGUF runner | `runtime/runner/gguf/aqli-runner-gguf` | `core/aljabr-gguf-llamacpp` and GGUF backend modules | Compare load/context/sampler/batch/state APIs; keep AQLI adapter only after parity |
| Tokenizer contracts | `framework/core/aqli-tokenizer-core` | `core/aljabr-spi-tokenizer` | Compare public interfaces and BPE, WordPiece, SentencePiece, template and pooling behavior |
| Safetensors loading | `runtime/runner/safetensor/aqli-runner-safetensor`, `aqli-safetensor-engine` | `core/aljabr-safetensor-core`, `loader`, `spi`, `bridge` | Remove format-level duplication only; do not delete model forward/decode behavior based on loader existence |
| Tensor and IR | `framework/core/aqli-core` | `core/aljabr-tensor`, `core/aljabr-core` | Classify framework-generic tensor APIs vs runtime/compiler-specific IR |
| Inference contracts | `framework/spi/aqli-spi-inference` | `core/aljabr-spi-inference` | Compare request/result types, cancellation, streaming and ownership before migration |
| KV cache | runtime core and optimization plugins | tensor/memory and GGUF modules | Separate storage/layout primitives from runtime policy and verify concurrency/lifetime |
| REST/RAG reverse leak | — | `core/aljabr-safetensor-api` includes HTTP resources/RAG-related classes per initial audit | Inventory consumers; move server-specific endpoints to AQLI without breaking reusable APIs |
| Quantization optionality | Safetensors engine and optional integration | `aljabr-safetensor-quantization`, bridge and Tafkir integration | Keep AQLI-owned port; verify NONE/default path does not require optional implementation |
| Legacy coordinates | References to `tech.kayys.alkhawarizm` | Historical Aljabr module/package names | Search source, Gradle metadata, docs, publishing coordinates and compatibility aliases |

## 3. Allowed dependency rules

1. `aqli-*` may depend on published `tech.kayys.aljabr:*` public artifacts.
2. Aljabr modules may not import `tech.kayys.aqli.*` or depend on AQLI server/runtime modules.
3. AQLI default runtime modules may not require Tafkir or optional quantization artifacts to compile or start.
4. Optional integrations must be isolated in named modules or runtime providers with explicit missing-provider behavior.
5. Avoid cycles between parser, loader, tensor core and integration modules. Keep format core independent from serving and optional quantization.
6. Native generated sources and ABI packages must have exactly one canonical owner.
7. No silent package relocation: preserve compatibility through an adapter or documented breaking release.
8. Gradle composite builds and published artifact resolution must both be tested.
9. If a module is marked optional, its transitive dependencies must not leak into the default runtime classpath.
10. A dependency exception must name the consumer, rationale, scope and removal condition.

## 4. Consolidation checklist for each candidate

- [ ] Record current public types, methods, constructors and callers.
- [ ] Identify behavioral differences and unsupported edge cases.
- [ ] Document object ownership, native/mapped memory lifetime, thread safety and cancellation.
- [ ] Add focused unit tests and fixtures before changing call sites.
- [ ] Add an adapter if it enables a bounded migration without cycles.
- [ ] Update Gradle dependency and publication metadata.
- [ ] Run focused module compile/tests on JDK 25.
- [ ] Run relevant repository CI and inspect every failed job.
- [ ] Verify composite-build dependency substitution.
- [ ] Verify published-artifact resolution from a clean consumer.
- [ ] Benchmark performance-sensitive execution using the same model, prompt, settings and hardware.
- [ ] Update API docs, architecture diagrams, README links and migration notes.
- [ ] Remove old implementation only after parity is demonstrated.
- [ ] Mark status accurately: planned, in progress, blocked, or verified.

## 5. Status vocabulary

- **Planned:** ownership decision exists; implementation has not started.
- **In progress:** code or tests have changed; acceptance evidence is incomplete.
- **Blocked:** a concrete build/API/test/dependency issue prevents progress; include a link to the failure.
- **Verified:** required tests and CI passed, dependency resolution is proven, and the old path is removed or explicitly justified.
- **Deferred:** intentional future work with reason and re-entry criteria.

Do not mark a phase verified because a PR was created, a file was deleted, or a single local compile succeeded.
