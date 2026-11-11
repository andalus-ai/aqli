# Inference Contract Migration Audit

Status: source-level audit in progress  
Related decision: [Canonical Inference Contract Ownership](./CANONICAL_INFERENCE_CONTRACTS.md)

## Confirmed duplicate types

The following types exist in both repositories under distinct Java package names:

| Concept | AQLI | Aljabr | Finding |
|---|---|---|---|
| Conversation message | `tech.kayys.aqli.spi.Message` | `tech.kayys.aljabr.spi.Message` | Inspected implementations are effectively identical |
| Tool definition | `tech.kayys.aqli.spi.tool.ToolDefinition` | `tech.kayys.aljabr.spi.tool.ToolDefinition` | Inspected implementations are effectively identical |
| Inference request | `tech.kayys.aqli.spi.inference.InferenceRequest` | `tech.kayys.aljabr.spi.inference.InferenceRequest` | Byte-for-byte identical after package normalization; both expose the same FFM `MemorySegment` API |
| Inference response | `tech.kayys.aqli.spi.inference.InferenceResponse` | `tech.kayys.aljabr.spi.inference.InferenceResponse` | Byte-for-byte identical after package normalization, including nested finish-reason and tool-call types |
| Streaming chunk | `tech.kayys.aqli.spi.inference.StreamingInferenceChunk` | `tech.kayys.aljabr.spi.inference.StreamingInferenceChunk` | Byte-for-byte identical after package normalization, including modality, usage, timestamp, and tool-call delta fields |
| Response interface | `tech.kayys.aqli.spi.inference.InferenceResponseInterface` | `tech.kayys.aljabr.spi.inference.InferenceResponseInterface` | Byte-for-byte identical after package normalization |

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
| `Message` | `:spi:aqli-spi` | `:spi:aljabr-spi-inference` | Identical after package normalization |
| `RequestContext` | `:spi:aqli-spi` | `:spi:aljabr-spi-inference` | Identical after package normalization |
| `ToolCall` | `:spi:aqli-spi` | `:spi:aljabr-spi-inference` | Identical after package normalization |
| `ToolDefinition` | `:spi:aqli-spi` | `:spi:aljabr-spi-inference` | Identical after package normalization |

This expands the initial comparison: `ToolCall` and `RequestContext` are also exact duplicates, not merely similar types. `Message` directly depends on the project-local `ToolCall`, so those two must be migrated as a unit or through an explicit compatibility conversion. `ToolCall` itself refers to the project-local `ToolDefinition.Type`, making the dependency group a three-type cluster: `Message`, `ToolCall`, and `ToolDefinition`.

## Module placement and dependency-boundary findings

- AQLI places `Message`, `RequestContext`, `ToolCall`, and `ToolDefinition` in the lower-level `:spi:aqli-spi` module. Their Aljabr counterparts are in `:spi:aljabr-spi-inference`. Consolidation therefore changes not only imports but also the module dependency graph for every consumer of `:spi:aqli-spi`.
- AQLI `:spi:aqli-spi` declares `tech.kayys.aljabr:aljabr-tensor` as an `implementation` dependency, while its public `RequestContext.preferredDevice()` signature exposes `tech.kayys.aljabr.core.tensor.DeviceType`. This is another public API dependency leak. More importantly, it couples a generic request-context SPI to a concrete tensor/core type. The target design should decide whether device preference belongs in a framework-neutral execution hint or should be represented by a stable identifier; simply changing the dependency to `api` would expose the coupling rather than resolve it.
- Aljabr's `:spi:aljabr-spi-inference` intentionally has API dependencies on tensor and model SPI modules. AQLI's base SPI currently reaches the tensor module directly, which should be reconsidered as the canonical shared context is migrated.

## Migration sequencing update

The foundational group should be migrated in dependency order, not as isolated class replacements:

1. Decide the canonical shape of `RequestContext` and remove its direct dependency on concrete `DeviceType` if the abstraction can use a stable device identifier.
2. Canonicalize `ToolDefinition` and `ToolCall` together because `ToolCall` uses `ToolDefinition.Type`.
3. Canonicalize `Message` after its `ToolCall` dependency is resolved.
4. Inventory and migrate all consumers of the AQLI base SPI module before removing any duplicate classes or changing module ownership.
5. Add API/serialization compatibility tests for old constructors, Jackson payloads, null/default semantics, and public method signatures before deleting or deprecating compatibility types.

The parity comparison proves implementation duplication; it does **not** prove that a package/module migration is source- or binary-compatible. The full repository-wide consumer inventory remains outstanding.


## Execution-boundary findings: native memory and device types

Further inspection of both canonical and compatibility request/context contracts found two low-level concerns that must be addressed before the DTO migration.

### Native memory in the public inference request

Both AQLI and Aljabr `InferenceRequest` expose `Optional<MemorySegment> getNativeContextSegment()`. The backing field is transient and Jackson-ignored, but the public method still places the JDK Foreign Function & Memory API type in the shared inference request surface.

This is not ordinary request data: a `MemorySegment` is process-local native-memory state with lifetime and scope constraints. It should not be serialized, transported, retained as a generic request property, or required by consumers that only need to submit an inference request. The shared request DTO should carry portable inference intent and metadata; native execution state should live in an execution-scoped context/handle owned by the selected backend or runtime adapter. Preserve any existing internal use through an explicit adapter during migration rather than deleting the accessor before its consumers are inventoried.

### Device preference in RequestContext

Both AQLI and Aljabr `RequestContext` expose a portable `Optional<String> getPreferredDevice()` and additionally expose `Optional<DeviceType> preferredDevice()`, coupling the context API to `aljabr-tensor`. The string form already provides the stable, extensible representation needed for routing hints. The enum-returning helper also parses through `DeviceType.fromId`, so an unrecognized future provider identifier can throw instead of remaining an opaque hint.

The canonical contract should retain a stable device/provider identifier (or a small framework-neutral execution hint) as the primary API. If enum compatibility is needed, keep it as a deprecated adapter in the relevant compatibility layer, not as a foundational context dependency.

### Updated migration gate

Before consolidating `InferenceRequest` and `RequestContext`:

1. Inventory all callers of `getNativeContextSegment()` and `preferredDevice()` in both repositories.
2. Move native-memory state to an execution-scoped abstraction with explicit lifetime ownership; keep the portable request DTO independent of FFM.
3. Make device preference extensible and backend-neutral; retain conversion helpers only at compatibility boundaries.
4. Add tests for unknown device identifiers, FFM scope/lifetime behavior, JSON serialization, and source/binary compatibility.
5. Only then migrate the AQLI compatibility surface to the canonical Aljabr contract.

These are design and compatibility findings, not authorization to remove either public method immediately.


## Inference DTO parity: direct normalized source comparison

A direct source comparison was completed for the following four public inference DTO contracts in the current AQLI and Aljabr default branches. Each pair was identical line-for-line after replacing the package prefix `tech.kayys.aqli` / `tech.kayys.aljabr` with one neutral package token:

| Contract | AQLI path | Aljabr path | Result |
|---|---|---|---|
| `InferenceRequest` | `:framework/spi/aqli-spi-inference` | `:core/aljabr-spi-inference` | Identical after package normalization |
| `InferenceResponse` | `:framework/spi/aqli-spi-inference` | `:core/aljabr-spi-inference` | Identical after package normalization |
| `StreamingInferenceChunk` | `:framework/spi/aqli-spi-inference` | `:core/aljabr-spi-inference` | Identical after package normalization |
| `InferenceResponseInterface` | `:framework/spi/aqli-spi-inference` | `:core/aljabr-spi-inference` | Identical after package normalization |

This establishes implementation duplication, not API interchangeability: Java treats the package-distinct types as unrelated, and their public signatures reference package-distinct `Message`, `ToolDefinition`, `RequestContext`, `InferenceStage`, and `Attachment` contracts. AQLI already exports the Aljabr inference SPI as an API dependency, but its public DTOs remain AQLI-owned classes.

### Migration implications

1. Add explicit bidirectional adapters at the AQLI inference SPI boundary. Request conversion must preserve request ID, API key, model, messages, tools, tool choice, parameters, streaming flag, preferred provider, plugin, timeout, priority, cache bypass, user/session/trace context, stage, prompt-token count, metadata, and attachments.
2. Response conversion must preserve all token counts, duration, timestamp, metadata, tool calls, finish reason, and session ID. Do not collapse the richer response into the smaller `InferenceResponseInterface`.
3. Streaming conversion must preserve index/order, modality, text/image delta, finished state, finish reason, usage, emitted timestamp, metadata, and tool-call start/delta/end fields.
4. Keep the AQLI public DTOs during the compatibility window. Do not delete or deprecate them until consumers, serialization contracts, and source/binary compatibility have been verified.
5. Do not copy `MemorySegment` from a request DTO into a portable canonical request. It is execution-scoped native state and needs a separate lifetime-owned context/handle. Until callers are inventoried and migrated, the adapter must make any omission explicit rather than silently claiming lossless conversion.

### Validation still outstanding

- Repository-wide import and construction-site inventory for the four DTOs, including tests, SDKs, and downstream consumers.
- Adapter tests for null/default behavior, enum mapping, multimodal attachments, Jackson compatibility, and round-trip preservation.
- Explicit native-context consumer inventory and execution-scope/lifetime tests.
- CI/build validation of the adapter branch.

The source comparison is complete for these four definitions; consumer inventory and runtime behavior equivalence are not yet complete.


## Generic inference engine interface ownership

A direct source comparison of the AQLI and Aljabr `InferenceEngine` interfaces and their `LocalInferenceEngine` defaults shows the same public method surface after package normalization. This is a second duplicate contract family beyond the request/response DTOs.

The duplicated `InferenceEngine` surface currently combines two categories that should not be migrated as one indivisible API:

| Capability | Long-term owner | Reason |
|---|---|---|
| Execute a framework inference request and produce a framework response | Aljabr | Reusable model execution contract |
| Stream framework inference chunks | Aljabr | Reusable execution/stream contract |
| Execute embeddings through framework embedding contracts | Aljabr | Framework capability, independent of serving lifecycle |
| Resolve a model ID / select a loaded runtime model | AQLI | Runtime model registry and selection |
| Submit/manage asynchronous jobs | AQLI | Job lifecycle, persistence, polling, cancellation and queueing |
| Initialize/shutdown a deployed runtime | AQLI | Application/runtime lifecycle, not a model math primitive |
| Runtime health, serving readiness and operational statistics | AQLI | Deployment and operational concerns |
| Backend/provider-specific execution health | Provider SPI / Aljabr backend capability | Capability health should describe the execution provider, not the whole serving deployment |

### Migration recommendation

Do not move the entire AQLI `InferenceEngine` interface into Aljabr or delete its lifecycle methods in one change. Define a smaller Aljabr execution contract (or a clearly scoped existing execution interface) around portable request/response/stream/embedding operations. Keep an AQLI runtime service facade responsible for model selection, job orchestration, lifecycle and operational health, delegating execution to Aljabr.

The current `LocalInferenceEngine` defaults also duplicate across the two projects. They should be consolidated only after the target Aljabr execution contract is agreed and direct implementations/consumers are inventoried; default implementations that return `null` stats or unconditional healthy status must not be treated as a reliable runtime-health model.

### Consumer inventory status

The repository tree confirms a broad AQLI Java surface across runtime core, adapters, plugins, runners and CLI. GitHub code search did not return usable indexed matches for the DTO identifiers, so this pass does not claim a complete import/construction-site inventory. Before changing public interfaces, perform a checked-out repository-wide search and classify direct implementors, method callers, serialization boundaries and external SDK consumers. No implementation migration is authorized by this audit alone.

## RequestContext construction and adapter gap

A further comparison of the current AQLI and Aljabr `InferenceRequest` implementations found a shared construction limitation that matters to canonical migration:

- Both DTOs expose `getRequestContext()`, but their public JSON constructors and builders synthesize the context internally from only API key, request ID, user ID, session ID, and trace ID.
- Neither builder accepts a `RequestContext` instance. Consequently, callers cannot submit a context carrying tenant ID, model scope, preferred-device identifier, cost-sensitivity, or a context-specific timeout through the normal `InferenceRequest` builder.
- The existing `AljabrInferenceDtoAdapter` maps the identity fields above, but does not map the other `RequestContext` hints. This is not currently a lossy conversion of a custom context accepted by the builder—the builder cannot accept one—but it exposes a missing capability in both parallel contracts and would become a real loss if custom context injection is added on only one side.
- The request-level `timeout` field is mapped separately. It must not be confused with `RequestContext.timeout()`.

### Required design before context consolidation

1. Decide whether `RequestContext` is immutable request data, an execution-scoped context, or a composition of both. Tenant/model scope and user/session/trace identity may be portable request metadata; device and cost hints are routing hints; timeout is an execution policy.
2. Add an explicit context-injection path only after that ownership split is agreed. Avoid silently flattening all context fields into unrelated builder scalars.
3. If context conversion remains necessary at the compatibility boundary, add bidirectional adapters and tests for every supported field, including unknown device identifiers.
4. Preserve the existing constructors and builder behavior during the migration window. Do not claim complete request round-trip fidelity until custom context semantics are represented and tested.

This is a source-level API capability finding. It does not justify moving runtime routing policy into Aljabr or removing either repository's compatibility context type.

## Runtime orchestration SPI parity: expanded ownership boundary

A second source-comparison pass found a wider duplicated family in the two inference SPI modules. The following pairs are byte-for-byte identical after normalizing the AQLI/Aljabr package prefix:

| Contract | AQLI path | Aljabr path | Initial ownership recommendation |
|---|---|---|---|
| `EngineContext` | `:spi:aqli-spi` | `:spi:aljabr-spi-inference` | AQLI runtime |
| `ExecutionContext` | `:framework:spi:aqli-spi-inference` | `:spi:aljabr-spi-inference` | AQLI runtime orchestration |
| `ExecutionStatus` | same | same | AQLI runtime orchestration |
| `ExecutionToken` | same | same | AQLI runtime orchestration |
| `Priority` | same | same | Aljabr portable inference request contract |
| `AsyncJobStatus` | same | same | AQLI asynchronous job lifecycle |
| `BatchConfig` | same | same | AQLI serving scheduler |
| `BatchInferenceRequest` | same | same | AQLI batch admission/serving contract |
| `BatchMetrics` | same | same | AQLI runtime observability |
| `BatchResponse` | same | same | AQLI batch serving result |
| `BatchResult` | same | same | AQLI batch serving result |
| `BatchScheduler` | same | same | AQLI scheduling/orchestration |
| `BatchStrategy` | same | same | AQLI scheduling policy |

The `BatchResult` pair has the same structure and behavior, with the AQLI version importing Aljabr's `ErrorPayload`; the other listed pairs compare identical after package normalization.

### Why this changes the consolidation plan

The duplication is not limited to portable model-execution contracts. It includes application/runtime orchestration concepts: a global engine executor/configuration context, mutable per-request execution state, durable execution tokens and transitions, asynchronous job status, and a complete batch scheduler contract.

The intended separation is:

- **Aljabr:** portable model execution, inference request/response/stream/embedding contracts, tensor/model execution, reusable backend capabilities, and low-level batch computation primitives.
- **AQLI:** runtime engine lifecycle, mutable execution orchestration, execution/job state, asynchronous job management, batch admission and scheduling policy, runtime batch metrics, routing, serving, and deployment operations.

AQLI should not need to import Aljabr's serving scheduler just to run an inference engine. Conversely, Aljabr should not retain a second copy of AQLI's application-level scheduler or job lifecycle merely because the original interfaces were copied into both SPI modules.

### Migration guardrails

1. Treat this as an ownership migration, not an immediate deletion. Inventory implementations and consumers of each duplicated type in both repositories, including provider modules, tests, persistence, serialization, and SDKs.
2. Keep `Priority` canonical in Aljabr because it is a portable inference-request property; convert the AQLI enum at the existing adapter boundary until the AQLI request facade is retired.
3. Move runtime orchestration contracts toward AQLI ownership only after verifying that no Aljabr framework execution implementation requires them. If Aljabr needs per-call execution state for kernel/model execution, define a smaller backend/framework execution context rather than importing AQLI's job/scheduler lifecycle.
4. Do not confuse serving-level dynamic/continuous batching with low-level tensor batch operations. AQLI owns admission, queueing, fairness, timeout, and scheduling; Aljabr owns reusable batched model/tensor execution capabilities.
5. Keep AQLI compatibility types during the transition and prevent the two copies from evolving independently.

This is a focused parity and ownership pass, not a complete inventory of every duplicated SPI type. Additional overlapping types (including inference phase/pipeline, observer, validation, and verification contracts) still require classification.


## Inference phase, pipeline, observer, validation, and verification contracts

A direct source inspection of the remaining overlapping inference contracts found that identical definitions do not imply shared ownership. Several encode AQLI serving orchestration, while speculative-token verification is a model-execution capability.

### Findings and ownership

| Contract | Source comparison / dependency | Recommended owner | Decision |
|---|---|---|---|
| `InferencePhase` | Same enum and behavior in both repositories. Its phases include authorization, provider routing, audit, observability, and cleanup in addition to model processing. | AQLI | Treat as runtime orchestration vocabulary, not an Aljabr execution contract. |
| `InferencePipeline` | Same interface, but its methods mutate and return `ExecutionContext`; it executes ordered runtime phases. | AQLI | Keep the pipeline in AQLI. Aljabr should expose model execution primitives, not the serving pipeline. |
| `InferencePhasePlugin` | AQLI version extends `AqliPlugin`; Aljabr version is in `:spi:aljabr-spi-plugin` and extends `AljabrPlugin`. Both depend on phase and execution-context concepts. | AQLI for serving phases | Do not treat them as interchangeable. AQLI phase plugins operate on mutable runtime state. Aljabr's generic plugin SPI may remain for framework/model extensions, but should not duplicate AQLI serving-phase semantics. |
| `InferenceObserver` | Same lifecycle callbacks in both repositories; callbacks accept project-specific `ExecutionContext`, including phase lifecycle and stream events. AQLI's concrete observer records Micrometer metrics and W3C trace metadata. | AQLI | Keep serving lifecycle observation in AQLI. Aljabr telemetry, if needed, should use smaller framework execution events independent of AQLI runtime context. |
| `ValidationContext` | Same record/builder, but it wraps `RequestContext` and supplies a community API-key fallback. | AQLI | This is request admission/authentication context, not tensor/model execution. Do not make API-key policy canonical in Aljabr. Split a pure model-input validation context only if an Aljabr validator needs it. |
| `VerificationRequest` / `VerificationResponse` | Same token-list DTO definitions. They describe verification of draft tokens against a target model. | Aljabr | This is speculative decoding/model-execution data and should have one Aljabr canonical contract, with AQLI compatibility conversion only if AQLI callers exist. |

### Compute stage versus serving phase

Aljabr's `InferenceStage` describes PREFILL, DECODE, and COMBINED compute placement/execution. It is materially different from `InferencePhase`, whose enum includes authorization, routing, audit, observability, and cleanup. Do not collapse or rename these concepts as if they were equivalent.

The current `InferencePhase` definition in Aljabr is misplaced at the framework inference boundary: most values are serving lifecycle concerns. AQLI should own that enum and its pipeline. Aljabr should retain compute-oriented concepts such as `InferenceStage` and define narrower model-execution events/stages where required.

### Implementation evidence and gaps

- AQLI's `InferencePhaseNodeAdapter` maps phase plugins into AQLI's visual node execution model and derives retry/critical-failure behavior from the phase. This is runtime workflow integration, not model math.
- AQLI's `DefaultInferenceObserver` writes trace metadata into mutable `ExecutionContext`, records Micrometer request/stream durations, and cleans up runtime span state. This confirms serving/operational ownership.
- AQLI's `ObservabilityPhasePlugin` consumes runtime response, error, tool-usage, and phase-latency variables.
- AQLI also has a separate runtime `DraftVerificationStrategy` accepting `SpeculativeBatch` and a raw `MemorySegment` of target logits. Its current `greedy()` implementation is explicitly a placeholder returning zero. This is not equivalent to the token-list `VerificationRequest` / `VerificationResponse` pair and exposes a second speculative-verification shape.
- The token-list verification DTOs retain caller-provided mutable lists and do not validate nulls, token ranges, or `numAccepted` consistency. The response derives `numAccepted` once from the supplied list. Canonicalization should include immutability and invariant tests, not just package conversion.
- GitHub code search did not return indexed consumer results for these identifiers. The source tree confirms AQLI runtime implementations and adapters, but a full checked-out import/construction-site inventory remains unavailable through remote source inspection. No public types are removed by this audit.

### Recommended migration sequence

1. Keep `InferencePhase`, `InferencePipeline`, `InferenceObserver`, `ValidationContext`, and serving `InferencePhasePlugin` under AQLI ownership. Deprecate/remove the Aljabr duplicates only after implementation/consumer inventory and a compatibility window.
2. Keep Aljabr `InferenceStage` as the compute-stage concept; do not import AQLI's serving phase enum into Aljabr's model execution path.
3. Consolidate speculative verification around one Aljabr-owned, typed model-execution contract. First inspect actual speculative decoding implementations and tensor/logit representations in both repositories; do not promote raw `MemorySegment` into a portable DTO.
4. Keep AQLI scheduling, speculative batch admission, draft/target model selection, and runtime lifecycle in AQLI. Delegate mathematical token acceptance/verification to Aljabr.
5. Add tests for defensive copying, empty/null inputs, accepted-token count consistency, target/prefix/draft shape constraints, and conversion compatibility before migrating callers.

This audit classifies ownership and identifies design gaps. It does not authorize immediate deletion of duplicate public types or assert that the existing speculative verification path is complete.


## Legacy convenience inference DTO parity

A further normalized source comparison found four more byte-for-byte duplicate pairs in the AQLI and Aljabr inference SPI modules:

| Contract | AQLI path | Aljabr path | Assessment |
|---|---|---|---|
| `ChatMessage` | `:framework/spi/aqli-spi-inference/.../inference/dto` | `:core/aljabr-spi-inference/.../inference/dto` | Minimal role/content convenience value; a strict subset of canonical `Message`. |
| `InferenceContext` | same AQLI DTO package | same Aljabr DTO package | API key, request ID, tenant ID, trace ID only; overlaps a subset of `RequestContext` and has separate defaulting behavior. |
| `PromptRequest` | same AQLI DTO package | same Aljabr DTO package | Simple chat prompt/options DTO; a strict subset of canonical `InferenceRequest` and lacks tools, multimodal attachments, full context, priority, and other request metadata. |
| `InferenceResult` | same AQLI DTO package | same Aljabr DTO package | Simplified result DTO; a strict subset of canonical `InferenceResponse`. |

### Ownership and consolidation recommendation

These classes are not equivalent to the richer canonical contracts already established in the inference SPI. Their presence in both repositories creates a second, less expressive request/response vocabulary in addition to the package-duplicated canonical DTOs.

- Keep `InferenceRequest`, `InferenceResponse`, `StreamingInferenceChunk`, and the canonical embedding contracts as the shared framework boundary.
- Treat `PromptRequest` and `InferenceResult` as convenience/legacy shapes, not alternative engine contracts. If a high-level AQLI SDK needs a simple prompt API, implement it as an adapter that constructs the canonical request and maps the canonical response.
- Treat `ChatMessage` as a convenience text-only input, not a replacement for multimodal `Message`.
- Avoid treating `InferenceContext` as a second canonical context. Resolve its defaults and field mapping against the eventual portable request metadata / execution-context split before migration.
- Do not add new provider or runner APIs that accept these convenience DTOs when canonical Aljabr request/response APIs already exist.

### Compatibility and correctness notes

- `PromptRequest` silently normalizes non-positive `maxTokens` to 256, non-positive `temperature` to 1.0, and invalid/non-positive `topP` to 1.0. These defaults differ from the semantics of a general inference parameter map and must be preserved only at an explicit convenience boundary, not silently injected into canonical requests.
- `InferenceContext.Builder` creates a random request ID and defaults tenant ID to `default`; it also defaults trace ID to request ID. These are observable builder semantics that need compatibility tests if the DTO is deprecated or adapted.
- `InferenceResult.extra`, `PromptRequest.messages/stop/extra`, and the token-list DTOs inspected above retain mutable collections. The records are shallowly immutable only; defensive copies and null policy should be designed at the canonical boundary.
- The four AQLI/Aljabr pairs compare identical after package normalization, but no complete consumer inventory is available from remote code search. Do not delete either package's classes based on parity alone.

### Migration sequence

1. Inventory all constructors, builder calls, serializers, and SDK entry points for these four DTOs.
2. Add explicit conversion tests from convenience DTOs to canonical requests and canonical responses to convenience results, including documented default behavior.
3. Prefer SDK-level convenience builders/adapters over duplicate SPI DTOs.
4. Deprecate duplicate SPI convenience types only after internal and downstream compatibility is understood; remove only in a deliberate breaking release.
