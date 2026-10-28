# Aljabr–AQLI Ownership Audit and Consolidation Plan

**Audit baseline:** 2026-10-28  
**Repositories:** `andalus-ai/aljabr` and `andalus-ai/aqli`  
**Status:** Initial source-tree audit; migration is incremental and CI-gated.

## Target architecture

- **Aljabr** is the reusable ML library/framework: tensor and memory abstractions, compute kernels/backends, model-format readers/writers, tokenizer contracts and reusable implementations, model-family/weight-loading capabilities, generic inference contracts, and native FFM integrations.
- **AQLI** is the inference/serving runtime: deployment and API endpoints, model repositories/downloads, request admission and scheduling, tenant/session policy, streaming and response adaptation, service lifecycle, observability, runtime plugin discovery, and integration adapters over Aljabr.
- **Tafkir** owns training/adaptation workflows and optional training-oriented quantization. AQLI must not acquire a mandatory Tafkir dependency for ordinary inference.

The boundary is about responsibility, not simply package names. AQLI-specific policies can use Aljabr primitives without moving serving policy into Aljabr.

## Initial inventory

The source-tree snapshot contained approximately:

- AQLI: 1,474 Java main-source files across framework, runtime, UI, and stubs.
- Aljabr: 1,019 Java main-source files.
- 227 Java class basenames appeared in both trees. This is a **candidate-overlap count**, not a claim that all 227 are duplicates; every candidate must be classified by package, behavior, dependency direction, and tests.

### High-confidence consolidation candidates

| Concern | AQLI location | Existing Aljabr location | Target |
|---|---|---|---|
| Generated llama.cpp/ggml FFM bindings | `runtime/runner/gguf/aqli-runner-gguf/.../tech/kayys/aqli/llama` | `core/aljabr-gguf-llamacpp/.../tech/kayys/aljabr/llama` | Aljabr owns generated ABI bindings. The 79 AQLI generated files are removed by the associated migration PR. |
| Handwritten llama.cpp native binding and execution collaborators | `runtime/runner/gguf/aqli-runner-gguf/.../inference/llamacpp` | `core/aljabr-gguf-llamacpp/.../gguf/llamacpp` | Consolidate into Aljabr; retain only an AQLI adapter for serving-specific configuration, lifecycle, metrics, and request types. |
| Tokenizer contract and overlapping BPE code | `framework/core/aqli-tokenizer-core` | `core/aljabr-spi-tokenizer` | Aljabr owns the canonical contract and reusable tokenizer implementations. Preserve AQLI-only capabilities until moved or replaced. |
| Safetensors loader/config/parser/validation | `runtime/runner/safetensor/aqli-runner-safetensor`, `aqli-safetensor-engine`, and LibTorch utility code | `core/aljabr-safetensor-loader`, `core/aljabr-safetensor-core`, `core/aljabr-safetensor-spi` | Aljabr owns format-level loading and validation; AQLI owns serving/model-execution policy. |
| Tensor/memory primitives | `framework/core/aqli-core` | `core/aljabr-tensor` and `core/aljabr-core` | Prefer Aljabr's canonical tensor and memory abstractions; retain AQLI IR only where it is genuinely runtime/compiler-specific. |
| Inference SPI types | `framework/spi/aqli-spi-inference` | `core/aljabr-spi-inference` | Elevate generic engine/request/response contracts to Aljabr and migrate consumers incrementally. |
| KV cache | `runtime/core/aqli-runtime-core`, optimization plugins | `core/aljabr-core`, GGUF core | Split reusable memory/layout primitives from serving-time cache policy, scheduling, admission, eviction, and tenant/session coordination. Same class name alone is not evidence that implementations are interchangeable. |

## Reverse boundary leaks to review

The dependency direction must be audited both ways. Aljabr's `core/aljabr-safetensor-api` contains Quarkus/JAX-RS resources such as `OpenAiCompatibleResource`, `EmbeddingsResource`, `ModelRouterResource`, `RagResource`, and a `RagPipeline`. AQLI already has API resources under `ui/aqli-api`.

HTTP serving endpoints, deployment policy, and runtime-specific RAG orchestration should generally live in AQLI. Before moving any Aljabr resource, inspect consumers, service wiring, tests, and release compatibility. Keep reusable inference contracts and tensor/model APIs in Aljabr; do not move a module merely because its name contains `api`.

## Preserve AQLI-owned behavior

Do not mass-delete AQLI classes just because their names overlap. The following are runtime responsibilities unless evidence proves otherwise:

- HTTP/OpenAI-compatible API endpoints, CLI, SDK serving facade, auth/security filters, background jobs, health/admin endpoints.
- Model repositories, download/cache policy, deployment configuration, request routing and scheduling.
- Streaming adaptation, request metrics/tracing, tenant/session lifecycle and operational controls.
- Runtime plugin registration and thin adapters that map AQLI contracts/configuration to Aljabr APIs.

The large Safetensors engine needs a class-by-class audit. It contains model forward paths, attention/FFN execution, KV-cache interactions, hardware-specific routes, and runtime orchestration; the existing Aljabr loader does not prove that every such class has an equivalent replacement.

## Migration order

1. **GGUF generated bindings:** remove AQLI's generated binding copy and use Aljabr's generated API. Validate JVM and native-smoke CI.
2. **GGUF backend integration:** replace AQLI's duplicated FFM loader, ABI layouts, batch/sampler wrappers, and native execution with Aljabr's public backend API. Keep a thin AQLI adapter; add parity tests for load/infer/stream/unload and native-unavailable behavior.
3. **Safetensors format boundary:** add/use Aljabr's loader dependency, replace duplicated header parsing and file/tensor loading, and verify strict validation, mmap/copy, sharding, dtype conversion, and error mapping.
4. **Tokenizer consolidation:** migrate generic contracts and shared BPE code to Aljabr; separately account for SentencePiece, WordPiece, pooling, native discovery, and chat-template features not yet provided there.
5. **Tensor/IR/compute boundary:** compare duplicate buffers, tensor wrappers, graph/IR types, kernels, and model-family implementations. Move reusable compute/model code to Aljabr; leave serving policy in AQLI.
6. **Reverse leaks and dependency hygiene:** review Aljabr REST resources, optional quantization/Tafkir edges, duplicate Gradle dependencies, composite-build substitutions, and legacy coordinates.
7. **Enforcement:** add CI architecture checks for duplicate generated bindings, AQLI declarations of Aljabr-owned packages, forbidden dependency directions, and accidental mandatory training dependencies.

## Validation requirements

- Do not claim a consolidation is complete based on file deletion alone.
- Verify public API compatibility and lifecycle semantics, then run focused module tests, full relevant Gradle tasks, and repository CI.
- Check both the composite-build path and the published-artifact path so that local development does not hide dependency-resolution failures.
- Keep each PR bounded. A failed CI run is a migration finding to resolve, not a reason to reintroduce duplicate implementation code.
