# Aqli Architecture & Module Boundaries

## Vision: A Pure Inference Runtime

Aqli is the **inference/serving engine runtime** built on top of Alkhawarizm. It is a
*pure runtime* — it provides no SPIs of its own in the long run. As SPI contracts mature,
they are elevated into Alkhawarizm so that **any** compliant engine can implement them.

## Ecosystem Layer Order

```
Alkhawarizm  (ML framework: tensors, backends, hardware, ALL serving SPIs)
     │  publishes as  tech.kayys.alkhawarizm:*
     │  hosts:        alkhawarizm-spi-inference  ← InferenceRequest/Response, Engine, Pipeline
     │                alkhawarizm-spi-plugin     ← Plugin lifecycle contracts
     │                alkhawarizm-spi-tokenizer  ← Tokenizer SPI
     │                alkhawarizm-spi-model      ← ModelConfig, ModelRunner, Multimodal
     │                alkhawarizm-tensor         ← Tensor, Shape, DType, ComputeBackend
     ▼
Aqli        (inference runtime: runners, repos, SDK, optimization plugins)
     │  publishes as  tech.kayys.aqli:*
     │  implements:   ModelRunner → GGUF, ONNX, LiteRT, Torch runners
     │                ModelRepository → HF, Kaggle, local, Panache DB
     │                AlkhawarizmPlugin → kv-cache, paged-attn, ...
     ▼
Wayang        (agent orchestration: RAG, MCP tools, providers, plugins)
```

> **SPI Elevation Rule**: When a Aqli SPI is general enough that *any* inference engine
> could implement it, it should be moved to Alkhawarizm. Aqli-specific orchestration
> details stay in Aqli.

---

## Directory Split: `framework/` vs `runtime/`

### `framework/` — Abstractions & Contracts

Contains **only** interfaces, SPIs, abstract base classes, canonical data types, and plugin-api definitions.

**Rules:**
- ✅ May contain: interfaces, abstract classes, enums, records, annotations, constants
- ✅ May depend on: other `framework/` modules, published `tech.kayys.alkhawarizm:*` artifacts
- ❌ Must NOT contain: concrete implementations, I/O, DB access, network calls, hardware-specific code
- ❌ Must NOT depend on: any `runtime/` module

| Gradle Path | Directory | Purpose |
|---|---|---|
| `:core:aqli-core` | `framework/core/aqli-core` | Canonical IR: GData, GTensor, GHandle, GScalar, AqliGraph |
| `:tokenizer:aqli-tokenizer-core` | `framework/core/aqli-tokenizer-core` | Tokenizer (SPI + impls) — SPI to be elevated to `alkhawarizm-spi-tokenizer` |
| `:spi:aqli-spi` | `framework/spi/aqli-spi` | Root SPI (node, plugin, tool, observability contracts) |
| `:spi:aqli-spi-inference` | `framework/spi/aqli-spi-inference` | InferenceRequest/Response — **↑ elevate to `alkhawarizm-spi-inference`** |
| `:spi:aqli-spi-runner` | `framework/spi/aqli-spi-runner` | ModelRunner + AbstractAqliRunner — **↑ elevate to `alkhawarizm-spi-model`** |
| `:spi:aqli-spi-multimodal` | `framework/spi/aqli-spi-multimodal` | Multimodal pipeline SPI |
| `:spi:aqli-spi-plugin` | `framework/spi/aqli-spi-plugin` | Plugin lifecycle SPI — **↑ elevate to `alkhawarizm-spi-plugin`** |
| `:spi:aqli-spi-runtime` | `framework/spi/aqli-spi-runtime` | Runtime management SPI |
| `:spi:aqli-spi-model` | `framework/spi/aqli-spi-model` | Model descriptor SPI |
| `:spi:aqli-spi-audio` | `framework/spi/aqli-spi-audio` | Audio modality SPI |
| `:spi:aqli-spi-image` | `framework/spi/aqli-spi-image` | Image modality SPI |
| `:sdk:aqli-sdk` | `framework/sdk/aqli-sdk` | Public SDK API surface |
| `:sdk:aqli-sdk-api` | `framework/sdk/aqli-sdk-api` | SDK API contracts |
| `:sdk:aqli-sdk-core` | `framework/sdk/aqli-sdk-core` | SDK core logic (no I/O) |
| `:sdk:aqli-sdk-protobuf` | `framework/sdk/aqli-sdk-protobuf` | Protobuf wire format |
| `:plugin:aqli-plugin-core` | `framework/plugin/aqli-plugin-core` | Plugin base API |
| `:plugin:aqli-plugin-kernel-core` | `framework/plugin/aqli-plugin-kernel-core` | Kernel plugin API |
| `:plugin:aqli-plugin-optimization-core` | `framework/plugin/aqli-plugin-optimization-core` | Optimization plugin API |
| `:plugin:aqli-plugin-runner-core` | `framework/plugin/aqli-plugin-runner-core` | Runner plugin API |
| `:observability:aqli-observability` | `framework/observability/aqli-observability` | Metrics/stats SPI |

---

### `runtime/` — Concrete Implementations

Contains all working code that touches the real world: file I/O, HTTP, databases, JNI/JNA, hardware backends, model downloads, KV caches.

**Rules:**
- ✅ May contain: concrete implementations of `framework/` SPIs, I/O, DB, network, JNI
- ✅ May depend on: `framework/` modules, other `runtime/` modules, `tech.kayys.alkhawarizm:*` artifacts
- ❌ Must NOT be depended on by: any `framework/` module

| Gradle Path | Directory | Purpose |
|---|---|---|
| `:runtime:aqli-runtime-core` | `runtime/core/aqli-runtime-core` | Scheduler, executor, KV cache |
| `:adapter:aqli-adapter` | `runtime/adapter/aqli-adapter` | Protocol adapters |
| `:repository:aqli-model-repo-common` | `runtime/repository/aqli-model-repo-common` | Shared repo utilities |
| `:repository:aqli-model-repo-local` | `runtime/repository/aqli-model-repo-local` | Local filesystem model store |
| `:repository:aqli-model-repo-hf` | `runtime/repository/aqli-model-repo-hf` | HuggingFace Hub client |
| `:repository:aqli-model-repo-kaggle` | `runtime/repository/aqli-model-repo-kaggle` | Kaggle dataset client |
| `:repository:aqli-model-database` | `runtime/repository/aqli-model-database` | Quarkus Panache DB persistence |
| `:sdk:aqli-sdk-local` | `runtime/sdk/aqli-sdk-local` | Local SDK provider (CDI beans) |
| `:optimization:aqli-plugin-kv-cache` | `runtime/optimization/aqli-plugin-kv-cache` | KV cache optimization plugin |
| `:runner:gguf:aqli-runner-gguf` | `runtime/runner/gguf/aqli-runner-gguf` | GGUF inference runner |
| `:runner:gguf:aqli-plugin-runner-gguf` | `runtime/runner/gguf/aqli-plugin-runner-gguf` | GGUF runner plugin registration |
| `:runner:onnx:aqli-runner-onnx` | `runtime/runner/onnx/aqli-runner-onnx` | ONNX inference runner |
| `:runner:litert:aqli-runner-litert` | `runtime/runner/litert/aqli-runner-litert` | LiteRT inference runner |
| `backend:*` | `runtime/backend/*` | CPU / CUDA / Metal / ROCm kernels |

---

## Alkhawarizm Boundary

Aqli must **never** declare classes in `package tech.kayys.alkhawarizm.*`. Any such class belongs in the `Families/Alkhawarizm/alkhawarizm` repository.

### Owned by Alkhawarizm (do not duplicate in Aqli)

| Type | Alkhawarizm Module |
|---|---|
| `DefaultTensor`, `Tensor`, `Shape`, `DType`, `DeviceType` | `alkhawarizm-tensor` |
| `ComputeBackend`, `CpuBuffer` | `alkhawarizm-tensor` |
| `MultimodalRequest`, `MultimodalResponse`, `MultimodalContent` | `alkhawarizm-spi-model` |
| `ModalityType`, `MultimodalCapability` | `alkhawarizm-spi-model` |
| `ModelConfig`, `ModelArchitecture`, `ModelRuntimeTraits` | `alkhawarizm-spi-model` |
| `ModelRegistry`, `ModelStatsProvider`, `ResourceMetrics` | `alkhawarizm-spi-model` |
| GGUF parsing utilities | `alkhawarizm-gguf-core`, `alkhawarizm-gguf-converter` |
| Safetensor loading | `alkhawarizm-safetensor-core`, `alkhawarizm-safetensor-spi` |

---

## Enforcement Checklist

Run these checks before merging any PR:

```bash
# 1. No framework module depends on a runtime module
grep -rn 'project(":' framework/ --include="*.kts" \
  | grep -v '//' \
  | grep -E 'runner:|repository:|optimization:|adapter:|runtime:'
# Expect: no output

# 2. No Aqli source declares an Alkhawarizm package
grep -rn '^package tech.kayys.alkhawarizm' --include="*.java" \
  framework/ runtime/
# Expect: no output

# 3. No stale old core:aqli-model-repo-* references
grep -rn 'core:aqli-model-repo' --include="*.kts" .
# Expect: no output

# 4. No shadow/duplicate data-layer imports in runtime
grep -rn 'tech.kayys.aqli.runtime.data' --include="*.java" runtime/
# Expect: no output
```

---

## Change Guide

| What you want to add | Where it goes |
|---|---|
| New inference contract / request-response type | `framework/spi/aqli-spi-inference` or `aqli-spi-multimodal` |
| New tensor or data primitive | `Families/Alkhawarizm/alkhawarizm` |
| New model download source (e.g. S3) | `runtime/repository/aqli-model-repo-s3` |
| New hardware runner (e.g. Vulkan) | `runtime/runner/vulkan/aqli-runner-vulkan` |
| New optimization plugin | `runtime/optimization/aqli-plugin-<name>` |
| New SDK convenience layer | `framework/sdk/aqli-sdk-<name>` (if pure abstraction) or `runtime/sdk/aqli-sdk-<name>` (if uses I/O) |
