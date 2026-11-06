# Canonical Inference Contracts and Runtime Ownership

**Status:** Proposed migration decision  
**Scope:** `andalus-ai/aljabr` and `andalus-ai/aqli`

## Decision

Aljabr and AQLI are peer projects in the Andalus AI ecosystem. Both must remain useful for research, AI/ML development, and production. Neither project is restricted to a single usage mode.

The ecosystem will have one canonical definition for each shared ML/inference concept. AQLI must consume Aljabr's canonical framework contracts rather than maintain parallel copies. AQLI may retain runtime-specific APIs, orchestration policies, and compatibility adapters where those represent genuinely different responsibilities.

This is a consolidation, not a blanket dependency inversion that moves all functionality into Aljabr.

## Ownership boundaries

| Capability | Canonical owner | Notes |
|---|---|---|
| Tensor types, tensor operations, numerical kernels | Aljabr | Backend-independent abstractions with Java CPU, Metal, CUDA, and other implementations |
| Model and weight representations | Aljabr | Shared framework-level model abstractions |
| GGUF and Safetensors parsing/loading/format metadata | Aljabr | Separate format-specific APIs and adapters |
| Tokenizer and model-level generation primitives | Aljabr | Reusable by research, development, and runtime consumers |
| Shared inference request, response, message, tool-definition, and stream-chunk data contracts | Aljabr | One canonical type per concept |
| Framework-level inference execution abstraction | Aljabr | Direct inference use without requiring AQLI deployment services |
| Model registry/resolution and runtime lifecycle | AQLI | Operational ownership and policy |
| Request routing, scheduling, batching, admission control | AQLI | Runtime orchestration |
| Generation sessions, runtime streaming orchestration | AQLI | Uses the canonical Aljabr chunk contract |
| Serving APIs, deployment, observability, operational health | AQLI | Runtime/application layer |
| Training/fine-tuning workflows | Tafkir where applicable; Aljabr primitives | Tafkir remains optional, not a required dependency for inference |

## Current duplication verified on the default branches

The following AQLI types duplicate corresponding Aljabr types:

- `tech.kayys.aqli.spi.inference.InferenceRequest` and `tech.kayys.aljabr.spi.inference.InferenceRequest`
- `tech.kayys.aqli.spi.inference.InferenceResponse` and `tech.kayys.aljabr.spi.inference.InferenceResponse`
- `tech.kayys.aqli.spi.inference.InferenceResponseInterface` and `tech.kayys.aljabr.spi.inference.InferenceResponseInterface`
- `tech.kayys.aqli.spi.inference.StreamingInferenceChunk` and `tech.kayys.aljabr.spi.inference.StreamingInferenceChunk`
- `tech.kayys.aqli.spi.inference.InferenceStage` and `tech.kayys.aljabr.spi.inference.InferenceStage`
- `tech.kayys.aqli.spi.inference.Attachment` and `tech.kayys.aljabr.spi.inference.Attachment`
- `tech.kayys.aqli.spi.Message` and `tech.kayys.aljabr.spi.Message`
- `tech.kayys.aqli.spi.tool.ToolDefinition` and `tech.kayys.aljabr.spi.tool.ToolDefinition`

The AQLI inference SPI already declares `aljabr-spi-inference` as an API dependency, but still defines its own parallel contracts. This is not a missing-dependency problem; it is an ownership and migration problem.

The request/response/message/tool/attachment/stage implementations are substantially equivalent, but package identity makes them incompatible Java types. They must not be deleted or replaced until AQLI consumers have been migrated and compatibility requirements are covered by tests.

## Important separation: execution contract versus runtime lifecycle

The current Aljabr and AQLI `InferenceEngine` interfaces also overlap. They both expose inference, streaming, embedding, asynchronous jobs, initialization, shutdown, health, and statistics.

The target must not simply move the entire AQLI runtime lifecycle into Aljabr. Instead:

1. Aljabr owns the reusable framework-level execution contract and canonical request/response/chunk types.
2. AQLI owns runtime lifecycle, model selection/resolution, scheduling, routing, operational health, deployment, and runtime statistics.
3. AQLI runtime services compose Aljabr execution engines/providers. They must not create another definition of the same inference DTOs.
4. Any compatibility facade must convert/delegate to canonical Aljabr contracts; it must not evolve as a second independent model.

The exact split of methods on the existing Aljabr `InferenceEngine` is a follow-up API design decision. Lifecycle methods should not be relocated mechanically without auditing direct Aljabr consumers.

## Format and compute dimensions

Model format and compute backend are independent axes:

- Formats: GGUF, Safetensors, ONNX, LiteRT, and future formats each have their own loaders/adapters.
- Compute: Java CPU, Metal, CUDA, and other compute providers implement reusable tensor/operation capabilities.

Do not introduce a combined cross-format backend such as `GgufSafetensorBackend`. Format adapters should feed common framework/runtime contracts; compute selection should remain independent.

## Migration sequence and safety gates

1. **Inventory consumers and API surface.** Enumerate direct imports, public signatures, serialization behavior, ServiceLoader providers, and downstream modules for each duplicate type.
2. **Canonicalize foundational value types.** Migrate AQLI's message, tool-definition, attachment, stage, request, response, response-interface, and stream-chunk consumers to Aljabr contracts. Add explicit converters only at external compatibility boundaries.
3. **Consolidate execution interfaces.** Separate reusable framework execution from AQLI runtime orchestration; migrate providers and callers without removing working behavior.
4. **Unify format paths.** GGUF and Safetensors must both execute through the shared contracts while preserving format-specific loading.
5. **Validate.** Run focused unit tests, module compilation, integration tests, serialization/compatibility tests, and CI. Validate hardware-specific Metal/FFM behavior on macOS only when the change touches those paths.

No duplicate public type is removed solely because an equivalent Aljabr type exists. Removal requires zero remaining internal consumers, a migration path for public consumers, and passing tests/CI.

## Non-goals

- Making AQLI a thin CLI-only wrapper.
- Restricting Aljabr to research or AQLI to production.
- Forcing Tafkir as a required dependency.
- Combining GGUF and Safetensors into one format-specific backend.
- Removing llama.cpp or LibTorch compatibility paths before the Java-native implementations meet correctness and performance gates.
