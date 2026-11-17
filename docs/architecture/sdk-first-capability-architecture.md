# SDK-First Capability Architecture

## Status

Proposed architecture baseline for the incremental decomposition of `RunCommand`.
This document establishes dependency and contract rules; it does not require creating
all proposed modules in one change.

## Decision

AQLI uses a transport-neutral SDK contract and a shared application execution path.
CLI, REST, and gRPC are adapters over that path. They must not independently implement
inference semantics, validation, defaults, routing, streaming lifecycle, or cancellation.

The SDK is the canonical public API contract. Application services are the single
source of execution behavior. These are related but deliberately distinct responsibilities.

## Dependency direction

```text
CLI / REST / gRPC adapters
          |
          v
      AQLI SDK API
          |
          v
 Application API / services
          |
          +---- Text capability API
          +---- Audio capability API
          +---- Image capability API
          +---- Video capability API
          +---- Spatial capability API
          |
          v
 Runtime / model runners / Aljabr backends
```

Rules:

1. SDK API contains transport-neutral public request, response, capability, and streaming contracts.
2. Application services own orchestration, validation, defaults, capability dispatch, and execution lifecycle.
3. Capability API modules own capability-specific domain requests, responses, options, and contracts.
4. Capability API modules must not depend on Picocli, REST frameworks, gRPC, or concrete model runners.
5. CLI owns argument parsing, terminal interaction, and mapping from CLI options to SDK requests.
6. REST and gRPC own wire serialization and protocol concerns only.
7. Runtime and runner modules own model loading and execution; they do not own CLI or wire-protocol concerns.
8. Aljabr remains the computation/framework layer and is not coupled to AQLI transports.

## Canonical request model

Do not grow one flat request object with every modality-specific field.
Use a common request envelope for cross-cutting concerns (request identity, model
selection, session, streaming intent, common generation controls, metadata, and
execution hints) plus typed capability task payloads.

Capability-specific examples:

- Text: sampling controls, stop conditions, grammar, structured output.
- Audio: task (transcription, speech generation, transformation), input/output audio,
  voice, codec, sample rate, channels, bitrate, chunking, and audio streaming controls.
- Image: source images, dimensions, output count, image format, and image-generation controls.
- Video: frame inputs, timestamps, frame sampling, and output encoding.
- Spatial: point clouds, meshes, coordinate systems, geometry formats, and generation controls.
- Embedding: input collection, pooling, normalization, and output representation.

A request may contain multiple compatible task payloads. Multimodal requests must not
be artificially constrained to exactly one modality.

Avoid untyped string parameter maps as the long-term public contract. They may remain
as a compatibility bridge during migration, but each key must have an owner, documented
semantics, validation, and a removal/migration plan.

## Capability module structure

Introduce modules incrementally, only when a capability has a stable contract and
more than one meaningful consumer or implementation.

Target pattern:

```text
capabilities/<capability>/
  aqli-<capability>-api/
  aqli-<capability>-core/       # only when shared orchestration is needed
  aqli-<capability>-<runner>/   # concrete runner integration, when appropriate
```

The CLI may have capability-specific option/mapping packages or modules. Keep
Picocli annotations out of capability API modules. Do not create a separate CLI
module for every capability until independent packaging or dependency isolation
provides real value; packages are the default first step.

## Transport parity

For equivalent semantic inputs, CLI, REST, and gRPC must reach equivalent normalized
application requests and produce equivalent outcomes.

Conformance tests must cover:

- defaults and validation;
- model and capability selection;
- errors and error classification;
- streaming event ordering, completion, and failure;
- cancellation and resource cleanup;
- metadata and usage reporting;
- multimodal input/output behavior.

Compare normalized application requests, not raw CLI arguments, HTTP JSON, and protobuf
wire messages. Protocol-specific representations may differ while their semantics remain
the same.

## Migration constraints

- Preserve existing CLI behavior unless an intentional behavior change is separately
  specified and tested.
- Keep existing REST/gRPC wire compatibility where feasible; version breaking changes.
- Do not introduce a second generic generation request model that permanently duplicates
  the existing inference request model.
- Audit the current SDK `AqliClient.GenerationRequest`, SPI `InferenceRequest`, and
  application contracts before deciding which types become canonical and how adapters
  preserve compatibility.
- Do not move execution behavior into the SDK API artifact.
- Keep each extraction independently reviewable and testable.
- Do not make runtime/backend changes as part of transport-contract refactoring unless
  required by a proven contract gap.

## Initial decomposition sequence

1. Audit the existing SDK public facades, SDK implementation, application operations,
   internal SPI requests, REST resources, gRPC services, and runtime engine catalog.
2. Record the canonical public contract and compatibility strategy. Keep the current
   internal `InferenceRequest` as an execution SPI during migration; do not expose it
   as the final typed capability request.
3. Define the SDK capability discovery/registration boundary and shared invocation
   semantics by reusing existing contracts. Specify sync/async, streaming, cancellation,
   deadlines, validation, defaults, and error categories before choosing new interfaces.
4. Connect one shared application execution path to the SDK capability contract.
5. Adapt CLI, REST, and gRPC to construct the same canonical typed requests and invoke
   that shared path. Keep the CLI run registry limited to Picocli contributions.
6. Add normalized-request and cross-transport conformance tests before moving existing
   command behavior.
7. Extract the text CLI options and mapping into a CLI adapter contribution, preserving
   all current aliases, defaults, validation, and SDK behavior.
8. Prove the boundary with an additional capability (audio or image) before expanding
   to video, spatial, and other capabilities.
9. Reduce `RunCommand` to a thin command adapter and coordinator.

## Acceptance criteria

The architecture is considered established when:

- CLI, REST, and gRPC invoke the same application services;
- each capability's public parameters have one typed definition and validation owner;
- transport adapters contain no duplicate inference orchestration;
- capability APIs are usable without CLI, REST, or gRPC dependencies;
- parity tests demonstrate equivalent semantics across all enabled transports.


## Current contract audit (2026-11-14)

The initial source inspection found two distinct request models:

- `AqliClient.GenerationRequest` in `framework/sdk/aqli-sdk-api` is a small text-only public request (prompt, max tokens, temperature, top-p, stop tokens).
- `InferenceRequest` in `framework/spi/aqli-spi-inference` is the richer internal execution request. It carries messages, tools, streaming, provider/plugin selection, request context, metadata, attachments, and a generic parameter map. It also contains execution/runtime-oriented concerns, including a deprecated native `MemorySegment` context handle.

The SDK API Gradle module currently exports dependencies on both `spi:aqli-spi` and `spi:aqli-spi-inference` (as well as the multimodal SPI). This means the current public SDK boundary is not yet fully independent of the internal SPI boundary.

### Contract decision to make before extraction

Do not simply rename `InferenceRequest` as the SDK request. It is currently an internal execution envelope with compatibility and runtime concerns, and its generic parameter map is not a suitable long-term typed capability API.

Instead:

1. Define a transport-neutral SDK request envelope with stable public semantics.
2. Define typed capability task payloads in capability API modules and compose them for multimodal requests.
3. Keep `InferenceRequest` as an internal execution SPI model during migration.
4. Implement one application-layer mapper from SDK requests to the internal SPI request. All transports must call this same mapper/application service.
5. Keep compatibility adapters for existing SDK callers and existing REST/gRPC wire contracts while migrating.
6. Remove the SDK API's direct SPI exposure only after auditing every public SDK type and adapter dependency; do not remove those Gradle dependencies speculatively.

This gives the SDK a genuine single-source-of-truth role without leaking runner/runtime implementation details into its public contract.

## Provider-specific lifecycle capabilities

Provider-specific options must not be added to a generic lifecycle request merely because
one transport currently needs them. The Hugging Face pull path is the first concrete
example:

- `ModelPullRequest` remains provider-neutral (model spec, revision, format, force, and
  generic conversion/quantization controls).
- `HuggingFacePullRequest` owns Hugging Face repository options such as include patterns
  and task classification.
  It also carries optional repository architecture and metadata for manifest classification and provenance; the local SDK forwards these fields to the repository.
- `HuggingFaceModelOperations` is the SDK capability contract; the local SDK delegates
  execution to `HuggingFaceRepository`, which remains responsible for downloads and
  manifest persistence.
- CLI parsing and terminal progress stay in the CLI, but the command maps to the typed
  SDK capability instead of invoking the repository directly.
- REST maps `format`, `includePatterns`, and `taskType` into the same typed capability; gRPC maps `format`, `include_patterns`, and `task_type` into it. The gRPC additions use new protobuf field numbers, preserving existing field numbers and wire compatibility.
- Non-Hugging-Face REST/gRPC pulls continue through the provider-neutral `ModelPullRequest` contract.
- Both transports delegate execution to the SDK. REST's `BackgroundJobManager` only owns job lifecycle/progress storage; gRPC only owns stream adaptation and transport error mapping.
- CLI, REST, and gRPC therefore share the same `HuggingFaceModelOperations` capability and the same `LocalAqliSdk` repository delegation.

The capability result returns an opaque artifact URI rather than a local `Path`, keeping
the SDK contract usable by remote implementations. A local runtime may return a `file:` URI.

### Run-command automatic model preparation

`RunCommand` may still perform CLI-specific local-index checks and display pull progress, but it must not call `HuggingFaceRepository` directly. When its compatibility fallback needs to download a Hugging Face model, it maps the repository ID, revision, and preferred format into `HuggingFacePullRequest` and invokes `AqliSdk`/`HuggingFaceModelOperations`. The local SDK remains the adapter that delegates to the repository. This keeps explicit `aqli pull`, automatic `aqli run` preparation, REST, and gRPC on the same provider-owned download implementation.



## CLI capability extension SPI

The `aqli run` command supports an extensible CLI contribution boundary separate from
the SDK capability API. The initial module is `:cli:aqli-cli-spi`.

A provider is registered with the Java service provider mechanism at
`META-INF/services/tech.kayys.aqli.cli.spi.run.RunCapabilityProvider`. Each provider
creates one `RunCapability`, whose stable ID must match the provider's declared ID.
The ServiceLoader registry validates IDs eagerly, rejects duplicates and mismatches,
and exposes capabilities in deterministic ID order.

A CLI capability may contribute Picocli options to the run command and validate the
parsed invocation. This Picocli dependency is confined to the CLI adapter extension
boundary. It must not leak into SDK capability modules or REST/gRPC contracts.

Request mapping is intentionally not represented as a generic map or a second
`RunRequest` envelope in this first increment. The next capability extraction must map
to the canonical typed SDK capability payload and shared application service once those
contracts are established. The registry remains discovery infrastructure; backend
selection and execution stay in runtime.

The base CLI should not depend on every optional capability provider. Distribution
assembly decides which provider modules are present, allowing third-party extensions
without edits to `RunCommand`.


## Registry ownership and capability lifecycle

AQLI has multiple registries/catalogs because they resolve different kinds of
extension points. They must not be collapsed into one registry.

| Boundary | Owns | Must not own |
| --- | --- | --- |
| SDK capability registry | Discovery and resolution of public SDK capability implementations | Picocli options, HTTP/protobuf DTOs, backend selection |
| CLI run capability registry | Picocli option contributions, CLI validation, CLI-to-SDK request mapping | Canonical capability contracts, inference execution, backend selection |
| Runtime engine catalog | Engine/backend discovery and selection for execution | Public capability semantics or transport parsing |
| REST/gRPC adapters | Protocol-specific endpoint/service wiring and wire mapping | Independent validation, defaults, dispatch, or execution semantics |

The lifecycle for adding a capability is:

1. Define its typed, transport-neutral SDK request/response contract and capability
   descriptor, reusing existing domain types where appropriate.
2. Define the shared application invocation and validation path for that capability.
3. Register the SDK implementation with the SDK capability registry.
4. Add optional transport adapters: CLI contribution, REST mapping, and gRPC mapping.
5. Connect runtime engine/backend implementations through the runtime engine catalog.
6. Verify all transports normalize to the same application request and observe the same
   execution, streaming, cancellation, and error behavior.

The SDK capability is independently usable without the CLI artifact. A CLI contribution
is optional packaging and must resolve the corresponding SDK capability by stable ID;
it is not the capability implementation itself.

## Existing implementation audit (2026-11-17)

The current codebase contains useful building blocks but does not yet implement the
complete SDK-first capability registry described above:

- `framework/sdk/aqli-sdk-api` exposes an `AqliSdk` facade whose primary completion
  method accepts the internal SPI `InferenceRequest`. It also exposes Aljabr multimodal
  SPI types and embedding SPI types. This is a compatibility surface, not yet the final
  typed AQLI capability API.
- `framework/sdk/aqli-sdk/src/main/java/tech/kayys/aqli/sdk/api/AqliClient.java`
  exposes a separate, smaller text-oriented public client contract and its own
  `GenerationRequest`. It must be reconciled with the broader SDK facade rather than
  treated as a second long-term canonical request model.
- `framework/sdk/aqli-sdk-core` contains `InferenceOperations`, `AqliChatService`,
  `TextGenerationRequestMapper`, and `RequestValidator`. These are existing
  application/SDK execution building blocks to audit and evolve before introducing
  parallel capability invocation interfaces.
- The internal SPI `InferenceRequest` contains runtime/execution context, a generic
  parameter map, and a deprecated native context handle. Keep it as an internal
  execution contract during migration; map canonical SDK requests to it in one shared
  application boundary.
- `:cli:aqli-cli-spi` provides `RunCapability`, `RunCapabilityProvider`, and
  `RunCapabilityRegistry` for Picocli contributions. It is intentionally not an SDK
  capability registry and must remain independent of canonical SDK semantics.
- The runtime engine catalog remains responsible for execution engine/backend selection.

Consequently, do not add a speculative generic `AqliCapability<R, S>` or a second
invocation envelope as the next code change. First document and test the canonical
request/response and invocation semantics against the existing SDK and application
contracts. The current CLI registry can then be connected to that stable SDK surface.
