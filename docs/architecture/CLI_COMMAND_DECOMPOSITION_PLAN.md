# CLI Command Decomposition Plan

This plan covers the CLI command classes whose responsibilities are too broad to maintain safely. It complements the existing [RunCommand Decomposition Plan](RUN_COMMAND_DECOMPOSITION_PLAN.md); it does not replace that plan or introduce a new public API.

## Verified baseline

| Class | Current size | Structural signal |
|---|---:|---|
| `FeaturesCommand` | 8,270 lines | 59 top-level/nested types, 14 Picocli command annotations, about 331 methods |
| `ExtensionsCommand` | 2,858 lines | 3 types, about 130 methods, one Picocli command annotation |
| `RunCommand` | 2,856 lines | Already has a staged extraction plan; continue that plan rather than creating another |

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
    FeatureScaffoldGenerator
    ScaffoldTemplates
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

1. **Command boundaries first.** Move nested Picocli command classes into top-level package-private/public command classes while preserving names, aliases, options, exit codes and help output.
2. **Scaffolding second.** Move template generation and identifier/path normalization into a deterministic `FeatureScaffoldGenerator`. It should accept immutable input and return a file plan; writing files belongs in a small filesystem adapter.
3. **Package inspection third.** Centralize JAR/ZIP manifest reading, path validation and package identity extraction in `FeaturePackageReader`. Do not duplicate archive parsing in install, inspect and doctor flows.
4. **Installation lifecycle fourth.** Move install-lock, backup, remove, detach and rollback state transitions into `FeatureInstallService`. Keep filesystem mutations explicit and transactional.
5. **Migration and repair fifth.** Separate migration planning from applying operations. Support dry-run plans and deterministic reports.
6. **Rendering last.** Move JSON/table projection out of command classes after service contracts stabilize.

## 2. ExtensionsCommand (the `modules` command)

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
