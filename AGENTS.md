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
- Keep provider-specific lifecycle options in provider-owned SDK capability contracts (for example, Hugging Face file include patterns and task classification); do not add them to the provider-neutral `ModelPullRequest` or silently discard them in generic overloads.
- Preserve Hugging Face repository architecture and metadata through `HuggingFacePullRequest` when a caller needs manifest classification or provenance; the local SDK adapter must forward these fields to the repository without exposing repository types to CLI/REST/gRPC adapters.
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
- For Hugging Face pulls, map REST `format`/`includePatterns`/`taskType` and gRPC `format`/`include_patterns`/`task_type` into `HuggingFacePullRequest`; map non-Hugging-Face pulls into `ModelPullRequest`. Do not duplicate download logic in either transport.
- Automatic model pulls initiated by `RunCommand` must use the SDK model-lifecycle capability (including `HuggingFaceModelOperations` for Hugging Face); never inject or call a repository implementation from the CLI. Keep local-index refresh and CLI progress presentation in the adapter.
- Add cross-transport conformance tests for normalized application requests, defaults, validation, errors, streaming event order/termination, cancellation/resource cleanup, metadata/usage, and multimodal behavior.
- Introduce capability API modules incrementally after auditing existing SPI ownership and consumers. Reuse existing modules where possible; do not create speculative empty modules.


## 9. Pluggable `aqli run` capability contributions

- Keep `RunCommand` an orchestration/adapter entry point; do not add modality-specific option fields or request-mapping logic to it as capabilities grow.
- Put CLI extension contracts in `:cli:aqli-cli-spi` (`cli/aqli-cli-spi`). This is intentionally a CLI-only SPI and may depend on Picocli; SDK capability APIs and REST/gRPC contracts must never depend on it.
- Discover optional CLI contributions through Java `ServiceLoader` using `RunCapabilityProvider`. Providers must have stable, unique capability IDs matching the created `RunCapability` ID.
- Registry ordering must be deterministic. Fail fast for duplicate/blank IDs, provider/capability ID mismatches, and invalid service registrations.
- A `RunCapability` may contribute Picocli options and validate parsed CLI arguments. Capability-specific mapping must target the canonical typed SDK capability request; do not introduce a permanent `Map<String,Object>` request sink or make the registry an API source of truth.
- The registry must not choose inference backends or execute inference. Runtime owns backend selection and execution.
- Keep optional capability providers out of the base CLI dependency graph unless they are intentionally packaged in that distribution. A third-party provider should be loadable without editing `RunCommand`.
- Add capability-specific SDK request contribution only after the canonical SDK task payloads and application mapping boundary are defined. Do not expose internal SPI `InferenceRequest` as a substitute public SDK request.


## 10. SDK capability registry and transport adapter separation

- The SDK owns the canonical, transport-neutral capability API: typed capability contracts and payloads, shared invocation semantics, and the public capability discovery surface.
- The SDK capability registry resolves available SDK capability implementations. It is not the CLI registry, runtime engine catalog, or transport adapter registry.
- Shared application execution owns validation, defaults, capability dispatch, execution lifecycle, streaming, cancellation, deadlines, and error semantics. Keep this behavior behind SDK/application contracts; do not put it in transport registries.
- The CLI run capability registry owns only Picocli contributions, CLI-specific validation, and mapping parsed arguments into canonical SDK requests. It must not define capability semantics or execute capabilities.
- REST and gRPC adapters map their wire DTOs into the same canonical SDK requests and invoke the same application execution path. Their protocol-specific serialization, authentication, and error translation remain transport concerns.
- The runtime engine catalog resolves execution engines/backends for an already-resolved application capability. It must not become a second public capability API.
- Keep these registries separate. Integrate them through stable capability identifiers and typed SDK contracts, not by merging their lifecycle or responsibilities.
- A capability must remain usable through the Java SDK and non-CLI transports when the CLI module is absent.
- Do not introduce a generic `AqliCapability<R, S>`, invocation envelope, or registry until existing SDK/API, application, and runtime contracts have been audited for reusable request, response, async, streaming, cancellation, and error semantics. Reuse and evolve existing contracts where possible; avoid parallel generic request models.
- The common invocation context may eventually carry cross-cutting request identity, tracing, deadlines, cancellation, and execution hints. Define it only after confirming which fields are truly shared and how they map to existing request context and runtime contracts.
- New capability work should proceed in this order: canonical typed SDK contract and application path; SDK implementation registration; CLI/REST/gRPC adapters; runtime execution support. Keep modality-specific option parsing out of the canonical SDK contract.


- Non-streaming REST chat must normalize DTO messages and tools to TextGenerationRequest and invoke the shared SDK typed text operation through AqliChatService. Keep transport response serialization in the resource, but do not rebuild inference requests or sampling defaults there.
- Do not migrate REST streaming chat to TextGenerationChunk until that contract preserves tool-call deltas and required stream metadata, and cancellation is verified. Keep the existing shared stream path until then.
- Public text-generation requests use SDK-owned `SdkMessage` and SDK-owned content-part/tool-call types. Do not add execution SPI message types (including `tech.kayys.aqli.spi.Message` or `ToolCall`) to public SDK capability signatures.
- Convert SDK-owned messages to execution SPI messages only in the SDK-core `SdkMessageMapper`; CLI, REST, and gRPC adapters must construct the canonical SDK message types and must not duplicate this conversion.
- Preserve all message semantics during migration: role, plain text, multimodal content parts, message name, tool calls and function arguments, and tool-call IDs. Keep legacy SPI-based methods until all consumers have an explicit compatibility migration.
- Do not remove SDK API SPI dependencies wholesale based on this one change. Audit every public signature and consumer first; this migration only removes the SPI message type from the typed text-generation request.


## 11. Triad ecosystem vision: AQLI, Aljabr, and Tafkir

The strategic product direction is documented in `docs/architecture/TRIAD_ECOSYSTEM_VISION.md`. Treat it as the product-level vision alongside the detailed ownership and SDK-first architecture documents.

- The triad is intended to provide a coherent Java-first AI/ML ecosystem and a practical alternative to the Python AI/ML toolchain across framework development, model ecosystems, training/adaptation, inference, and production serving. This is a capability-coverage goal, not a claim of complete PyTorch, Transformers, or scikit-learn parity.
- **Aljabr** is the independently usable general ML/compute framework: tensors, operators, autograd/optimization where implemented, model topology/execution, formats, tokenizers, backends, and FFM/native foundations.
- **Tafkir** owns training, fine-tuning, adaptation, and evaluation workflows built on Aljabr. It must not become an AQLI training mode or a mandatory AQLI dependency.
- **AQLI** owns inference, model lifecycle, scheduling, deployment, serving, streaming, CLI/SDK/API, and production operations on top of Aljabr.
- Preserve independent usability: Aljabr without AQLI; Tafkir on Aljabr without AQLI internals; AQLI inference without Tafkir.
- Support multiple entry points over the same Java APIs and artifacts: embedded libraries, Java SDK, CLI, JBang/scripts, JShell/Jupyter workflows, and AQLI production runtime/service.
- Do not make CLI, notebook, REST, or gRPC the exclusive owner of a capability. They are entry points/adapters over the owning typed API and shared application behavior.
- Capability coverage must be tracked honestly by owner, API/module, task, model/format/backend, maturity, tests, examples, and known limitations. Do not claim blanket parity with PyTorch, Hugging Face Transformers, or scikit-learn without a published feature matrix.
- Keep framework-generic ML utilities in Aljabr only when they belong to its reusable framework scope; training workflow orchestration belongs in Tafkir; serving and production policy belongs in AQLI.
- Keep public APIs, artifacts, examples, and documentation independently consumable through published dependencies; do not rely exclusively on sibling checkouts or composite builds.

## 12. Proportional design, reuse, and maintainability

Apply SOLID as practical design guidance, not as a requirement to create an interface, factory, manager, registry, or module for every class.

### Responsibility and cohesion

- Give each class one cohesive reason to change. A class may coordinate a use case, but it must not also own unrelated parsing, persistence, provider selection, transport formatting, and resource lifecycle.
- Keep methods focused and name them after observable behavior. Extract a method or collaborator when it clarifies a real responsibility, makes a policy independently testable, or removes meaningful duplication.
- Treat very large classes (roughly 500+ lines) and methods (roughly 80+ lines) as review signals, not automatic refactoring rules. First identify cohesive seams and callers; do not split code merely to satisfy a line count.
- Avoid god classes, god packages, generic dumping grounds, and deeply nested implementation types that make ownership hard to discover.
- Organize packages primarily around capability and domain responsibility. Use subpackages for meaningful internal boundaries; do not create a new package or Gradle module for every class.

### SOLID, abstraction, and dependency direction

- **SRP:** separate policies and lifecycle responsibilities that change for different reasons.
- **OCP:** use an extension point when new providers, model families, transports, or policies are expected to vary independently; do not pre-build speculative plugin systems.
- **LSP:** implementations of a contract must preserve its documented preconditions, postconditions, error semantics, cancellation, streaming, and resource ownership.
- **ISP:** keep contracts small and capability-specific. Do not force implementations to depend on unrelated methods or modality-specific fields.
- **DIP:** depend on stable, owner-defined contracts at genuine boundaries. Keep concrete providers and frameworks behind adapters.
- Introduce an interface when it represents a meaningful boundary, multiple implementations, an external extension point, or a useful test seam. Do not add interfaces solely to mock a class or mirror its name.
- Prefer constructor injection and explicit dependencies. Avoid service locators, global mutable state, hidden fallback behavior, and dependency cycles.
- Keep abstractions proportional to demonstrated complexity. Prefer a small cohesive implementation over a generic framework with one implementation and no concrete extension requirement.

### Reuse and duplication

- Search for existing behavior, contracts, utilities, and provider integrations before implementing another version.
- Keep one canonical implementation for behavior with the same semantics. Make adapters delegate or translate at the boundary instead of copying business rules.
- Do not consolidate code merely because names or syntax look similar. Preserve separate implementations when their domain semantics, lifecycle, provider constraints, or performance requirements genuinely differ.
- When duplicate or near-duplicate code is found, compare behavior, callers, tests, configuration, service registrations, reflection/CDI wiring, and compatibility requirements before choosing the canonical owner.
- Avoid parallel request/response types that represent the same use case. If a compatibility type must remain, document its canonical replacement, consumers, deprecation state, and removal condition.
- Prefer domain-specific names over `Common`, `Utils`, `Helper`, `Manager`, or ambiguous suffixes. Do not rename a type solely to avoid a same-name type in another bounded context.

### Dead, excluded, experimental, and generated code

- Do not assume that a tracked source file is compiled, packaged, registered, or used. Inspect Gradle source sets, project inclusion, dependency scopes, service files, CDI/reflection registration, resources, and runtime entry points.
- Every source or test exclusion must have a concrete reason and an owner. Prefer fixing or migrating stale tests; if temporary exclusion is necessary, record the reason, tracking issue or migration task, and a review/removal condition.
- Do not leave large uncompiled source trees or disabled tests as undocumented alternate implementations. Move genuine experiments to an explicitly named experimental area or remove them when their consumers and migration needs are resolved.
- Before deleting code, search all source and test references, build scripts, service registrations, configuration, documentation, generated metadata, and reflective use. Confirm that no public or compatibility contract is being removed accidentally.
- Keep generated sources, local experiments, benchmark scratch files, dependency dumps, patches, and migration artifacts out of production packages and the repository root unless they are intentionally maintained and documented.

### Runtime correctness and API experience

- Make ownership and closure of files, buffers, tensors, native arenas, sessions, threads, and executors explicit. Use structured cleanup and deterministic shutdown.
- Preserve cancellation, deadlines, backpressure, stream ordering, completion/failure signaling, and cleanup semantics when extracting or reusing asynchronous code.
- Do not swallow exceptions or catch `Throwable` for ordinary recovery. Preserve causes and classify errors at the boundary that can provide useful context.
- Errors exposed to users should identify the failed operation, relevant model/provider/configuration context where safe, and a practical next action. Never expose secrets, tokens, or sensitive local paths unnecessarily.
- Keep process exit, terminal rendering, and interactive behavior at the CLI boundary. Reusable services must not call `System.exit` or print directly to a terminal.
- Keep defaults and validation consistent across equivalent CLI, SDK, REST, and gRPC requests by mapping them to the same application contract.

### Tests and documentation

- Add tests at the layer that owns the behavior. Cover contract semantics, failure paths, resource cleanup, concurrency, cancellation, and adapter mapping where relevant.
- When consolidating implementations, add regression tests before deleting the old path. When changing a public contract, test compatibility and every supported adapter.
- Keep examples executable and aligned with current published APIs and Gradle coordinates. Label proposed, experimental, optional, deprecated, and production-supported features accurately.
- Update the owning module README and architecture/ownership documentation when a public capability, dependency boundary, module placement, or supported workflow changes. Keep the root README focused on the product entry point.
- Distinguish current implementation from target architecture and migration intent. A roadmap or architecture rule is not evidence that a capability is already implemented.

### Refactoring acceptance checklist

Before completing a structural refactor, verify:

1. The class and module have one clear owner and responsibility.
2. Existing contracts and implementations were reused where semantics match.
3. Dependency direction remains acyclic and adapters do not own reusable business logic.
4. All callers, tests, resources, service registrations, reflection metadata, and docs use the canonical path.
5. No unexplained source/test exclusions or stale copies were introduced.
6. Focused tests and affected compile/build checks were run; report anything not run.
7. The change is the smallest reviewable step that improves maintainability without degrading user-visible behavior.

