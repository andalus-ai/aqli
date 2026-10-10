# Aqli SPI (Service Provider Interfaces)

Centralized location for all Aqli Service Provider Interfaces (SPIs).

## 📦 Module Structure

```
inference-aqli/spi/
├── pom.xml                       # Parent POM
├── aqli-spi-plugin/            # Plugin lifecycle and extension points
├── aqli-spi-model/             # Model loading and metadata
├── aqli-spi-inference/         # Inference execution contracts
└── aqli-spi-provider/          # Provider discovery and management
```

## 🎯 Purpose

Each SPI module defines a specific contract:

### aqli-spi-plugin
- Plugin lifecycle management
- Extension points
- Plugin registry
- Plugin health monitoring
- Plugin state management

### aqli-spi-model  
- Model loading interface
- Model metadata
- Model format abstraction
- Model repository contracts
- Model routing

### aqli-spi-inference
- Inference session management
- Input/output processing
- Execution contracts
- Result handling
- Device management

### aqli-spi-provider
- Provider discovery
- Provider configuration
- Backend selection
- Provider lifecycle
- Provider routing
- Backend-neutral feature pipelines through `ModelPipeline`
- Global extension adapters through `FeatureAdapter`

## Feature Pipelines

`aqli-spi-provider` also contains the feature pipeline SPI:

- `ModelPipeline` is implemented by OCR, TTS, ASR, diffusion, evaluation, or other model-specific orchestration projects.
- `ModelPipelineRequest` preserves the original `ProviderRequest` and adds neutral model facts such as `format`, `provider`, and backend-specific inspection keys.
- `ModelPipelineRegistry` discovers feature jars through CDI or Java `ServiceLoader`.

This layer is intentionally not ONNX-only. ONNX, LiteRT, safetensor, GGUF, or future runners can all publish inspected facts into the same request envelope, then delegate to a matching pipeline when a model needs preprocessing, multiple sessions, tokenizer/audio/image postprocessing, or other orchestration above a single backend call. Current provider facts include `format=onnx`, `format=litert`, and `format=safetensors`.

External extensions should live outside the core engine when they are experimental, domain-specific, or maintained by a separate team. See `features/aqli-paddleocr` for a minimal out-of-core example.

## Feature Adapters

`FeatureAdapter` is the generic SPI inside an extension. Use it for training, optimization, quantization, backend bridges, model conversion/export, dataset tooling, evaluation, or other domain-specific capabilities that should live outside Aqli core.

Adapters can be declared through Java `ServiceLoader`:

```text
META-INF/services/tech.kayys.aqli.spi.feature.FeatureAdapter
```

Extension manifests can describe all extension surfaces in one schema:

```json
{
  "schema_version": 1,
  "id": "aqli-awq-lab",
  "name": "Aqli AWQ Lab",
  "version": "0.1.0",
  "requires": {
    "aqli_spi": ">=0.1.0"
  },
  "capabilities": [
    {
      "id": "awq-quantizer",
      "kind": "quantization",
      "class": "example.AwqQuantizer",
      "formats": ["safetensors", "gguf"],
      "inputs": ["model"],
      "outputs": ["model"],
      "targets": ["llm"]
    },
    {
      "id": "metal-backend-profile",
      "kind": "backend",
      "provider": "metal"
    }
  ]
}
```

The preferred manifest path is `META-INF/aqli-extension.json`; Aqli still reads the legacy `META-INF/aqli-feature.json` path for compatibility. The legacy `pipelines` array is still supported and is treated as `kind: "pipeline"`. New manifests may also use top-level `adapters` for adapter-only declarations. The CLI scans all three arrays: `pipelines`, `capabilities`, and `adapters`.

Runtime extension jars and distributable extension zip packages can be managed with the CLI:

```bash
aqli extensions kinds
aqli extensions init features/aqli-my-extension --pipeline-id my-feature --aqli-path aqli
aqli extensions init features/aqli-awq-lab --kind quantization --adapter-id awq-quantizer --input model --output model --target llm --format safetensors,gguf --aqli-path aqli
aqli extensions --plugin-dir path/to/extensions --details --paths
aqli extensions inspect path/to/extension.jar --strict
aqli extensions install path/to/extension.jar
aqli extensions inspect path/to/aqli-extension-package.zip --strict
aqli extensions install path/to/aqli-extension-package.zip
aqli extensions index --output aqli-extensions.index.json
aqli extensions doctor --strict --json
aqli extensions doctor --repair --dry-run
aqli extensions doctor --plugin-dir path/to/extensions --strict
aqli extensions backups --json
aqli extensions backups extension-id --prune --keep 5 --dry-run
aqli extensions rollback extension-id --dry-run
aqli extensions remove extension-id
aqli extensions --doctor --paths
aqli run --model <model> --pipeline my-feature --prompt "hello"
```

`features` remains an alias for `extensions` during migration. `inspect --strict` validates service providers, manifest pipeline classes, duplicate pipeline ids, and modality names before install. When given a package zip, `inspect` reads `aqli-package.json`, extracts the declared jar artifact to a temporary file, applies the same jar checks, and compares package descriptor id/name/version/requirements plus artifact manifest metadata against the jar manifest. Package installs reject descriptor-to-jar mismatches before copying. `extensions doctor --json` applies the same checks across every configured extension path, reports validation summary counts, and returns a non-zero exit code for CI when errors are found. Add `--strict` to treat warnings as failures. The root `extensions --doctor --json` flag remains useful when you want discovery output and doctor details in one report.

`extensions kinds` lists supported scaffold kinds, generated service contracts, default inputs/outputs, target hints, and copyable examples. `extensions init` defaults to `--kind pipeline` and generates a `ModelPipeline` service. Non-pipeline kinds such as `training`, `optimization`, `quantization`, `backend`, `exporter`, `dataset`, and `evaluator` generate a `FeatureAdapter` service plus a manifest capability so global extension work can start outside the core engine. Generated manifests include service metadata and an ahead-of-time loading note.

`extensions install` accepts either a jar or a package zip. Zip installs read the package descriptor, extract the declared jar artifact, and install that jar into the target extension directory. The command maintains a `aqli-extensions.lock.json` file in the target extension directory. The lock stores each installed jar's filename, path, SHA-256, size, install time, runtime version, providers, adapter providers, extension id, capability ids, capability kinds, and pipeline ids. Package zip installs also record `source_package` provenance with the zip path, package SHA-256, package size, descriptor entry, jar artifact entry, jar artifact SHA-256, jar artifact size, package id, version, kind, install filename, and required runtime metadata. `extensions doctor` compares current jars against that lock so manual replacement, stale lock entries, and missing installed jars are visible during validation; when a recorded source package zip still exists, doctor also verifies its size, SHA-256, descriptor entry, jar artifact entry, jar artifact fingerprint, package id/version/kind, and recorded requirements. Existing jars without lock entries remain valid; they are simply untracked until reinstalled through the CLI. `extensions doctor --repair --dry-run` previews lock changes, and `extensions doctor --repair` refreshes lock entries for existing readable jars, enriches older package provenance when the recorded package artifact still matches the installed jar, and removes stale lock entries from scanned directories.

`extensions doctor` also detects collisions across extension ids, feature ids, pipeline ids, adapter ids, provider classes, and implementation classes. Collisions remain warnings by default and become failures with `--strict`.

JVM runtimes can dynamically load extension jars from `~/.aqli/extensions`, `AQLI_EXTENSION_PATH`, or `-Daqli.extensions.path`. GraalVM native image runtimes are AOT-only: dynamic jar classloading is disabled, so extension pipelines/adapters must be present on the build classpath and registered before native compilation. Installed jars are still manifest-inspectable for doctor, migration, and inventory. Use `extensions index --output aqli-extensions.index.json` to emit jar checksums, ServiceLoader entries, manifest capabilities, validation status, and native-image hints for launchers or build scripts.

`extensions migrate --dry-run` previews copying legacy `~/.aqli/features` jars into the runtime `~/.aqli/extensions` directory. Running without `--dry-run` copies readable jars and writes `aqli-extensions.lock.json`. Pass `--force` when a target jar already exists and should be replaced after a rollback backup is created.

`extensions finalize-migration --dry-run` previews legacy jars that are already represented by runtime extension jars. Running without `--dry-run` archives each matched legacy jar into rollback backups and removes it from `~/.aqli/features`. `extensions prune-legacy` remains available as the lower-level cleanup command.

Forced installs and removals create rollback backups under `.aqli-extension-backups` by default. `extensions rollback <jar-or-extension>` restores the newest matching backup, writes a fresh install lock, and saves the current jar first so rollback itself can be undone. Pass `--no-backup` to `install --force` or `remove` when that safety copy is not desired.

`extensions backups` lists rollback history with extension metadata, reason, size, checksum, source path, and metadata path. Add a target to filter by jar filename, extension id, feature id, pipeline id, or provider class. `extensions backups <target> --prune --keep 5 --dry-run` previews cleanup, and dropping `--dry-run` deletes older backup jars plus their `.json` sidecars.

`extensions --dir path/to/extensions` and `extensions --plugin-dir path/to/extensions` are manifest scanners for extension directories or individual jars. They list declared features/adapters without classloading the jar, which is useful for review and CI staging. To make a scanned pipeline active for inference, install the jar into `~/.aqli/extensions` or launch Aqli with `AQLI_EXTENSION_PATH` / `-Daqli.extensions.path`. Legacy `~/.aqli/features` is only a migration source. General plugin lifecycle and `~/.aqli/plugins` loading still live in `aqli/core/plugin/aqli-plugin-core`.

Legacy path configuration such as `AQLI_FEATURE_PATH` or `-Daqli.features.path` is ignored. `extensions doctor` reports those settings as warnings, and `--strict` fails when they are still present.

`extensions doctor --json` includes a compact `legacy_migration` block. It keeps legacy jars out of runtime discovery while still showing whether `migrate --dry-run` or `finalize-migration --dry-run` would clean up old installs.

Extension manifests should declare runtime compatibility:

```json
{
  "schema_version": 1,
  "requires": {
    "aqli_spi": "0.1.0-SNAPSHOT",
    "java": ">=25"
  }
}
```

`extensions inspect` and `extensions doctor` compare `requires.aqli`, `requires.aqli_runtime`, `requires.aqli_cli`, or `requires.aqli_spi` against the active Aqli runtime. Exact versions, wildcard forms such as `0.1.x`, and simple constraints such as `>=0.1.0,<0.2.0` are supported. Incompatible versions are reported as errors; missing compatibility metadata is reported as a warning.

Extension jars are installed into `~/.aqli/extensions` by default, or loaded from custom paths with `AQLI_EXTENSION_PATH` / `-Daqli.extensions.path`. Legacy feature paths are still supported for existing installations.

## 🔧 Usage

Add dependency to your module:

```xml
<!-- Plugin SPI -->
<dependency>
    <groupId>tech.kayys.aqli</groupId>
    <artifactId>aqli-spi-plugin</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Model SPI -->
<dependency>
    <groupId>tech.kayys.aqli</groupId>
    <artifactId>aqli-spi-model</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Inference SPI -->
<dependency>
    <groupId>tech.kayys.aqli</groupId>
    <artifactId>aqli-spi-inference</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- Provider SPI -->
<dependency>
    <groupId>tech.kayys.aqli</groupId>
    <artifactId>aqli-spi-provider</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 🏗️ Architecture Principles

The SPI modules follow these principles:

1. **Interface-only**: Define contracts, not implementations
2. **Independent**: Each SPI can evolve independently  
3. **Minimal dependencies**: Only depend on what's necessary
4. **Stable contracts**: Backward-compatible changes only
5. **Well-documented**: Clear JavaDoc for all interfaces
6. **Centralized location**: All SPIs in one place for easy discovery

## 📝 Implementation Guide

To implement a SPI:

1. Add the SPI dependency to your module
2. Implement the interface(s)
3. Register implementation via `META-INF/services`
4. Use appropriate annotations

Example:

```java
// Implement Model SPI
public class MyModelLoader implements ModelLoader {
    @Override
    public Model load(Path path) {
        // Implementation
    }
}

// Register in META-INF/services
// META-INF/services/tech.kayys.alkhawarizm.spi.model.ModelLoader
tech.kayys.alkhawarizm.spi.model.MyModelLoader
```

## 📁 Location

**Old Structure** (deprecated):
```
inference-aqli/core/
├── aqli-spi-plugin/
├── aqli-spi-model/
├── aqli-spi-inference/
└── aqli-spi-provider/
```

**New Structure** (current):
```
inference-aqli/spi/
├── aqli-spi-plugin/
├── aqli-spi-model/
├── aqli-spi-inference/
└── aqli-spi-provider/
```

## 🔗 Related Modules

### Implementations (in `core/`)
- `aqli-plugin-core` - Plugin implementation
- `aqli-model-registry` - Model registry implementation
- `aqli-provider-core` - Provider implementation

### Runtime (in `runtime/`)
- `aqli-runtime` - Main runtime
- `aqli-engine` - Engine implementation

## 🚀 Benefits of Centralized SPI Location

1. **Easy Discovery**: All SPIs in one place
2. **Clear Separation**: SPIs separate from implementations
3. **Better Organization**: Logical grouping
4. **Simplified Dependencies**: Single parent for all SPIs
5. **Independent Evolution**: SPIs can change without affecting core

---

**Version**: 1.0.0-SNAPSHOT  
**Parent**: aqli-parent  
**Location**: `inference-aqli/spi/`
