# GGUF Format and Execution Provider Boundary

## Decision

GGUF is a model artifact format, not an execution provider. AQLI must not make llama.cpp a mandatory dependency of the GGUF runner or identify a provider simply as `gguf`.

The Java-native GGUF implementation is the preferred/default provider as it reaches the required correctness, feature, and performance gates. llama.cpp remains an optional compatibility provider that users can add explicitly. It must be possible to run AQLI with GGUF format support and Java-native execution while the llama.cpp modules and native libraries are completely absent from the runtime classpath.

This is an architectural target and migration direction, not a claim that every Java-native model/feature path is already production-ready.

## Current repository evidence

### AQLI

- `runtime/runner/gguf/aqli-runner-gguf` contains the full `tech.kayys.aqli.inference.llamacpp` package, including `LlamaCppEngine`, binding/session/configuration/loader/sampler/LoRA classes.
- Its Gradle dependencies include Aljabr `aljabr-gguf-llamacpp`, so a module named `aqli-runner-gguf` currently brings in a concrete native execution provider.
- `LlamaCppEngine.providerId()` returns `gguf`, conflating format with provider identity.
- `aqli-plugin-runner-gguf` is also llama.cpp-specific: its plugin is named `LlamaCppRunnerPlugin`, depends on the current GGUF runner, and advertises `llama.cpp` in its metadata.
- The plugin wrapper currently has incomplete wiring: it sets its `LlamaCppRunner` field to null and returns placeholder success maps for inference/tokenization/detokenization. It must not be mistaken for the real inference implementation.
- The generic `GgufDaemonProtocol`, request decoder/dispatcher, server loop, and framing streams are explicitly backend-agnostic and should not be moved into a llama.cpp-only module without evidence they are coupled to it.
- The old `aqli-gguf-feature-text` feature module currently depends on both runner and plugin modules; its dependency needs to be re-evaluated once the provider plugin is separated.

### Aljabr

Aljabr already has the right structural direction:

- `:core:aljabr-gguf-api` defines `GgufBackend`, `GgufBackendProvider`, capability/selection contracts, and `GgufInferenceEngine`.
- `:core:aljabr-gguf-core` contains shared GGUF metadata, parsing, quantization, tokenizer, and model infrastructure.
- `:core:aljabr-gguf-java` provides `JavaGgufBackend` and registers `JavaGgufBackendProvider` through `ServiceLoader`.
- `:core:aljabr-gguf-llamacpp` provides `LlamaCppGgufBackend` and a separate `LlamaCppGgufBackendProvider`.

The provider SPI uses stable provider IDs such as `java` and `llamacpp`, availability/probe methods, explicit selection, and ServiceLoader discovery. The engine API has no compile-time dependency on either concrete provider. This is the provider model AQLI should consume rather than creating a second GGUF engine/provider registry.

## Target ownership and module graph

### Aljabr owns

| Module | Responsibility |
|---|---|
| `aljabr-gguf-api` | Canonical GGUF backend/provider SPI, capability probing, selection, generic engine API |
| `aljabr-gguf-core` | GGUF parsing, tensor metadata, quantization layouts, tokenizer/model support shared across providers |
| `aljabr-gguf-java` | Default Java-native GGUF execution provider |
| `aljabr-gguf-llamacpp` | Optional llama.cpp FFM/native execution provider |

### AQLI owns

| Module | Responsibility |
|---|---|
| `aqli-runner-gguf` | Format-level AQLI integration: model resolution/manifest integration, runtime request/session adaptation, GGUF feature discovery and generic daemon protocol only where needed; no concrete native provider dependency |
| `aqli-runner-gguf-llamacpp` (target) | Optional AQLI adapter/configuration/DI integration for the Aljabr llama.cpp provider; depends on the GGUF API and the optional Aljabr llama.cpp provider |
| `aqli-plugin-runner-gguf-llamacpp` (target, if legacy RunnerPlugin SPI is still needed) | Optional legacy runner-plugin bridge; no generic GGUF identity |
| AQLI runtime/provider registry | Selects providers, configures defaults and deployment policy; does not reimplement GGUF tensor execution |

AQLI should consume Aljabr's canonical GGUF API and provider implementation. Do not duplicate `GgufBackendProvider`, `GgufInferenceEngine`, or concrete tensor execution in AQLI.

The exact AQLI module names may be adjusted to the existing Gradle conventions, but the dependency direction is mandatory:

```text
AQLI runtime / model resolution
          |
          v
AQLI GGUF integration (format-level; no llama.cpp dependency)
          |
          v
Aljabr GGUF API + shared GGUF core
          |
          +-----------------------+
          |                       |
          v                       v
Aljabr Java provider       Aljabr llama.cpp provider
(default when ready)       (optional plugin)
```

The Java provider should be available without the llama.cpp artifact on the classpath. The optional llama.cpp provider must be discovered only when installed; its absence must not prevent application startup or Java-provider inference.

## Provider identity and selection

- Format identifier: `gguf`.
- Java-native provider ID: `java` (or a stable explicit alias such as `gguf-java`, if the Aljabr SPI chooses one).
- llama.cpp provider ID: `llamacpp` (or `gguf-llamacpp` at an AQLI-facing adapter boundary). Never use `gguf` as the provider ID.
- Default selection: Java-native provider once its production-readiness gate is met.
- Explicit fallback: users may select llama.cpp when its optional module/native runtime is installed.
- If an explicitly requested provider is unavailable, fail with a clear provider-specific error. Do not silently switch providers when the user explicitly selected one.
- In AUTO mode, provider capability probing may choose an available provider according to a documented priority/policy. Do not equate provider presence with full feature support.

Example future configuration (names illustrative until wired to the canonical selection API):

```properties
aqli.model.format=gguf
aqli.gguf.provider=java
# Optional compatibility provider:
aqli.gguf.provider=llamacpp
```

## Safe migration sequence

1. **Inventory and baseline:** enumerate all classes, tests, CDI producers, service registrations, Gradle dependencies, CLI flags, config keys, packaging/native-image metadata, and downstream modules that reference the existing llama.cpp package. Preserve the current working provider until the replacement wiring is verified.
2. **Create optional module:** introduce `aqli-runner-gguf-llamacpp`; move/copy the AQLI-specific llama.cpp engine, configuration, session orchestration, FFM binding adapters, and provider tests there. Keep the existing module compiling during the transition.
3. **Separate plugin bridge:** rename/rehome the legacy plugin as llama.cpp-specific only if the legacy RunnerPlugin SPI remains required. Do not keep a generic `aqli-plugin-runner-gguf` that silently means llama.cpp.
4. **Remove mandatory dependency:** remove `aljabr-gguf-llamacpp` from the format-level `aqli-runner-gguf`; add it only to the optional llama.cpp module. Ensure the Aljabr Java provider and API are independently resolvable.
5. **Integrate Java provider:** AQLI format integration delegates to Aljabr's `GgufInferenceEngine` / provider SPI. Ensure the default dependency graph includes `aljabr-gguf-java` and excludes `aljabr-gguf-llamacpp`.
6. **Fix provider identity and defaults:** change AQLI's llama.cpp provider ID from `gguf` to `llamacpp`; make Java the default only after correctness, model-family, multimodal, streaming, cancellation, memory, and benchmark gates pass.
7. **Prove optionality:** add dependency-graph and classpath tests proving the default AQLI GGUF distribution has no llama.cpp artifacts/native libraries, plus an integration test proving the optional provider can be added and explicitly selected.
8. **Retire legacy bridge:** only after CLI/API compatibility and deployment packaging are verified, deprecate/remove the old combined module and placeholder legacy plugin.

## Required validation gates

- Java-only build and tests pass without llama.cpp headers, jextract, native libraries, or the llama.cpp provider artifact.
- ServiceLoader discovers Java provider when present and works when llama.cpp provider is absent.
- Explicit llama.cpp selection fails clearly when the optional provider/native runtime is unavailable.
- Explicit Java selection never loads or initializes llama.cpp.
- AUTO selection is deterministic and capability-based.
- GGUF parsing/loading remains available independent of execution provider.
- AQLI request/response/streaming semantics remain canonical Aljabr contracts; no second GGUF-specific DTO stack is introduced.
- Verify supported model architectures, quantization formats, tool calls, multimodal paths, embeddings, streaming, cancellation, session/KV-cache lifecycle, and failure behavior.
- Compare Java and llama.cpp on identical cold/warm prompt and decode workloads before changing the default. Keep the old provider available during the transition.

## Immediate next implementation slice

Start with the optional AQLI module boundary and Gradle dependency graph. Do not move the whole package in one unreviewed bulk change. First isolate the existing llama.cpp engine/provider classes and tests behind the optional module, then wire the AQLI generic GGUF integration to Aljabr's existing Java provider API. Preserve the current default until the no-llama Java-only test matrix and hardware benchmarks pass.
