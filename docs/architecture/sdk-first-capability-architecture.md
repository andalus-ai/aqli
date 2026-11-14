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

1. Inventory existing SDK, SPI, application, REST, and gRPC request/response contracts.
2. Decide the canonical request envelope and compatibility strategy.
3. Extract request assembly from `RunCommand` into application/capability mappers.
4. Move CLI-only option parsing into CLI option groups and mapping adapters.
5. Add transport conformance tests against the shared application boundary.
6. Introduce capability API modules incrementally, starting with text and audio, then
   image, and later video/spatial as their contracts mature.
7. Reduce `RunCommand` to a thin command adapter and coordinator.

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
