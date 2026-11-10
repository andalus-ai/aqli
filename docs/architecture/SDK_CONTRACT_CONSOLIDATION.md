# SDK Contract Consolidation

**Status:** Proposed architecture decision; implementation must follow in bounded migrations.  
**Baseline:** AQLI `main`, 2026-11-10.

## Decision

AQLI will expose one canonical public Java SDK contract. The SDK API is defined once, in one API module, and every supported connection style implements that contract.

The canonical public entry point is **`tech.kayys.aqli.sdk.api.AqliClient`**, now housed in the dedicated `sdk:aqli-sdk-api` module. The older `tech.kayys.aqli.sdk.AqliClient` in the facade module remains a legacy surface to be migrated or deprecated after its consumers are audited. There must not be parallel public `AqliClient` or `AqliSdk` interfaces with different method sets in separate packages or modules.

The SDK contract is a client-facing API. Server resources, server-side runtime composition, CLI presentation, and transport implementations are not alternate definitions of the SDK.

## Current source-tree findings

The inspected default-branch tree has these SDK-related modules:

- `framework/sdk/aqli-sdk`
- `framework/sdk/aqli-sdk-core`
- `framework/sdk/aqli-sdk-remote`
- `runtime/sdk/aqli-sdk-local`
- `framework/sdk/aqli-sdk-protobuf` is referenced by settings/build files but is absent from the inspected tree.
- `framework/sdk/aqli-sdk-api` is referenced by settings/build files and by both SDK and SDK Core build files, but is absent from the inspected tree.

The root settings conditionally include the missing API and protobuf projects, while the SDK build scripts directly depend on them. This inconsistency must be resolved before relying on a clean Gradle module graph.

The current SDK source contains multiple overlapping public contracts:

- `tech.kayys.aqli.sdk.AqliClient` — request/response generation, embedding, model listing and a nested builder.
- `tech.kayys.aqli.sdk.api.AqliClient` — another generation API, with its own nested request/result/stream/feature types.
- `tech.kayys.aqli.sdk.api.AqliSdk` — a broader inference/model/job API.
- `tech.kayys.aqli.sdk.core.AqliSdk` — another broader inference/model/job API consumed by the server's `SdkProvider`.

There are also two unrelated `DeploymentMode` enums:

- `sdk.api.DeploymentMode`: `EMBEDDED`, `REST`, `GRPC`, `CLI`; these values describe client connection/transport selection, not deployment topology.
- `sdk.core.DeploymentMode`: `STANDALONE`, `MICROSERVICE`, `HYBRID`; these values describe runtime deployment topology.

Transport selection and deployment topology are different concepts and must not share an ambiguous type name.

The main SDK module also contains REST, gRPC, embedded, CLI, and remote client implementations/factories. This mixes the public contract, client orchestration, and optional transport adapters. The local SDK module already documents itself as the runtime composition boundary, but currently depends on both SDK and SDK Core and on all local runner integrations.

The actual CLI module in the inspected tree is `cli/aqli-cli`; the `ui/aqli-cli` and `ui/aqli-api` paths referenced by optional settings are absent. The server API module is `framework/api/aqli-api`. Do not create a second `cli/api` layer merely to match a proposed directory layout.

## Target module ownership

| Module | Target responsibility | Must not own |
|---|---|---|
| `sdk:aqli-sdk-api` | The only public SDK contract: `AqliClient`, builder/configuration types, client-facing capability abstractions and stable exceptions. Uses canonical Aljabr inference/model contracts wherever those are the shared concepts. | HTTP resources, transport implementations, local engine wiring, duplicated inference DTOs |
| `sdk:aqli-sdk-core` | Transport-neutral client orchestration, validation, retry policy, shared mapping and lifecycle behavior. Implements the public API only where it is transport-neutral. | A second public SDK interface or model of deployment topology |
| `sdk:aqli-sdk-remote` | Optional remote adapters, separated by protocol where justified (HTTP/REST and gRPC). Implements the same `AqliClient`. | A separate client API, CLI process transport as a core SDK concern |
| `sdk:aqli-sdk-local` | In-process runtime composition and adapters over AQLI runtime/runner services. Implements the same `AqliClient`. | Public contract definitions or duplicated model DTOs |
| `sdk:aqli-sdk` | Optional convenience/facade artifact and dependency bundle for the default supported SDK experience. It should not contain another client implementation or contract. | Competing API definitions, hidden reflective discovery, all transports in one mandatory dependency |
| `framework/api/aqli-api` | Server-side REST/OpenAI-compatible resources, server DTOs, authentication, HTTP mapping and server lifecycle. | Client SDK interfaces, client-side builders or transport implementations |
| `cli/aqli-cli` | Terminal commands, parsing, formatting and command orchestration. Uses runtime/application services or the SDK where appropriate. | SDK contract definitions or server API DTO ownership |

The API module should be dependency-light and usable without Quarkus, runner implementations, local model repositories, native bindings, or gRPC/HTTP client libraries. Transport adapters depend on the API module, never the reverse.

The SDK API must use Aljabr's canonical framework contracts for generic inference requests, responses, stream chunks, messages, tools, attachments and model metadata where those are already owned by Aljabr. AQLI-specific client configuration, request conveniences, and runtime operations may remain AQLI-owned when they represent genuinely different responsibilities.

## Canonical naming

| Existing name | Target name / action | Reason |
|---|---|---|
| `tech.kayys.aqli.sdk.AqliClient` | Canonical `tech.kayys.aqli.sdk.AqliClient` | Retain the clearest public entry point and migrate its API surface into the API module |
| `tech.kayys.aqli.sdk.api.AqliClient` | Remove after compatibility migration | Duplicate public client abstraction and nested DTO family |
| `tech.kayys.aqli.sdk.api.AqliSdk` | Remove after its unique operations are classified and migrated | "SDK" names a distribution/developer experience, not a second client interface |
| `tech.kayys.aqli.sdk.core.AqliSdk` | Replace with internal runtime/application service interfaces or migrate client-facing operations to canonical `AqliClient` | Currently conflates inference, model management, jobs, and server provider concerns |
| `sdk.api.DeploymentMode` | `ClientTransport` or `ConnectionMode` | EMBEDDED/REST/GRPC are connection strategies; CLI should be an explicit process adapter, not a deployment mode |
| `sdk.core.DeploymentMode` | `RuntimeTopology` (only if still needed) | STANDALONE/MICROSERVICE/HYBRID describe topology, not client transport |
| `AqliClientFactory` | `AqliClientProvider` or protocol-specific factories | Factory should not switch among unrelated transport and runtime concerns |
| `RemoteAqliClient` / `RestAqliClient` | One clearly named REST adapter implementation | Avoid two layers with indistinguishable remote/REST responsibilities |
| `GrpcAqliClient` | gRPC adapter implementation | Protocol-specific implementation of the same contract |
| `EmbeddedAqliClient` / `LocalAqliClient` | One local adapter implementation | Avoid duplicate names for the same in-process mode |
| `CliAqliClient` | Move to an explicit CLI/process adapter only if there is a real SDK use case | CLI is not a deployment mode and must not leak into the core API |

Do not mechanically rename types before auditing callers. For public types, provide deprecated forwarding interfaces/builders or a documented breaking release according to the repository's release policy. Avoid maintaining two independently evolving contracts during the transition.

## API shape principles

1. One public `AqliClient` and one builder/configuration model.
2. The client is model-format-neutral. GGUF, Safetensors, ONNX, LiteRT and future formats are selected by model resolution/runtime policy, not by separate client interfaces or format-specific client methods.
3. Common inference request/response/stream types come from Aljabr's canonical contracts. Avoid a second generation request/result hierarchy when it merely wraps the same concepts.
4. Capabilities are queried through a stable capability model; do not expose backend implementation classes or format names as the primary API.
5. Sync, async and streaming methods have consistent semantics and cancellation/resource ownership.
6. Model catalog/resolution and operational job APIs should be grouped as cohesive client capabilities, not forced into a single oversized interface. Use narrowly scoped sub-interfaces composed by the canonical client only where consumers need them.
7. Transport and local runtime selection are configuration/provider concerns. They do not alter the public request/response contract.
8. The public API must not require Quarkus CDI or reflection-based class-name discovery. Explicit providers/service loading or explicit dependency composition should make missing optional transports fail clearly.
9. Client close semantics must be deterministic and documented, including ownership of HTTP/gRPC channels and local runtime resources.
10. Keep server-side HTTP DTOs separate when their serialization/schema contract is intentionally OpenAPI-specific; map to canonical inference contracts at the resource boundary.

## Migration sequence

### Phase 1 — Establish a real API module and dependency graph

- [x] Create `framework/sdk/aqli-sdk-api` with a dependency-light `java-library` build and the public `sdk.api.AqliClient` contract.
- [x] Add the `AqliClientProvider` service-provider boundary so the API module does not instantiate implementation classes reflectively.
- [x] Register the facade module's provider implementation and expose the API dependency transitively from `aqli-sdk`.
- [x] Move the `sdk.api.DeploymentMode` compatibility enum to the API module and introduce the correctly named `ClientTransport` selection.
- [x] Remove the placeholder `sdk.api.AqliClientImpl` that returned synthetic inference results.
- Reconcile `settings.gradle.kts` with directories that actually exist.
- Create/restore `framework/sdk/aqli-sdk-api` as the only home for public SDK types.
- Move the canonical `AqliClient` contract and its builder/configuration/capability contracts into that module.
- Ensure API has no Quarkus, runner, HTTP client, gRPC, or local runtime dependencies.
- Fix all SDK module build dependencies to point to existing modules.

**Acceptance:** clean Gradle configuration; API module compiles in isolation; dependency report proves no transport/runtime implementation leaks into API.

### Phase 2 — Classify and consolidate contracts

- Compare all four current public interfaces method-by-method, including request/response types, exceptions, sync/async/streaming semantics and lifecycle.
- Map generic inference and model data to Aljabr canonical contracts.
- Classify each unique operation as client API, AQLI runtime service, or server-only operation.
- Remove format-specific convenience methods from the canonical API unless they represent a genuinely useful format-neutral capability.
- Resolve the two deployment enums into distinct, correctly named concepts or remove them if configuration/provider selection makes them unnecessary.

**Acceptance:** one public interface definition; no duplicate public request/result/stream model; tests cover behavior formerly exposed by each interface.

### Phase 3 — Move implementations behind adapters

- Keep shared client behavior in SDK Core.
- Move REST/gRPC code to optional remote transport adapters.
- Keep local engine composition in SDK Local.
- Make each adapter implement the same API contract.
- Remove CLI subprocess transport from generic SDK composition unless a concrete supported use case requires it.

**Acceptance:** each adapter passes the same contract test suite; API-only consumer compiles without transport dependencies; local SDK does not depend on CLI.

### Phase 4 — Server and CLI boundaries

- Replace `SdkProvider`'s dependency on the legacy `sdk.core.AqliSdk` with a server-owned runtime/application service or canonical client where that is semantically correct.
- Keep demo fallback behavior explicit and test-only or clearly marked; do not hide missing runtime wiring behind a production dummy implementation.
- Keep REST resources and OpenAPI DTOs in `framework/api/aqli-api`.
- Audit CLI dependencies and remove direct SDK implementation/transport wiring where the local runtime composition service already owns it.

**Acceptance:** server API depends on runtime/application ports, not duplicate SDK interfaces; CLI remains a presentation adapter; no dependency cycles.

### Phase 5 — Compatibility and publication

- Publish the API artifact first, then Core and optional adapters, then the convenience SDK bundle.
- Add a compile-time compatibility fixture for the supported public API.
- Update README, examples, package docs and Maven coordinates.
- Add architecture checks preventing duplicate public SDK contracts and forbidden API-to-implementation dependencies.

**Acceptance:** clean consumer project resolves published artifacts; compatibility/deprecation policy is documented; full relevant CI passes.

## Explicit non-goals

- Do not merge server REST resources with the client SDK API.
- Do not make all transports mandatory dependencies of the SDK API or convenience bundle.
- Do not move runtime model repositories, runner selection, scheduling or deployment policy into the public SDK.
- Do not combine GGUF/Safetensors/ONNX/LiteRT into a format-specific client API.
- Do not mass-delete classes based on duplicate names without comparing behavior and call sites.
- Do not make CLI a required SDK transport.

## Validation checklist

- [ ] Root Gradle settings and all module paths agree.
- [ ] API module compiles independently.
- [ ] API dependency graph excludes Quarkus, runners, transports and local runtime.
- [ ] Exactly one canonical public `AqliClient` is defined.
- [ ] No parallel public `AqliSdk` interface remains after migration.
- [ ] Shared model/inference contracts use Aljabr canonical types.
- [ ] REST, gRPC and local adapters pass shared contract tests.
- [ ] Server API and CLI have no reverse dependency on SDK implementation internals.
- [ ] Compatibility fixture and published-artifact consumer compile.
- [ ] Relevant module tests and CI pass.


## Additional findings from implementation inspection

The duplicate SDKs also contain overlapping provider-discovery systems:

- `sdk.api.AqliSdkBuilder` discovers `sdk.api.AqliSdkProvider` through `ServiceLoader` and selects by provider name/priority.
- `sdk.core.AqliSdkProvider` defines a second provider SPI with a separate `Mode.LOCAL/REMOTE` and `SdkConfig`.
- `AqliSdkFactory` discovers the second SPI, while `AqliSdkBuilder` discovers the first. These are separate registries and should be consolidated into one provider mechanism after their actual consumers and service descriptors are inventoried.

The old `sdk.api.AqliClientImpl` is not a valid production implementation: the inspected implementation returns placeholder generation text and synthetic embeddings rather than invoking a runner or remote service. It must not become the canonical implementation merely because it implements the duplicate `AqliClient` interface. Keep it only as a clearly isolated test/demo fixture if existing tests need it; otherwise remove it after consumer audit.

The server's `SdkProvider` consumes `sdk.core.AqliSdk`, and `LocalAqliSdk` implements it. The local implementation explicitly reports several operations as unsupported because the corresponding production runtime services are not composed. This confirms that the broad `AqliSdk` interface currently combines inference, embeddings, jobs, model catalog/registration, system information, plugin discovery and operational metrics. Those capabilities should be split into cohesive runtime/application ports; the public `AqliClient` should compose only the capabilities intended for SDK consumers. Do not expose unsupported operations as if all local and remote providers implement them.

The two builder/provider systems also differ semantically: one selects a generic provider by name/priority; the other selects LOCAL/REMOTE mode and uses a different priority convention. Consolidation must preserve explicit provider selection, deterministic fallback behavior, and clear errors for missing optional providers. Do not merge them by simply renaming one interface.


## Phase 1 implementation notes

The first source migration establishes `sdk:aqli-sdk-api` and moves the `tech.kayys.aqli.sdk.api.AqliClient` contract into it. The API's static builder now resolves an optional `AqliClientProvider` using `ServiceLoader`; it no longer constructs an implementation class directly or falls back to the synthetic `AqliClientImpl`.

The `aqli-sdk` facade registers the provider and implements the builder by delegating to the existing transport factory. The public builder adds `transport(ClientTransport)`; the old `mode(DeploymentMode)` remains deprecated for source compatibility. `aqli-sdk` exposes the API module as an `api` dependency, while SDK Core no longer depends on the client API module.

This is deliberately an incremental migration, not the completed consolidation: the older root-package `tech.kayys.aqli.sdk.AqliClient`, the two `AqliSdk` interfaces, provider-discovery duplication for those interfaces, and server-side `SdkProvider` still require a call-site and behavior audit before removal.
