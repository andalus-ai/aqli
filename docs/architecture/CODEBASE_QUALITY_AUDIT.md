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
