# Repository Agent Guidelines

These rules apply to all changes in this repository. Follow the existing Gradle project layout and architecture documentation, but prefer the actual capability boundary over the location where code happens to be used today.

## 1. Identify ownership before editing

Before adding, moving, or extracting a class:

1. Inspect its callers, tests, Gradle dependencies, and related implementations.
2. Identify the capability that owns the behavior, not the adapter or command that currently invokes it.
3. Check whether an existing module already owns the capability. Extend it rather than creating a near-duplicate module or abstraction.
4. Move implementation, tests, and required resources together. Update all consumers and remove obsolete copies/imports.
5. Keep the change focused; do not combine unrelated package cleanup or broad redesign with a module extraction.

A class does not belong to the CLI merely because the CLI was its first or only caller. Likewise, do not move code to another package while leaving it in the wrong Gradle module.

## 2. Module and dependency boundaries

### `framework/`: contracts and public abstractions

- Keep this layer limited to stable interfaces, SPIs, canonical request/response types, enums, annotations, and other dependency-light contracts.
- Do not put filesystem/network/database access, model execution, native runtime integration, or concrete service implementations here.
- Framework modules must not depend on `runtime/`, `cli/`, or transport adapters.
- Keep contracts transport-neutral. Do not make an SPI type depend on Picocli, REST/JAX-RS, Quarkus HTTP request objects, CLI formatting, or a particular wire protocol.
- Elevate a genuinely engine-neutral ML/tensor/model contract to Aljabr rather than creating a competing Aqli-owned copy.

### `runtime/`: capability implementations and orchestration

- Place reusable implementation in the runtime module that owns the capability.
- Prefer capability-oriented modules and packages, for example `runtime/audio/aqli-audio-core` with packages such as `tech.kayys.aqli.audio.metadata` and `tech.kayys.aqli.audio.output`.
- Keep shared media/file primitives in a media capability when they are used by audio, image, video, or other modalities. Do not make one modality depend on another merely to reuse generic media behavior.
- Keep model-family-specific behavior with that model integration, and execution-provider-specific behavior with its runner/backend.
- Runtime modules may depend on framework contracts and lower-level runtime capabilities. Avoid reverse dependencies and dependency cycles.
- Reuse an existing capability module before creating another one. Add a new module only when it establishes a real ownership/dependency boundary, not just to house one arbitrary class.

### `cli/`, REST/API, and SDK adapters

- Treat CLI, REST endpoints, RPC handlers, and SDK front ends as adapters. They translate external input into application requests, invoke shared services, and translate results into their respective output formats.
- Do not make the CLI module the owner of reusable business logic, model execution, metadata persistence, media handling, caching, or capability configuration.
- CLI-specific parsing, terminal presentation, shell command rendering, interactive prompts, and process exit behavior may remain in the CLI.
- REST-specific routing, HTTP status mapping, serialization/wire concerns, and authentication filters may remain in the API adapter.
- SDK-specific convenience methods and transport bindings may remain in SDK adapters.
- When multiple adapters expose the same use case, they should call the same application service and share the same canonical request/response semantics. Do not independently reimplement the use case in each adapter.
- Do not introduce a generic service/facade solely to make two classes look symmetrical. Extract a shared service when there is a real use case and more than one meaningful consumer.

## 3. Audio, TTS, and media ownership

### Audio capability

- Reusable audio behavior belongs outside the CLI, in audio capability modules.
- Audio metadata persistence, audio artifact publication/writing, and audio-domain operations should be reusable by CLI, REST, SDK, and programmatic callers.
- Keep generic media integrity, file identity, and cross-modality artifact primitives in the media capability when they are not audio-specific.
- Keep CLI-only replay argument parsing, shell command construction, terminal formatting, and output auto-open policy in the CLI unless another adapter has a demonstrated need for the same behavior.

### Text-to-speech is a capability, not a model or backend

- Treat TTS as a reusable capability that can be implemented by multiple model families and execution providers.
- Put stable TTS request/response contracts and provider-neutral capability interfaces in the appropriate framework/SPI layer. Put reusable application orchestration and implementation support in the TTS/audio runtime capability.
- Do not define the TTS module as an ONNX module or as a MOSS module. A future Java-native, GPU, remote, or other provider must be able to implement TTS without depending on MOSS or ONNX-specific APIs.
- Keep MOSS-specific prompt handling, model assets, tokenizer behavior, graph catalog, and MOSS inference algorithm with the MOSS model integration.
- Keep ONNX Runtime session management, ONNX graph execution, optimized ONNX graph caching, and ONNX provider details with the ONNX runner.
- Keep backend/provider adapters behind the TTS capability contract. The generic TTS API must not expose ONNX Runtime types, MOSS-only data structures, or CLI settings.
- Do not move MOSS implementation into a generic TTS module merely because MOSS currently provides the only TTS implementation.

## 4. Contracts and data types

- Use one canonical request/response contract for a use case across CLI, REST, SDK, and internal callers where the semantics are the same.
- Keep domain/application request types independent of transport-specific DTOs. Map HTTP/JSON/protobuf/CLI input at the adapter boundary.
- Avoid duplicate DTOs that carry the same meaning but drift in field names, defaults, validation, or behavior.
- Do not use a `dto` package as a dumping ground for implementation classes, stores, resolvers, or services.
- Keep validation and defaults at the contract/domain boundary where they are consistent across all adapters; keep transport-specific validation and error mapping in the adapter.
- Prefer immutable request/response values. Defensively copy mutable collections and byte arrays where appropriate.

## 5. Naming and package organization

- Use packages that express capability and responsibility, such as `tech.kayys.aqli.audio.metadata`, `tech.kayys.aqli.audio.output`, `tech.kayys.aqli.tts`, or `tech.kayys.aqli.media.integrity`.
- Avoid packages rooted under `tech.kayys.aqli.cli` for reusable implementation.
- Do not use names such as `Common`, `Utils`, `Helper`, or `Manager` when a domain-specific responsibility can be named.
- Keep model-family names (for example, MOSS) and provider names (for example, ONNX) explicit where the implementation is specific to them.
- Do not rename public APIs or move packages without updating all consumers, tests, documentation, service registrations, and module dependencies.

## 6. Gradle and testing

- Register new projects in `settings.gradle.kts` using the repository's existing optional-project conventions.
- Follow the formatting, Java toolchain, dependency-version, publication, and test conventions of comparable modules. Avoid inventing a separate build style for each new module.
- Declare dependencies at the narrowest appropriate scope. Use `api` only when a dependency's types are part of the module's public API; otherwise use `implementation`.
- Keep tests with the module that owns the behavior. Test capability contracts and implementations independently from CLI/API adapter tests.
- When moving code, search for all references and verify no stale source, import, test, resource, or Gradle dependency remains.
- Run the smallest relevant module tests first, then affected integration/build checks when practical. Report exactly what was and was not run; never claim tests or CI passed without evidence.

## 7. Refactoring workflow

- Prefer small, reviewable changes with one architectural purpose per PR.
- Preserve behavior unless the task explicitly requests a behavior change.
- Do not overlap an independently delegated refactor. Coordinate by respecting its ownership and avoid editing the same command flow or files unless required for a dependency fix.
- Before moving a class, inspect its responsibilities. Split mixed concerns when necessary rather than relocating the entire mixed class into an unrelated module.
- After extraction, verify dependency direction: adapters depend on capabilities; capabilities do not depend on adapters.
- Update this file and `ARCHITECTURE.md` when an approved architectural rule changes.


## 8. SDK-first API and transport parity

The approved direction is documented in `docs/architecture/sdk-first-capability-architecture.md`.
Apply these rules when changing inference requests, `RunCommand`, REST endpoints, gRPC handlers, or SDK modules.

- The AQLI SDK API is the canonical **AQLI public, transport-neutral contract**. CLI, REST, gRPC, and programmatic callers must express the same use-case semantics through it.
- The SDK API is a contract, not the execution engine. Shared application services own orchestration, capability dispatch, defaults, validation, routing decisions, streaming lifecycle, cancellation, and error semantics.
- CLI, REST, and gRPC are adapters. They may have transport-specific parsing/serialization and error mapping, but must invoke the same application service path. Never implement inference behavior independently in a command, REST resource, or RPC handler.
- Capability APIs own typed capability-specific requests, responses, options, validation rules, and capability contracts. Keep audio/image/video/spatial parameters out of a growing flat text-generation request.
- Use a common request envelope for cross-cutting fields and typed capability task payloads for modality-specific fields. Support compatible multimodal composition; do not assume every request has exactly one modality.
- Keep Picocli annotations and CLI option groups in CLI adapter code. Keep HTTP and protobuf wire DTOs at their adapter boundaries. Map all of them into the same SDK/application semantics.
- Do not create one CLI Gradle module per capability by default. Start with capability-owned option/mapping packages; create separate modules only for a demonstrated packaging or dependency-isolation boundary.
- Do not make capability API modules depend on Picocli, REST/JAX-RS, gRPC, Quarkus transport types, or concrete model runners.
- Do not treat `AqliClient.GenerationRequest` and SPI `InferenceRequest` as interchangeable. Audit their callers and compatibility obligations before changing either.
- Keep the current SPI `InferenceRequest` as an internal execution contract during migration. Do not simply rename or expose it as the canonical SDK request: it contains generic parameter-map compatibility, SPI/runtime concerns, and a deprecated native `MemorySegment` handle.
- Introduce one application-layer mapping path from canonical SDK/capability requests to the internal execution SPI. Every transport must use that same path.
- Avoid permanent duplicate generic request models and chains of converters. Define the canonical public model once, retain only deliberate compatibility adapters, and document their removal/versioning plan.
- Elevate contracts that are genuinely engine-neutral and reusable by any ML/inference framework to Aljabr. Keep AQLI SDK contracts focused on AQLI's public client/use-case surface; do not duplicate Aljabr tensor, model, or general serving SPIs.
- Preserve existing CLI behavior and REST/gRPC wire compatibility where feasible. Treat breaking protocol changes as explicit versioned migrations.
- Add cross-transport conformance tests for normalized application requests, defaults, validation, errors, streaming event order/termination, cancellation/resource cleanup, metadata/usage, and multimodal behavior.
- Introduce capability API modules incrementally after auditing existing SPI ownership and consumers. Reuse existing modules where possible; do not create speculative empty modules.
