# RunCommand Decomposition Assessment and Execution Plan

## Purpose

Decompose `cli/aqli-cli/.../RunCommand.java` along real capability and adapter boundaries. The goal is not merely to reduce line count: reusable behavior must move to the module that owns it, while Picocli parsing, CLI policy, terminal rendering, and process behavior remain at the CLI boundary.

This assessment is based on the repository `main` baseline at commit `138a96ec597e3c38c862186d74d6878242b741b6`. At that baseline, `RunCommand.java` is 5,749 lines and its `run()` method spans approximately lines 537–1703 (about 1,167 lines). These counts are baseline measurements and will change as the plan is executed.

## Guardrails

- Follow the root `AGENTS.md`: inspect callers, tests, dependencies, and related implementations before moving behavior.
- Prefer existing capability modules. Do not create a module for an arbitrary helper.
- Move implementation, tests, and resources together; update every consumer and remove stale copies.
- Keep framework contracts dependency-light; runtime capabilities may depend on framework contracts and lower-level runtime modules; CLI depends on capabilities, never the reverse.
- Keep CLI option interpretation, terminal presentation, interactive prompts, shell command construction, and process exit behavior in CLI.
- Keep MOSS-specific model behavior with MOSS; ONNX session/graph/cache execution with the ONNX runner; generic TTS behavior provider-neutral.
- Keep audio and media behavior reusable outside CLI. Keep generic cross-modality artifacts in media, audio-domain behavior in audio.
- Preserve behavior and public API compatibility unless a specific change is justified. Avoid broad renames and unrelated cleanup.
- Each PR should have one architectural purpose and focused tests. Do not claim local tests were run unless they were actually run.

## Current ownership already extracted

The following existing work is considered part of the target architecture and must be reused rather than duplicated:

- `RunStreamChunkHandler` — CLI stream chunk presentation/routing and token timing.
- `StreamingAudioChunkRouter`, `StreamingAudioOutputSession`, and `LivePcmAudioPlayer` — audio chunk routing, output buffering/file lifecycle, and PCM preview.
- `AudioMetadataSidecarStore` and `ImageMetadataSidecarStore` — reusable metadata persistence/integrity operations.
- `MossTtsOptimizedCacheMaintenance` and `MossTtsOnnxCacheDiagnostics` — runner-owned MOSS/ONNX cache maintenance and snapshots.
- `ModelSourceResolver` and `ModelSourceRoutingPolicy` — CLI-specific source classification and CLI routing policy.
- `TerminalDownloadProgressRenderer`, replay builders/parsers, and shell-command formatter — CLI presentation/replay boundaries.

Do not reintroduce these responsibilities into a new facade or duplicate their calculations.

## Responsibility assessment

| Concern currently in RunCommand | Correct ownership |
|---|---|
| Picocli options, inherited environment, interactive policy, terminal messages, JSON/SSE formatting, auto-open, process exit | CLI adapter |
| CLI provider aliases, MCP selection, local-path precedence, option validation | CLI policy |
| Local model index lookup, repository artifact resolution, downloads, import/copy mechanics | Existing repository/model-resolution capability where semantics are reusable; CLI supplies explicit CLI policy |
| Runner/provider/format selection | Canonical SDK/runtime route policy and contracts; CLI translates flags |
| Direct Safetensors, GGUF, LiteRT execution and compatibility checks | Corresponding runner/runtime capabilities; CLI selects explicit mode and reports errors |
| InferenceRequest assembly from CLI flags | A focused request-mapping boundary; canonical request semantics stay in SPI/SDK |
| Stream subscription, fallback decision, stream resource lifecycle, completion coordination | Shared stream/runtime orchestration only where it is transport- and presentation-neutral; CLI supplies output/error callbacks |
| Audio chunk decoding/classification, buffering, atomic file output | Audio capability (already substantially extracted) |
| Output path choice, extension override warnings, auto-open, terminal save messages | CLI |
| Cross-modality artifact integrity/metadata | Media capability; audio-specific metadata in audio capability |
| MOSS model/codec layout, tokenizer, model-specific diagnostics | MOSS integration |
| ONNX graph/session/cache execution and warmup | ONNX runner |
| CLI diagnostic option handling, terminal/JSON diagnostic rendering | CLI presentation |
| Metric calculation/recording | Observability/metrics capability when reusable; terminal formatting stays CLI |
| Response text/JSON rendering, SSE envelope, terminal statistics | CLI presentation |

## Decomposition sequence

### Stage 0 — Baseline and behavior characterization

- Inventory all Picocli options, injected services, mutable command state, method groups, call sites, tests, and module dependencies.
- Add focused tests around request semantics, route selection, stream completion/failure, output publication, and exit behavior before moving their implementations.
- Identify global/static state and test leakage in the Quarkus command tests.
- Record the exact Gradle tasks and CI checks relevant to each touched module.

**Exit criteria:** each planned extraction has a known caller set, behavior contract, test location, and dependency direction.

### Stage 1 — Stream execution lifecycle

- Keep `RunStreamChunkHandler` as the chunk/presentation adapter and `StreamingAudioChunkRouter` as the audio capability router.
- Isolate the remaining subscription/fallback/completion lifecycle from the large `run()` method.
- Separate transport-neutral stream timing/resource coordination from CLI callbacks. Do not move terminal output or Picocli state into audio/runtime modules.
- Ensure primary and fallback streams share the same finalization behavior and that all error/interruption paths close PCM and encoded-audio resources exactly once.
- Add deterministic tests for success, primary failure with fallback, fallback failure, null stream, and interruption.

**Exit criteria:** `run()` delegates stream lifecycle through a focused boundary; chunk interpretation remains in the existing handler/router; tests prove cleanup and completion behavior.

### Stage 2 — Model source and artifact resolution

- Separate CLI source intent (explicit `--modelFile`, `--modelDir`, positional path, provider aliases, MCP, route-report mode) from actual resolution.
- Audit `LocalModelIndex`, `LocalModelIndexResolver`, `ModelSourceResolver`, HF/Kaggle/local repository modules, and SDK model preparation before moving any implementation.
- Reuse existing repository contracts/services. Move only genuinely reusable index/artifact operations out of CLI; do not move Picocli policy into SDK.
- Remove duplicate path/format/provider inference once a single owner is established.

**Exit criteria:** CLI policy produces a clear model-source intent; reusable repository work is not implemented in `RunCommand`; all supported source types retain existing precedence.

### Stage 3 — Execution planning and dispatch

- Make runner selection and effective execution mode explicit before execution.
- Audit direct Safetensors, standalone GGUF, standalone LiteRT, cached LiteRT QAT, and normal SDK paths.
- Keep engine implementations in their respective runner modules and ensure Aljabr remains the single source of truth for engine-neutral tensor/model behavior.
- Preserve explicit-engine refusal semantics; do not silently route an explicit Java request to a different engine.
- Converge response/error/metrics semantics where possible without introducing a generic facade that merely wraps existing calls.

**Exit criteria:** CLI constructs an explicit execution decision and delegates to the selected runtime capability; runner selection is testable without invoking model execution.

### Stage 4 — Inference request composition

- Isolate mapping from CLI options to `InferenceRequest` parameters, messages, metadata, tools, RAG, audio/TTS, and multimodal settings.
- Keep Picocli option defaults and CLI-only flags at the boundary.
- Reuse canonical SPI request types; do not create duplicate DTOs or make framework contracts depend on Picocli.
- Test option combinations and metadata precedence.

**Exit criteria:** request construction is independently testable and the command no longer assembles the full request inline.

### Stage 5 — Audio and media output completion

- Reuse the existing audio output session/router and media/audio sidecar stores.
- Separate audio/image byte decoding and artifact publication from CLI naming/presentation only where a real reusable consumer exists.
- Keep output-path and extension policy, sidecar opt-in flags, terminal save messages, and auto-open in CLI.
- Reassess `LivePcmAudioPlayer`: it currently resides in audio-core but uses Java Sound and writes directly to `System.err`. Decide whether to inject a warning sink and/or isolate the Java Sound device adapter behind an optional boundary. Do not add a module unless the dependency boundary is real.
- Ensure failures in optional sidecars or playback do not fail inference.

**Exit criteria:** reusable artifact/audio operations have no CLI output dependency; output policy remains explicit at the adapter boundary.

### Stage 6 — TTS and ONNX diagnostics/operations

- Keep MOSS model/codec resolution, ONNX graph/cache operations, warmup, and runner diagnostics with their respective runner/model capabilities.
- Reuse the existing runner cache snapshot and optimized-cache maintenance APIs.
- Extract only CLI-specific diagnostic orchestration/rendering from `RunCommand`; avoid moving terminal/JSON presentation into runner modules.
- Move MOSS codec repository acquisition only after confirming whether the repository operation is generic or MOSS-specific and whether another consumer exists.
- Keep TTS request/voice semantics provider-neutral; do not create an ONNX-only generic TTS layer.

**Exit criteria:** `RunCommand` does not perform ONNX/MOSS execution or duplicate runner diagnostics; CLI owns only option interpretation and report presentation.

### Stage 7 — Response reporting, metrics, and final cleanup

- Separate pure metric extraction/aggregation and reusable recording from CLI output.
- Keep response text, JSON, SSE, color, terminal statistics, and auto-open policy in CLI.
- Consolidate duplicate metadata parsing/formatting only where a shared owner is justified.
- Remove obsolete imports, state, helpers, and dependencies after all callers have migrated.
- Revisit command size and method cohesion; do not optimize for a target line count.

**Exit criteria:** `RunCommand` is a thin CLI composition/adapter that parses options, selects policies, invokes application/runtime capabilities, and renders results.

## Priority and PR strategy

1. **P0:** characterize stream lifecycle and execution behavior; no broad module moves before tests establish the contract.
2. **P1:** isolate stream lifecycle/fallback and cleanup (the remaining large inline execution block).
3. **P1:** audit and extract reusable model resolution/artifact work.
4. **P1:** explicit execution planning and runner dispatch.
5. **P2:** request composition.
6. **P2:** audio/media output boundary completion.
7. **P2:** ONNX/MOSS diagnostic orchestration.
8. **P3:** response metrics/reporting and final cleanup.

Each item should be a separate, reviewable PR when it crosses an ownership boundary. A PR may contain the implementation, its tests, and the minimal dependency/build changes required for that single purpose. Update this plan when repository inspection disproves an assumption or reveals an existing capability that should be reused.

## Validation approach

- Run focused module tests first for every changed capability.
- Run the CLI test suite for command wiring and behavior.
- Run affected Gradle compile/test tasks and CI workflows when available.
- Review the final diff for stale classes, imports, resources, dependencies, duplicate implementations, and dependency inversion.
- Mac hardware inference/performance tests are reserved for changes that affect actual Metal/native/device behavior; do not ask for hardware testing for pure Java refactors.
