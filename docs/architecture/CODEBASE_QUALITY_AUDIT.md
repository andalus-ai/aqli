# AQLI Codebase Quality Audit

## Snapshot and scope

- Repository: `andalus-ai/aqli`
- Branch: `main`
- Audited commit: `86035c82619fd0d9cf0e0ddef79b6a9f87d5305f`
- Snapshot date: 2026-11-21
- Method: recursive Git tree inventory, targeted source/build/documentation inspection, duplicate-name and similarity checks.
- Validation: this was a static repository inspection. No Gradle build, test suite, runtime inference, or hardware benchmark was run as part of this snapshot.

This is an incremental audit ledger, not a claim that every file has been semantically reviewed. Findings are labeled as confirmed, candidate, or pending so that structural cleanup does not remove behavior based on filenames alone.

## Inventory

| Measure | Snapshot |
|---|---:|
| Git tree entries (files and directories) | 4,029 |
| Java files | 2,141 |
| Java main-source files | 1,654 |
| Java test-source files | 483 |
| Java files outside conventional `src/` roots | 4 |
| Repeated Java basenames | 46 groups |

Repeated basenames include legitimate types in different bounded contexts, package descriptors, and compatibility façades. They are review signals, not a deletion list.

## Confirmed findings

### 1. CLI classes have excessive responsibility and size

| File | Lines |
|---|---:|
| `cli/aqli-cli/.../commands/FeaturesCommand.java` | 8,270 |
| `cli/aqli-cli/.../commands/ExtensionsCommand.java` | 2,858 |
| `cli/aqli-cli/.../commands/RunCommand.java` | 2,856 |

`FeaturesCommand` contains many nested command, report, scaffold, migration, backup, and manifest types. `ExtensionsCommand` builds numerous reports across plugin, runner, model-family, and runtime concerns. `RunCommand` combines option handling with provider routing, model preparation, audio/TTS, ONNX diagnostics, direct runner paths, and terminal output.

**Disposition:** decompose incrementally along demonstrated capabilities and application-service boundaries. Keep Picocli parsing and terminal concerns in the CLI; move reusable behavior to the owning runtime/application capability. Do not perform a mechanical one-class-per-file split.

### 2. Duplicate ONNX CLI capability implementation

The following files exist in both `commands/diagnostics` and `commands/onnx`:

- `OnnxDiagnosticsRunCapability`
- `OnnxDiagnosticsRunCapabilityProvider`
- `OnnxDiagnosticsRunOptions`

The two capability implementations are identical after removing the package declaration and comments. Both advertise the same capability ID, `onnx-diagnostics`. The CLI `RunCapabilityProvider` service file registers the `commands.diagnostics` provider.

**Disposition:** audit all imports, tests, registrations, and consumers; retain one canonical diagnostics capability and remove the redundant package only after confirming no reflective or compatibility consumer depends on it.

### 3. Safetensors source and tests are excluded from the active source set

`runtime/runner/safetensor/aqli-safetensor-engine/build.gradle.kts` excludes:

- Three tracked main-source classes under `engine/lifecycle/`.
- Two tracked main-source classes under `safetensor/mask/`.
- The `engine/forward/` test directory, which currently contains 41 test files.
- Eight additional individually named test files that exist in the tree.

Two configured exclusions currently appear stale because the corresponding paths are absent from the audited tree: `SafetensorProviderConfig.java` and `InferenceModelPathResolverTest.java`.

**Disposition:** map each excluded class/test to the current implementation and coverage. Re-enable migrated tests, repair stale tests where practical, or document a time-bounded exclusion with an owner and removal condition. Do not delete the 41 forward tests before checking whether they cover behavior that has moved to Aljabr or another test suite.

### 4. Safetensors contains near-duplicate implementation candidates

The following same-named pairs share very high normalized token similarity:

- `engine/generation/BeamSearchDecoder` and `engine/tooling/BeamSearchDecoder` (approximately 98%).
- `engine/generation/attention/SlidingWindowAttention` and `engine/tooling/SlidingWindowAttention` (approximately 95%).

The session manager pair under `engine/session` and `engine/tooling` differs more substantially and must be compared semantically rather than treated as a clone.

**Disposition:** inspect call sites, model semantics, performance requirements, and tests; establish a canonical implementation only where behavior is truly equivalent. Keep provider/model-specific policy separate where it is genuinely different.

### 5. SDK and runtime repeated names include compatibility and ownership ambiguity

Examples include multiple `AqliSdk` façades, `AqliClient` contracts, `ExecutionEngine` types, `ExecutionSession`, `ModelLoadException`, and `ExecutionContext`.

Some SDK duplicates are explicitly deprecated compatibility interfaces. Others may represent separate contracts or different layers.

**Disposition:** document canonical package/module, compatibility status, real consumers, and removal conditions. Audit the runtime execution types for overlapping semantics before consolidation.

### 6. Root-level historical and scratch artifacts need classification

The root contains large or development-oriented files including `README2.md` (~298 KB), `aqli-refactor-changes.txt` (~176 KB), `deps.txt` (~135 KB), `aqli-safetensor-java.patch`, and `TestDylib.java`.

**Disposition:** identify whether each is maintained documentation, a reproducible tool, a migration archive, or a local experiment. Move maintained material into an appropriate documentation/tooling location; remove obsolete artifacts only after checking their provenance and references.

### 7. Build configuration contains redundant declarations

- `settings.gradle.kts` calls `includeOptionalProject("audio:aqli-audio-core", ...)` three times.
- `cli/aqli-cli/build.gradle.kts` declares `:audio:aqli-audio-core` twice.
- The Safetensors engine build repeats `mavenLocal()` in its repository list.

**Disposition:** remove redundant declarations in a focused build-configuration change after verifying project inclusion and dependency resolution are unchanged.

### 8. Root README has incorrect license metadata and broken relative links

- The README badge and license statement say MIT, but `LICENSE.md` contains Apache License 2.0.
- The README links to `LICENSE`, which does not exist in the tree.
- The CLI README link points to `ui/aqli-cli/README.md`, which does not exist in the tree; the tracked module guide is under `cli/aqli-cli/README.md`.

**Disposition:** correct the badge, license text, and links together.

### 9. Architecture documents need an explicit current-state/target-state distinction

The architecture describes a long-term direction in which engine-neutral SPIs move to Aljabr, while current AQLI source and build modules still contain AQLI-owned SPI and compatibility contracts. The distinction is strategically valid but needs to be explicit to prevent premature deletion or duplicate contract creation.

**Disposition:** clarify that migrations are incremental and require an Aljabr-owned contract, published compatibility, migrated consumers, and tests before removing an AQLI contract.

## Incremental work plan

| Workstream | Scope | Acceptance gate |
|---|---|---|
| A. Guidelines and docs | SOLID in practice, proportional abstractions, reuse, package/module ownership, dead-code policy, API and DX rules | AGENTS.md and architecture docs agree; README links and license are correct |
| B. CLI capability cleanup | ONNX duplicate capability; Features/Extensions/Run command decomposition | One provider registration per capability; shared services own reusable behavior; CLI behavior tests pass |
| C. Safetensors source-set cleanup | Excluded source, 49 excluded tests, duplicated attention/decoder candidates | Every exclusion has an owner/reason/removal condition; migrated tests are enabled or explicitly tracked |
| D. SDK and runtime contracts | SDK façades, request/response duplication, execution/session/context types | Canonical API documented; compatibility wrappers have verified consumers and removal conditions |
| E. Module graph | Gradle inclusion, dependency scopes, package placement, cross-module cycles | Dependency direction is verified; optional modules and external composite builds remain optional |
| F. Repository hygiene | Root scratch artifacts, stale docs, generated outputs, scripts | Every retained root artifact has an owner and purpose; dead artifacts are removed safely |
| G. Guardrails | Architecture tests and lightweight static checks | CI catches forbidden dependency direction, stale exclusions, duplicate service IDs, and documentation drift |

## Working rules

1. Keep each PR focused on one architectural purpose.
2. Preserve runtime and CLI behavior unless a separate behavior change is explicitly tested.
3. Do not infer dead code from a filename, directory, or lack of direct imports alone; account for service loading, CDI, reflection, configuration, and published consumers.
4. Do not create abstractions or modules just to satisfy a pattern. Make each extraction improve ownership, reuse, testability, or dependency isolation.
5. Do not move tensor, model-format, compute-backend, or low-level execution ownership from Aljabr back into AQLI.
6. Report exact tests/builds run and any platform-specific validation still outstanding.

### 10. SDK public contracts need consolidation and a compatibility map

The current source exposes multiple SDK entry points:

- `framework/sdk/aqli-sdk-api` defines `tech.kayys.aqli.sdk.AqliSdk`, which extends typed `TextGenerationOperations` but also exposes `createCompletion(InferenceRequest)` using the AQLI SPI request type.
- `AqliClient` exists in both `tech.kayys.aqli.sdk` (the `aqli-sdk` module) and `tech.kayys.aqli.sdk.api` (the `aqli-sdk-api` module). Both are public interfaces and expose different request/result shapes.
- Embedding request/response types exist in both the SDK model package and the inference SPI package.
- The repository's SDK-first guidance already says that SDK-owned typed request contracts should be canonical and that SPI conversion belongs in SDK core.

**Disposition:** map all public signatures, implementations, service providers, examples, and consumers. Choose the canonical SDK API per use case, keep the SPI conversion in the application/SDK implementation boundary, and define deprecation/removal conditions for legacy methods and façades. Do not remove SPI dependencies or compatibility methods wholesale.

### 11. The documented module map and optional Gradle registrations need reconciliation

The architecture table lists modules such as `framework/spi/aqli-spi-model` and multiple backend/runner projects that are not present as Gradle build files in the audited tree. `settings.gradle.kts` still contains optional registrations for some absent module paths. It also conditionally includes five sibling Wayang provider modules under AQLI's `:plugins` project group when a Wayang checkout exists.

**Disposition:** compare the documented module map with the actual Gradle project graph and published artifacts. Remove stale declarations/documentation only after checking whether the path is intentionally supplied by an external workspace, a generated project, or a future optional integration. Keep AQLI independently buildable without Wayang.

### 12. Additional repeated implementation names in the Safetensors tooling package

The `engine/tooling/` package contains five classes. Four repeat names and behavior already present in more domain-specific packages:

- `MultiTenantQuotaService` in `engine/tooling` is identical to `engine/routing/MultiTenantQuotaService` after package/comment normalization.
- `BeamSearchDecoder` closely matches `engine/generation/BeamSearchDecoder`.
- `SlidingWindowAttention` closely matches the generation attention implementation.
- `ConversationSessionManager` overlaps with `engine/session/ConversationSessionManager`, but the two versions have materially different APIs and behavior.

The `ToolCallingEngine` source itself does not reference those four classes. That alone does not prove they are unused: CDI discovery, other module consumers, tests, or reflective wiring may still depend on them.

**Disposition:** trace injection and call sites, compare tests, then retain the canonical capability implementation and make tooling depend on it rather than carrying copied implementations. The session manager requires a behavior-level migration plan.

### 13. Other same-name contracts and implementations require bounded-context review

The basename scan also found repeated names such as `ExecutionEngine`, `ExecutionSession`, `ExecutionContext`, `ModelLoadException`, `ModelException`, `MetricsCollector`, `TokenizerFactory`, `TenantResolver`, `KVBlock`, and `UnifiedKVCache`.

These span framework contracts, runtime implementations, SDK compatibility, and model/provider-specific modules. They are not all defects. The audit must compare fully qualified names, public signatures, dependency direction, semantics, and migration state before consolidation.

## Additional audit limitations

- Repository-wide code search did not return indexed results for the targeted class names in this snapshot. Therefore, no deletion decision is based on a claim that a class has zero references.
- Cross-repository consumers and published Maven artifacts require separate inspection before public API or package removals.
- Platform-specific execution, native resource behavior, and model correctness require focused builds/tests and, where relevant, hardware validation.

### 14. Gradle's declared optional surface is broader than the checked-in build graph

The audited tree contains 77 Gradle build files. The active settings file contains 84 non-commented `includeOptionalProject` declarations, but a number of registrations have no matching in-repository module directory. Some are intentionally external (the sibling Wayang integrations), while others point to absent AQLI paths such as `framework/spi/aqli-spi-model`, `runtime/backend/*`, and older GGUF converter/core modules.

The tree also contains build files for projects that are not part of the active settings graph. The optimization section explicitly labels most optimization plugins experimental/unintegrated and includes only KV-cache by default; ONNX export/helper, pickle, TensorRT plugin, Quarkus extension, and YAFF stub projects also need an explicit active/experimental/legacy status.

**Disposition:** publish a truthful module matrix with one status per project: default build, opt-in build, external composite integration, experimental/unintegrated, legacy/not included, or removed. Remove stale registrations for capabilities now owned by Aljabr (especially backend and model SPI paths) after confirming no supported workspace relies on them. Do not equate the presence of a `build.gradle.kts` file with inclusion, publication, or runtime support.
## Existing architecture work to continue (avoid parallel plans)

This audit is a cross-cutting inventory. It does not replace the repository's more detailed ownership and migration documents. Use those as the source of truth for implementation sequencing and acceptance gates:

- [Module Ownership Matrix](MODULE_OWNERSHIP_MATRIX.md) — placement rules and the existing ExecutionEngine overlap finding.
- [Aljabr–AQLI Ownership Audit](ALJABR_AQLI_OWNERSHIP_AUDIT.md) — execution-engine and Safetensors class-by-class migration boundaries.
- [Inference Contract Migration Audit](INFERENCE_CONTRACT_MIGRATION_AUDIT.md) — direct contract comparisons, consumer-inventory requirements, and compatibility gates.
- [SDK Contract Consolidation](SDK_CONTRACT_CONSOLIDATION.md) — identifies tech.kayys.aqli.sdk.api.AqliClient in aqli-sdk-api as the canonical public client contract and the root-package tech.kayys.aqli.sdk.AqliClient as the legacy surface.
- [RunCommand Decomposition Plan](RUN_COMMAND_DECOMPOSITION_PLAN.md) — existing staged plan for extracting CLI execution lifecycle, model resolution, dispatch, audio/media and diagnostics.
- [Safetensors Engine README](../../runtime/runner/safetensor/aqli-safetensor-engine/README.md) — records that excluded legacy tests are migration work, not passing coverage.
- [Optimization Architecture](../../runtime/optimization/ARCHITECTURE.md) — records the status and default-build policy for experimental optimization modules.

The SDK source inspection in this audit adds one point to the existing SDK decision: the broad tech.kayys.aqli.sdk.AqliSdk interface in aqli-sdk-api still exposes AQLI SPI types such as InferenceRequest and EmbeddingRequest. Keep that compatibility/API-surface issue distinct from the canonical AqliClient decision; do not create a third public client abstraction.

For each workstream, update its existing migration document rather than maintaining competing plans. This audit should remain a concise index of verified structural findings and cross-cutting cleanup status.
### 15. Installer scripts can recurse instead of installing

The current bootstrap chain is circular:

- `scripts/install.sh` downloads and runs `scripts/install-cli.sh`.
- When Homebrew is unavailable, `scripts/install-cli.sh` downloads and runs `scripts/install.sh` again.

On a machine without the Homebrew path, this can recurse indefinitely instead of invoking the actual source installer, `scripts/install-local-runtime.sh`. The installer scripts also hardcode `bhangun/aqli` in their repository defaults/comments, while the current repository and README entry point are `andalus-ai/aqli`.

**Disposition:** make one bootstrap path download/checkout the selected repository revision and invoke the local runtime installer exactly once. Keep repository/branch configurable, use one canonical default, and test the no-Homebrew path as well as the already-installed/update path. Do not use a piped script for a child installer that assumes its own on-disk repository path.

### 16. Development and local installation scripts retain obsolete `ui/` paths

The checked-in layout places the CLI in `cli/aqli-cli` and the API server in `framework/api/aqli-api`, but:

- `scripts/run-dev.sh` looks for `ui/aqli-cli/build/aqli.jar` and tries to build with `-p ui/aqli-cli`.
- `scripts/build-aqli.sh` reports the output at `ui/aqli-cli/build/aqli.jar`.
- `scripts/install-local-runtime.sh` copies the API Quarkus runtime from `ui/aqli-api/build/quarkus-app`.

The same installer correctly uses `:cli:aqli-cli:quarkusBuild` and the `cli/aqli-cli` source module, so its CLI path and API copy path disagree.

**Disposition:** centralize module/output paths or derive them from Gradle project paths. Validate build, run, local install, API runtime copy, and native install from a clean checkout.

### 17. The current module-selection manifest is mostly from an older project graph

`scripts/module-selection-current.env` lists 63 Gradle project paths. A comparison against the current `settings.gradle.kts` and tracked module build files matched only 20 of those paths; 43 names do not map to an active current project. Examples include old `:backend`, `:models`, `:core:aqli-tensor`, `:core:aqli-tokenizer-core`, old Safetensors modules, and `:framework:api:aqli-api` (the current logical project path is `:api:aqli-api`).

`scripts/build-aqli.sh` and `scripts/install-local-macos.sh` source this manifest and turn its `MODULES` entries into Gradle build/publication tasks. The build wrapper skips only a subset of legacy names, so the remaining absent project paths can still be passed to Gradle.

**Disposition:** replace the stale module list with the actual active Gradle project graph and an explicit capability/profile selection. Let Gradle resolve transitive project dependencies from the CLI/API composition roots instead of maintaining a second hand-written list of obsolete module names. Preserve profile properties for backend/format selection.

### 18. CLI command documentation does not match registered Picocli commands

The current `AqliCommand` top-level registration does not include `mcp`, `providers`, or `convert`. The `ConvertCommand` source is marked "CURRENTLY DISABLED" and is not registered. The extension/component command is registered as `modules`, and system information is exposed by `info`, not `system info`.

**Disposition:** derive the command table from the actual top-level Picocli registration and command annotations. Mark disabled commands as unavailable rather than advertising them. Verify the command help output after changes.

### 19. Several maintained user guides still describe the previous product/build architecture

- `docs/QUICKSTART.md` describes the former ML framework, Maven artifact `aqli-ml-api`, old package names, old repository and example paths.
- `framework/sdk/README.md` describes the older `AqliSdkFactory`/SDK-core architecture without identifying the canonical public `AqliClient` API or compatibility layers.
- `framework/sdk/aqli-sdk/README.md` is only a one-line placeholder.
- `framework/api/aqli-api/README.md` instructs developers to use Maven wrapper commands although the module is a Gradle/Quarkus project.
- `docs/README_API.md` links to a missing `API_TEST_SCRIPT.sh` and describes an older server workflow.
- `cli/aqli-cli/README.md` lists unregistered commands and old `bhangun/aqli` release/Homebrew URLs.

**Disposition:** align each guide with the current CLI registration, Gradle project path, canonical SDK contracts, and API module. Avoid documenting examples or install channels that are not present in the repository.
### 20. Local installer also used an invalid API Gradle project path

The local runtime installer invoked `:framework:api:aqli-api:quarkusBuild`, but the actual logical Gradle project path declared by settings is `:api:aqli-api`. The source directory path and Gradle project path are different concepts; both must be derived from the settings mapping.

The local installer and build wrapper have now been corrected in the focused installer follow-up to build the current CLI/API composition roots and use the current source/output directories. The separate `module-selection-current.env` list remains stale: 43 of its 63 declared project paths do not map to the active project graph. It is still consumed by the optional local Maven publication flow in `install-local-macos.sh`, so that opt-in compatibility path remains a follow-up item.
### 21. CI exposed two pre-existing correctness defects during the audit

The first focused build-config PR triggered the repository's current CI and exposed two unrelated baseline failures:

- `RunCommand.printResponse` had an incomplete extraction to `RunResponseContentRenderer`: the structured-output call was opened as a block, its duplicate OCR/JSON implementation remained inline, and the method boundary was missing. This caused `RunCommand.java:2467` to fail compilation. The follow-up removes the duplicate rendering path, honors the renderer's boolean result, preserves media/telemetry handling, and removes the unused start-time parameter.
- `LocalAqliSdk.createCompletionAsync` used Mutiny's default timeout failure, while the existing async SDK contract tests require `java.util.concurrent.TimeoutException` as the `CompletableFuture` failure cause. The follow-up maps the timeout operator to the JDK exception while retaining the timeout operator.

These are correctness fixes, not regressions introduced by the Gradle declaration cleanup. The focused fixes are tracked in [PR #664](https://github.com/andalus-ai/aqli/pull/664) and [PR #665](https://github.com/andalus-ai/aqli/pull/665). CI must be rerun after those changes land before treating the baseline as green.
