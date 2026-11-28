# CLI Command Decomposition Plan

This plan covers the CLI command classes whose responsibilities are too broad to maintain safely. It complements the existing [RunCommand Decomposition Plan](RUN_COMMAND_DECOMPOSITION_PLAN.md); it does not replace that plan or introduce a new public API.

## Verified baseline

| Class | Current size | Structural signal |
|---|---:|---|
| `FeaturesCommand` | 7360 lines | 14 Picocli command annotations; scaffold catalog, rendering and file writes extracted |
| `ExtensionsCommand` | 2,858 lines | 3 types, about 130 methods, one Picocli command annotation |
| `RunCommand` | 2,763 lines | Already has a staged extraction plan; continue that plan rather than creating another |

These counts are from the current source files and should be refreshed before each extraction.

## 1. FeaturesCommand

Source: `cli/aqli-cli/src/main/java/tech/kayys/aqli/cli/commands/FeaturesCommand.java`

The class currently combines several independent responsibilities:

1. Picocli root command and subcommand registration.
2. Feature catalog operations: kinds, index, inspect and doctor.
3. Feature scaffolding and code generation: Gradle files, Java providers, manifests and README generation.
4. Installation lifecycle: install, remove, detach, backups and rollback.
5. Migration lifecycle: migrate, prune legacy installations and finalize migration.
6. Filesystem/JAR/ZIP inspection, package manifest parsing, collision detection and install-lock repair.
7. JSON/table rendering and operational summaries.

### Target organization

Keep the root command thin and preserve the existing command names/aliases:

```text
commands/features/
  FeaturesCommand
  catalog/
    KindsCommand
    IndexCommand
    InspectCommand
    DoctorCommand
  scaffold/
    InitCommand
    ScaffoldKindCatalog
    ScaffoldContentGenerator
    ScaffoldFileWriter
    ScaffoldDefinition
  install/
    InstallCommand
    RemoveCommand
    DetachCommand
    BackupsCommand
    RollbackCommand
    FeatureInstallService
    FeaturePackageReader
    FeatureInstallLockStore
  migration/
    MigrateCommand
    PruneLegacyCommand
    FinalizeMigrationCommand
    FeatureMigrationService
  diagnostics/
    FeatureDoctorService
    FeatureCollisionScanner
    FeatureInstallLockRepairService
  report/
    FeatureReportRenderer
    FeatureReportDtos
```

This is a target grouping, not a requirement to create every file immediately. Keep small cohesive helpers together when splitting them would create needless indirection.

### Extraction order

**Completed first scaffold slice:** `ScaffoldKindCatalog` now owns the immutable list of scaffold kinds, their JSON projection, and adapter default input/output metadata. `FeaturesCommand.Kinds` is now a Picocli presentation adapter over that catalog, and `Init` reuses the same default metadata. The catalog lives at `commands.features.scaffold`; it is not a new Gradle module. The adapter-kind vocabulary now lives in `commands.features.FeatureAdapterKinds`, so the catalog no longer depends on the Picocli root command. The former nested `FeaturesCommand.FeatureAdapterKinds` remains as a deprecated compatibility facade. Existing command names, output field names, ordering, normalization behavior, and generated scaffold templates are preserved.

**Completed second scaffold slice:** `ScaffoldContentGenerator` now owns deterministic rendering of Java providers, Gradle build files, service registrations, manifests, plugin descriptors and README files. Its immutable `ScaffoldDefinition` input lives with the scaffold capability. `ScaffoldFileWriter` owns path containment, directory creation, dry-run reporting and create/overwrite behavior. The generator has no filesystem or terminal side effects; the CLI `Init` command only normalizes options, constructs the definition, delegates, and renders the result. Existing file paths, generated content, command output fields and force/dry-run behavior are preserved. Focused tests cover deterministic generation, model-family registrations, dry-run and overwrite semantics.

**Collision-analysis slice:** `FeatureCollisionScanner` owns deterministic, pure collision aggregation over immutable jar/capability claim snapshots. `FeaturesCommand` remains responsible for reading extension metadata and adapting it into those snapshots; the scanner does not inspect archives, discover providers, or apply install/runtime policy. Collision DTOs and JSON projection move with the scanner, preserving collision kinds, claim sources, ordering, severity, and output keys. Focused tests cover cross-jar collisions, deterministic ordering, blank claims, and same-jar duplicate suppression.

**Package-reader slice:** `ExtensionPackageReader` owns the ZIP distribution boundary used by extension inspect/install flows: package-manifest discovery, Java-artifact selection, archive-relative path validation, temporary extraction, SHA-256 metadata, and deterministic temporary-directory cleanup. It returns a package report and an AutoCloseable install source; it does not load providers, register capabilities, resolve SDK capability implementations, or select runtime engines. Package `kind` and artifact metadata remain descriptive package metadata; capability semantics and execution registration stay with the owning SDK/runtime capability contracts. Focused tests cover JAR passthrough, ZIP extraction and cleanup, traversal rejection, duplicate manifests, and unsupported file types.

**Install-lock persistence slice:** `features.install.FeatureInstallLockStore` owns canonical/legacy lockfile path resolution, JSON compatibility fields, inventory reads/writes, and entry upsert/removal. Callers supply runtime compatibility metadata and already-inspected artifact entries; the store does not inspect packages, discover providers, or interpret capability kinds. Install/remove/rollback orchestration and backup transaction policy remain with their existing use-case flows until extracted independently.


1. **Command boundaries first.** Move nested Picocli command classes into top-level package-private/public command classes while preserving names, aliases, options, exit codes and help output.
2. **Scaffolding second.** Template generation and filesystem writing are now separated into `ScaffoldContentGenerator` and `ScaffoldFileWriter`. Continue by extracting the remaining input normalization/validation from `Init` into a focused scaffold request factory/validator only where it removes real duplication; do not add another facade around the generator.
3. **Package inspection third.** Centralize JAR/ZIP manifest reading, path validation and package identity extraction in `ExtensionPackageReader`. Do not duplicate archive parsing in install, inspect and doctor flows.
4. **Installation lifecycle fourth.** Move install-lock, backup, remove, detach and rollback state transitions into `FeatureInstallService`. Keep filesystem mutations explicit and transactional.
5. **Migration and repair fifth.** Separate migration planning from applying operations. Support dry-run plans and deterministic reports.
6. **Rendering last.** Move JSON/table projection out of command classes after service contracts stabilize.

## 2. ExtensionsCommand (the `modules` command)
### Extraction progress

**Completed first reporting slice:** `UnifiedRuntimeReadinessReport` now owns the unified-runtime JSON projection (`runtimes`, conflicts, contract violations, and model-family requirement rows). It consumes the registry and compatibility rows supplied by the command; plugin discovery and compatibility/policy evaluation remain with their existing owners. Focused tests cover empty registries and null input normalization. The Picocli command and JSON section keys remain unchanged.


Source: `cli/aqli-cli/src/main/java/tech/kayys/aqli/cli/commands/ExtensionsCommand.java`

The command currently builds and renders a large composite report spanning:

- module and runner inventory;
- plugin directory and classpath health;
- unified runtime requirements and violations;
- model-family plugins, bundle manifests, tokenizer coverage and capability matrices;
- extension availability and policy gates;
- audio extension readiness;
- route-benchmark cache readiness;
- JSON and terminal output.

This is a reporting/projection concern, not one cohesive command operation.

### Target organization
**Completed first bundle-report slice:** `ModelFamilyBundleReport` now owns the CLI projections for bundle fixture/availability, production safety, catalog readiness, gates, presets, preset conformance, policy status/violations, and aliases. The command continues to load the manifest, collect plugins/registries, and evaluate availability/gate policy through the existing owners. This keeps the extracted class a projection layer rather than a second policy engine.


Keep `ExtensionsCommand` as the Picocli adapter. Extract focused report builders that consume existing registries and immutable snapshots:

```text
commands/modules/
  ModulesCommand
  report/
    ModuleInventoryReport
    PluginHealthReport
    UnifiedRuntimeReadinessReport
    ModelFamilyCapabilityReport
    ModelFamilyBundleReport
    ExtensionAvailabilityReport
    RouteBenchmarkReadinessReport
    ModuleReportRenderer
```

Use existing runtime registries and contracts as the data source. Do not duplicate plugin discovery, model-family registries or runtime compatibility policy inside the CLI. Report builders should project those values into CLI-facing DTOs; they should not own runtime policy.

## 3. Boundaries and SOLID rules

- **Single responsibility:** Picocli classes parse options, invoke one use case and select an output mode. They do not own archive parsing, installation transactions, policy evaluation or large JSON assembly.
- **Open/closed:** new feature kinds and report sections should be added through focused registries/builders, not by expanding a monolithic switch or command class.
- **Liskov/interface segregation:** use the existing narrow capability/provider contracts; do not introduce broad command-service interfaces that every subcommand must implement.
- **Dependency inversion:** command classes depend on small application services and existing SPI contracts, not concrete runner internals.
- **Reuse:** centralize package manifest parsing, feature identity normalization, install-lock persistence and report projections. Do not copy helpers between command packages.
- **Proportionality:** do not create a new Gradle module or generic command framework for CLI-only behavior. Promote a service into a shared framework/runtime module only when there is a second real consumer and a stable domain contract.

## 4. Compatibility and acceptance gates

Each extraction should be a separate, reviewable PR.

- Preserve command names, aliases, options, defaults, exit codes and help output.
- Preserve existing JSON field names and meanings unless an explicitly versioned contract change is approved.
- Preserve install/migrate/rollback behavior and on-disk manifest compatibility.
- Keep file mutations behind explicit services; support dry-run for migration and repair.
- Add focused unit tests for pure generators, readers, report builders and state transitions.
- Add command-level tests for Picocli wiring and representative end-to-end behavior.
- Run the relevant CLI tests and `./gradlew :cli:aqli-cli:quarkusBuild`.
- Delete old nested implementations only after all consumers and tests use the extracted owner.

## 5. Non-goals

- Do not rename public CLI commands as part of structural extraction.
- Do not move CLI-specific output DTOs into the public SDK.
- Do not duplicate runtime plugin/model-family policies in the CLI.
- Do not split every method into a class; the goal is cohesive ownership, not maximum file count.


## 6. Capability-first package and deployment organization

The command tree is an adapter surface; it is not the owner of the capabilities it exposes. Keep the existing public root command classes as stable Picocli entry points while moving their implementation into cohesive capability packages.

### CLI package target

```text
tech.kayys.aqli.cli.commands
  RunCommand                         # stable root command / composition only
  FeaturesCommand                    # stable root command / registration only
  run/
    text/                            # text-generation option groups and CLI mapping
    audio/                           # audio/TTS options, replay and CLI presentation
    vision/                          # image/vision/OCR CLI input and presentation
    tools/                           # tool and MCP CLI options/mapping
    retrieval/                       # RAG/retrieval CLI options/mapping
    pipeline/                        # pipeline-specific CLI options/mapping
    model/
      source/                         # source intent and CLI precedence policy
      repository/                     # repository option groups and CLI mapping
      conversion/                     # conversion option groups and CLI mapping
    routing/                          # CLI route flags and route presentation
    execution/                        # execution-mode selection at adapter boundary
    stream/                           # CLI stream callbacks and output routing
    output/                           # terminal, JSON/SSE and artifact presentation
    diagnostics/                      # CLI diagnostics options and rendering
  features/
    catalog/                          # kinds, index, inspect, doctor command adapters
    scaffold/                         # init command and CLI scaffold presentation
    install/                          # install/remove/detach/rollback/backups adapters
    migration/                        # migration command adapters
    diagnostics/                      # doctor/collision/repair presentation
    report/                           # CLI JSON/table projections
```

This is a destination map, not a mandate to move every option record into a separate file. Existing cohesive option groups should be moved as units, and package moves must include tests and all callers. The stable `RunCommand` and `FeaturesCommand` FQCNs remain until a separately justified compatibility change.

### Runtime and framework ownership

| Capability | Contract / public API | Reusable implementation | CLI responsibility |
|---|---|---|---|
| Text inference | SDK text capability API | SDK application path and selected runtime runner | Parse text flags, map request, render output |
| Audio / TTS | Audio/TTS capability contracts | audio runtime and provider/model-specific runner | Audio flags, playback choice, terminal progress |
| Image / vision / OCR | modality contracts | corresponding runtime/runner capability | Input paths and CLI result presentation |
| Model lifecycle | SDK model lifecycle capabilities | model repository and provider-owned lifecycle services | Source intent, flags, progress display |
| Routing / execution | SDK/runtime route contracts | runtime route policy and runner catalog | Translate explicit CLI choices and explain decisions |
| Tools / retrieval / pipelines | capability APIs and SPIs | corresponding runtime/plugin capabilities | CLI-only option contribution and mapping |
| Media artifacts | media/audio contracts | media and audio runtime capabilities | Output naming, auto-open, terminal messages |
| Diagnostics / metrics | observability and runner diagnostics APIs | owning runtime/runner capability | Select diagnostic action and render report |
| Feature installation | stable feature/package contracts only where shared | plugin/extension lifecycle owner | Picocli command and interactive confirmation |

Do not promote a CLI-only option record or report DTO into the SDK. Conversely, do not keep reusable filesystem, archive, repository, media, or runtime policy in the CLI simply because a command currently calls it.

### Deployment and profiling strategy

Capability packages provide source-level discoverability; Gradle artifacts provide dependency isolation; deployment profiles provide selectable runtime composition. These are related but not interchangeable.

1. Keep capability implementations in their existing runtime modules (audio, media, repository, runner, plugin, observability, and SDK-local modules). Extend an existing owner before adding a module.
2. Keep `:cli:aqli-cli-spi` as the CLI-only Picocli contribution SPI. It must not become the public capability registry or runtime engine catalog.
3. Keep `:cli:aqli-cli` as the command/application adapter. Move runner-specific composition behind the existing SDK/runtime capability boundaries before trying to make individual runner dependencies optional.
4. Define deployment profiles as curated dependency compositions (for example, text-only, text-plus-audio, multimodal, and full development). Each profile must declare its included capabilities, runner/provider implementations, native requirements, and exclusions.
5. Do not implement profile switching by excluding jars from the runtime classpath while CLI classes still directly reference those runner types. First remove those compile-time coupling points and prove service discovery/Quarkus augmentation works without the omitted implementation.
6. Create a separate Gradle distribution/profile module only when it produces a genuinely independently buildable/packageable application artifact. Do not create one module per CLI command or one thin wrapper per capability.
7. Keep profiling metadata capability-scoped: each capability should expose stable identity, implementation/provider identity, required runtime features, and optional native/device requirements through existing registries/contracts. Deployment assembly consumes this metadata; CLI report code must not duplicate runtime policy.

### Packaging acceptance gates

- A profile builds without resolving implementation artifacts it excludes.
- A profile starts without class-loading or CDI augmentation failures for excluded capabilities.
- Capability discovery reports only implementations present and eligible in that profile.
- SDK/application contracts remain usable without Picocli.
- The same capability can be composed by CLI, REST, gRPC, or programmatic callers without duplicating its execution semantics.
- Profile-specific smoke tests cover startup, capability inventory, and one representative operation; hardware inference tests are required only for profiles that exercise device/native execution.
