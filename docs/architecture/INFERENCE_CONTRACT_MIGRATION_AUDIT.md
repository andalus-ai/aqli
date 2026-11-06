# Inference Contract Migration Audit

Status: source-level audit in progress  
Related decision: [Canonical Inference Contract Ownership](./CANONICAL_INFERENCE_CONTRACTS.md)

## Confirmed duplicate types

The following types exist in both repositories under distinct Java package names:

| Concept | AQLI | Aljabr | Finding |
|---|---|---|---|
| Conversation message | `tech.kayys.aqli.spi.Message` | `tech.kayys.aljabr.spi.Message` | Inspected implementations are effectively identical |
| Tool definition | `tech.kayys.aqli.spi.tool.ToolDefinition` | `tech.kayys.aljabr.spi.tool.ToolDefinition` | Inspected implementations are effectively identical |
| Inference request | `tech.kayys.aqli.spi.inference.InferenceRequest` | `tech.kayys.aljabr.spi.inference.InferenceRequest` | Similar structure, but coupled to project-local message, tool, context, and stage types |
| Inference response | `tech.kayys.aqli.spi.inference.InferenceResponse` | `tech.kayys.aljabr.spi.inference.InferenceResponse` | Distinct public Java identities; full behavior and consumer comparison remains required |
| Streaming chunk | `tech.kayys.aqli.spi.inference.StreamingInferenceChunk` | `tech.kayys.aljabr.spi.inference.StreamingInferenceChunk` | Very similar inspected record shape; package and modality dependencies differ |

Other identified duplicates requiring the same treatment include `Attachment`, `InferenceStage`, and `InferenceResponseInterface`.

## Dependency boundary issue

AQLI's `framework/spi/aqli-spi-inference/build.gradle.kts` declares Aljabr `aljabr-spi-inference` as an `api` dependency, but declares AQLI `:spi:aqli-spi` as `implementation`.

AQLI's public `InferenceRequest` constructor and accessors expose AQLI `Message`, `ToolDefinition`, and `RequestContext` types. This is a public API dependency leak: downstream consumers may need types that are not exported transitively.

Do not blindly change the dependency scope. First determine which AQLI-owned public types remain intentionally runtime-specific and which should become canonical Aljabr types.

## Migration implications

1. **Message and ToolDefinition are the strongest initial candidates.** Their inspected implementations are effectively identical. Before migration, enumerate source, test, serialization, SDK, and downstream consumers in both repositories.
2. **InferenceRequest cannot be migrated by changing imports alone.** Its public constructor, getters, builder methods, and nested dependencies must resolve to canonical Aljabr types. AQLI request context data must be mapped without losing API key, user, session, or trace information.
3. **Response compatibility needs explicit treatment.** Compare finish-reason enums, nested tool-call records, JSON property names, null/default behavior, and builders.
4. **Streaming compatibility needs explicit treatment.** Preserve modality, chunk index/order, finish semantics, usage, timestamps, metadata, and tool-call deltas.
5. **Retain compatibility facades where public consumers need a migration window.** They must delegate or convert to canonical Aljabr contracts, not evolve as an independent implementation.
6. **Do not delete public AQLI classes** until internal and downstream consumers, serialization behavior, and compatibility tests demonstrate a safe migration.

## Ownership guidance

- Aljabr owns reusable message/tool/inference value contracts and framework-level execution abstractions.
- AQLI owns runtime lifecycle, model resolution, scheduling, routing, serving, deployment, and operational state.
- AQLI-specific runtime context and orchestration data remain AQLI-owned unless demonstrated to be generic framework concepts.
- GGUF and Safetensors remain independent format paths; CPU, Metal, and CUDA remain independent compute paths.

## Audit scope and next gate

This document records a source comparison of inspected public type definitions and the AQLI inference SPI Gradle dependency declaration. It is not yet a complete repository-wide consumer inventory or downstream binary/source compatibility assessment.

The next implementation gate is a complete import/usage inventory across AQLI modules, tests, SDKs, and Aljabr modules. Select the first type migration only after that inventory. No public class is removed by this audit.

## Foundational contract parity: expanded source inspection

A direct comparison of the four foundational types below shows that each AQLI implementation is byte-for-byte equivalent to its Aljabr counterpart after normalizing the package prefix from `tech.kayys.aqli` to `tech.kayys.aljabr`:

| Concept | AQLI source module | Aljabr source module | Result |
|---|---|---|---|
| `Message` | `:spi:aqli-spi` | `:core:aljabr-spi-inference` | Identical after package normalization |
| `RequestContext` | `:spi:aqli-spi` | `:core:aljabr-spi-inference` | Identical after package normalization |
| `ToolCall` | `:spi:aqli-spi` | `:core:aljabr-spi-inference` | Identical after package normalization |
| `ToolDefinition` | `:spi:aqli-spi` | `:core:aljabr-spi-inference` | Identical after package normalization |

This expands the initial comparison: `ToolCall` and `RequestContext` are also exact duplicates, not merely similar types. `Message` directly depends on the project-local `ToolCall`, so those two must be migrated as a unit or through an explicit compatibility conversion. `ToolCall` itself refers to the project-local `ToolDefinition.Type`, making the dependency group a three-type cluster: `Message`, `ToolCall`, and `ToolDefinition`.

## Module placement and dependency-boundary findings

- AQLI places `Message`, `RequestContext`, `ToolCall`, and `ToolDefinition` in the lower-level `:spi:aqli-spi` module. Their Aljabr counterparts are in `:core:aljabr-spi-inference`. Consolidation therefore changes not only imports but also the module dependency graph for every consumer of `:spi:aqli-spi`.
- AQLI `:spi:aqli-spi` declares `tech.kayys.aljabr:aljabr-tensor` as an `implementation` dependency, while its public `RequestContext.preferredDevice()` signature exposes `tech.kayys.aljabr.core.tensor.DeviceType`. This is another public API dependency leak. More importantly, it couples a generic request-context SPI to a concrete tensor/core type. The target design should decide whether device preference belongs in a framework-neutral execution hint or should be represented by a stable identifier; simply changing the dependency to `api` would expose the coupling rather than resolve it.
- Aljabr's `:core:aljabr-spi-inference` intentionally has API dependencies on tensor and model SPI modules. AQLI's base SPI currently reaches the tensor module directly, which should be reconsidered as the canonical shared context is migrated.

## Migration sequencing update

The foundational group should be migrated in dependency order, not as isolated class replacements:

1. Decide the canonical shape of `RequestContext` and remove its direct dependency on concrete `DeviceType` if the abstraction can use a stable device identifier.
2. Canonicalize `ToolDefinition` and `ToolCall` together because `ToolCall` uses `ToolDefinition.Type`.
3. Canonicalize `Message` after its `ToolCall` dependency is resolved.
4. Inventory and migrate all consumers of the AQLI base SPI module before removing any duplicate classes or changing module ownership.
5. Add API/serialization compatibility tests for old constructors, Jackson payloads, null/default semantics, and public method signatures before deleting or deprecating compatibility types.

The parity comparison proves implementation duplication; it does **not** prove that a package/module migration is source- or binary-compatible. The full repository-wide consumer inventory remains outstanding.
