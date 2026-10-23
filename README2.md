(Files content cropped to 300k characters, download full ingest to see more)
================================================
FILE: README.md
================================================
<div align="center">
  <img src="https://raw.githubusercontent.com/kayys-tech/repo-assets/master/aqli03%404x.png" alt="Aqli" width="300" />
  
  # Aqli Inference Engine
  
  [![Java 25](https://img.shields.io/badge/Java-25-blue.svg)](https://jdk.java.net/25/)
  [![Gradle](https://img.shields.io/badge/Gradle-9.x-brightgreen.svg)](https://gradle.org/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Native Image](https://img.shields.io/badge/GraalVM-Native%20Image-orange.svg)](https://www.graalvm.org/)
</div>

**Aqli** is a high-performance, low-level inference engine and serving layer for AI models. It is the unified evolution of the former `aljabr` (tensor/model runtime) and `tafkir` (quantization) projects, now consolidated as a single standalone project.

Aqli acts as the foundation for the [Wayang](../wayang/) orchestration layer. While Wayang handles agents and orchestration, Aqli handles the raw inference machinery: model loading, tensor operations, quantization, GGUF/SafeTensor execution, and hardware-accelerated backends (CPU, Metal, CUDA).

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Wayang (Agent Layer)              │
│          Orchestration · RAG · MCP · Tools          │
└───────────────────────┬─────────────────────────────┘
                        │  aqli-sdk / aqli-sdk-api
┌───────────────────────▼─────────────────────────────┐
│              Aqli (Inference Engine)              │
│                                                     │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │  GGUF    │  │SafeTensor│  │  LiteRT / ONNX    │ │
│  │  Runner  │  │  Runner  │  │     Runners       │ │
│  └──────────┘  └──────────┘  └───────────────────┘ │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │         aljabr (tensor core)            │  │
│  │  CPU · Metal (Apple Silicon) · CUDA          │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## Prerequisites

- **Java 25** (GraalVM for native image, Temurin for JVM)
- **Gradle 9.x** (wrapper included)
- **macOS** (Apple Silicon, Metal acceleration) or **Linux** (CPU / CUDA)

Optional:
- `graalvm-ce-25` for `buildNative`
- `llama.cpp` source (auto-prepared by `scripts/prepare-llama-source.sh`)

## Quick Start

### Install via script

```bash
curl -sSL https://raw.githubusercontent.com/kayys-tech/aqli/main/scripts/install.sh | bash
```

### Build from source

```bash
git clone https://github.com/kayys-tech/aqli.git
cd aqli

# Build JVM jar
./gradlew :ui:aqli-cli:quarkusBuild -x test

# Build native binary (requires GraalVM 25)
./gradlew :ui:aqli-cli:buildNative -x test
```

### Run the CLI

```bash
# Interactive chat
aqli chat --model <modelId>

# Pull a model from HuggingFace (task type auto-detected)
aqli pull hf:Qwen/Qwen2.5-7B-Instruct-GGUF

# Pull a vision model
aqli pull hf:HuggingFaceTB/SmolVLM-256M-Instruct

# List models with task-type grouping
aqli list

# Filter by task category
aqli list --task-type vision
aqli list --task-type stt

# Single-shot inference (clean output for scripts)
aqli run --model <id> --no-banner --no-info --only-text --prompt "Hello"

# Start the API server
aqli serve --model <id> --port 8080

# Check system info and resource utilization
aqli system info
```

## CLI Reference

See the full [CLI README](ui/aqli-cli/README.md) for all commands and flags. Key highlights:

### Model task-type grouping

Every model downloaded via `aqli pull` is automatically tagged with a **task type** derived from the
HuggingFace `pipeline_tag`:

| HuggingFace tag | Aqli task type |
|-----------------|------------------|
| `text-generation`, `conversational` | `text` |
| `automatic-speech-recognition`, `audio-classification` | `stt` |
| `text-to-speech`, `text-to-audio` | `tts` |
| `image-to-text`, `image-classification`, `object-detection` | `vision` |
| `feature-extraction`, `sentence-similarity` | `embedding` |

```bash
aqli list              # shows TASK column
aqli list -t vision    # filter by category
aqli pull hf:myorg/my-tts-model --task-type tts  # override
```

### Clean output flags for scripting

```bash
aqli run --model <id> \
  --no-banner   \   # suppress ASCII banner
  --no-info     \   # suppress model/provider line
  --only-text   \   # print only the assistant text
  --prompt "..."
```

## SDK Usage

```java
// Embedded SDK with built-in resource metrics
AqliSdk sdk = AqliSdkFactory.create(
    AqliSdkConfig.local()
        .observability(SdkObservabilityProvider.withResourceMetrics())
        .build()
);

// Infer
InferenceResponse response = sdk.infer("qwen2.5-0.5b", request);

// Check resource utilization (for capacity planning)
SdkMetricsCollector.ResourceSnapshot resources = sdk.getResourceSnapshot();
System.out.printf("CPU: %.1f%% | Heap: %dMB / %dMB%n",
    resources.processCpuLoad() * 100,
    resources.heapUsedBytes() / 1_048_576,
    resources.heapMaxBytes() / 1_048_576);
```

## Observability

Aqli exposes metrics for capacity planning via:

| Metric | Description |
|--------|-------------|
| `aqli.resource.cpu.process.load` | JVM process CPU load (0-1) |
| `aqli.resource.cpu.system.load` | System-wide CPU load (0-1) |
| `aqli.resource.memory.heap.used` | JVM heap used (bytes) |
| `aqli.resource.memory.heap.max` | JVM heap max (bytes) |
| `aqli.inference.request.cpu.seconds` | CPU time per request (histogram) |
| `aqli.inference.request.memory.delta` | Heap delta per request (histogram) |
| `aqli.inference.duration` | Request latency (histogram) |
| `aqli.inference.ttft` | Time-to-first-token (histogram) |
| `aqli.inference.tpot` | Time-per-output-token (histogram) |

Metrics are exported via Micrometer (Prometheus-compatible) when running as a server.

## Logging

Aqli writes structured JSON logs to `~/.aqli/logs/aqli.log` by default.

```bash
# Override log directory
export AQLI_LOG_DIR=/var/log/aqli
aqli start
```

The JSON format is compatible with Fluentd, Logstash, Loki, and Elasticsearch.

## Project Structure

```
Families/aqli/
├── framework/
│   ├── core/           # aqli-core: inference orchestration
│   ├── observability/  # Metrics, tracing, logging (Micrometer + OTel)
│   ├── sdk/            # aqli-sdk, aqli-sdk-core, aqli-sdk-api
│   └── spi/            # Service Provider Interfaces
├── runtime/
│   ├── plugins/        # Dynamic runner plugins
│   └── runner/         # GGUF, SafeTensor, LiteRT, ONNX runners
├── ui/
│   └── aqli-cli/     # Quarkus-based CLI (JVM + native image)
├── scripts/            # install.sh, prepare-llama-source.sh, etc.
└── docs/               # Extended documentation
```

## Docs

- [Quickstart](docs/QUICKSTART.md)
- [API Quick Reference](docs/API_QUICK_REFERENCE.md)
- [Observability Guide](docs/OBSERVABILITY.md)
- [API Examples](docs/API_EXAMPLES.md)
- [Error Codes](docs/error-codes.md)
- [Install Profiles](docs/install-profiles.md)

## License

MIT © Kayys.tech



================================================
FILE: available_projects.txt
================================================
:aljabr
:core
:core
:core:aqli-adapter
:core:aqli-adapter
:core:aqli-core
:core:aqli-core
:core:aqli-model-repo-hf
:core:aqli-model-repo-hf
:core:aqli-model-repo-kaggle
:core:aqli-model-repo-kaggle
:core:aqli-model-repo-local
:core:aqli-model-repo-local
:core:aqli-tokenizer-core
:core:aqli-tokenizer-core
:observability
:observability
:observability:aqli-observability
:observability:aqli-observability
:optimization
:optimization
:optimization:aqli-plugin-elastic-ep
:optimization:aqli-plugin-elastic-ep
:optimization:aqli-plugin-fa3
:optimization:aqli-plugin-fa3
:optimization:aqli-plugin-fa4
:optimization:aqli-plugin-fa4
:optimization:aqli-plugin-kv-cache
:optimization:aqli-plugin-kv-cache
:optimization:aqli-plugin-paged-attention
:optimization:aqli-plugin-paged-attention
:optimization:aqli-plugin-qlora
:optimization:aqli-plugin-qlora
:plugin
:plugin
:plugin:aqli-plugin-core
:plugin:aqli-plugin-core
:plugin:aqli-plugin-kernel-core
:plugin:aqli-plugin-kernel-core
:plugin:aqli-plugin-optimization-core
:plugin:aqli-plugin-optimization-core
:plugin:aqli-plugin-runner-core
:plugin:aqli-plugin-runner-core
:plugin:aqli-plugin-runner-gguf
:plugin:aqli-plugin-runner-gguf
:plugins
:plugins
:plugins:log-parser
:plugins:log-parser
:plugins:wayang-plugin-anthropic
:plugins:wayang-plugin-anthropic
:plugins:wayang-plugin-cerebras
:plugins:wayang-plugin-cerebras
:plugins:wayang-plugin-gemini
:plugins:wayang-plugin-gemini
:plugins:wayang-plugin-mistral
:plugins:wayang-plugin-mistral
:plugins:wayang-plugin-openai
:plugins:wayang-plugin-openai
:repository
:repository
:repository:aqli-model-database
:repository:aqli-model-database
:repository:aqli-model-repo-common
:repository:aqli-model-repo-common
:runner
:runner
:runner:diffuser
:runner:diffuser
:runner:diffuser:aqli-diffuser
:runner:diffuser:aqli-diffuser
:runner:gguf
:runner:gguf
:runner:gguf:aqli-gguf-feature-text
:runner:gguf:aqli-gguf-feature-text
:runner:gguf:aqli-plugin-runner-gguf
:runner:gguf:aqli-plugin-runner-gguf
:runner:gguf:aqli-runner-gguf
:runner:gguf:aqli-runner-gguf
:runner:aqli-diffusion
:runner:aqli-diffusion
:runner:litert
:runner:litert
:runner:litert:aqli-litert-core
:runner:litert:aqli-litert-core
:runner:litert:aqli-plugin-runner-litert
:runner:litert:aqli-plugin-runner-litert
:runner:litert:aqli-runner-litert
:runner:litert:aqli-runner-litert
:runner:onnx
:runner:onnx
:runner:onnx:aqli-plugin-runner-onnx
:runner:onnx:aqli-plugin-runner-onnx
:runner:onnx:aqli-runner-onnx
:runner:onnx:aqli-runner-onnx
:runner:safetensor
:runner:safetensor
:runner:safetensor:aqli-runner-safetensor
:runner:safetensor:aqli-runner-safetensor
:runner:safetensor:aqli-runner-stable-diffusion
:runner:safetensor:aqli-runner-stable-diffusion
:runner:safetensor:aqli-safetensor-engine
:runner:safetensor:aqli-safetensor-engine
:runner:tensorrt
:runner:tensorrt
:runner:tensorrt:aqli-plugin-runner-tensorrt
:runner:tensorrt:aqli-plugin-runner-tensorrt
:runner:tensorrt:aqli-runner-tensorrt
:runner:tensorrt:aqli-runner-tensorrt
:runner:torch
:runner:torch
:runner:torch:aqli-runner-libtorch
:runner:torch:aqli-runner-libtorch
:runtime
:runtime
:runtime:aqli-runtime-core
:runtime:aqli-runtime-core
:runtime:aqli-runtime-distributed
:runtime:aqli-runtime-distributed
:sdk
:sdk
:sdk:aqli-sdk
:sdk:aqli-sdk
:sdk:aqli-sdk-api
:sdk:aqli-sdk-api
:sdk:aqli-sdk-core
:sdk:aqli-sdk-core
:sdk:aqli-sdk-local
:sdk:aqli-sdk-local
:sdk:aqli-sdk-protobuf
:sdk:aqli-sdk-protobuf
:sdk:aqli-sdk-remote
:sdk:aqli-sdk-remote
:spi
:spi
:spi:aqli-spi
:spi:aqli-spi
:spi:aqli-spi-inference
:spi:aqli-spi-inference
:spi:aqli-spi-multimodal
:spi:aqli-spi-multimodal
:spi:aqli-spi-plugin
:spi:aqli-spi-plugin
:spi:aqli-spi-runner
:spi:aqli-spi-runner
:spi:aqli-spi-runtime
:spi:aqli-spi-runtime
:suling
:ui
:ui
:ui:aqli-api
:ui:aqli-api
:ui:aqli-cli
:ui:aqli-cli
:ui:aqli-sdk-protobuf
:ui:aqli-sdk-protobuf



================================================
FILE: build-macos.sh
================================================
#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/build-macos.sh"

if [[ ! -x "$SCRIPT_PATH" ]]; then
  echo "Missing $SCRIPT_PATH"
  echo "Run ./create-builder-script.sh to generate it."
  exit 1
fi

exec "$SCRIPT_PATH" "$@"



================================================
FILE: build.gradle.kts
================================================
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

buildscript {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath("io.smallrye:jandex:3.5.3")
    }
}

plugins {
    java
    `maven-publish`
    id("io.quarkus") version "3.32.2" apply false
}

extra["aqliVersion"] = "0.1.0-SNAPSHOT"
extra["quarkusVersion"] = "3.32.2"

allprojects {
    group = "tech.kayys.aqli"
    version = rootProject.extra["aqliVersion"] as String

    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    val quarkusVersion = rootProject.extra["quarkusVersion"] as String
    val mutinyVersion = "2.5.5"
    val smallryeMutinyVertxVersion = "3.15.1"
    val caffeineVersion = "3.1.8"
    val commonsCollectionsVersion = "4.4"
    val jakartaValidationVersion = "3.0.2"
    val jakartaEnterpriseVersion = "4.0.1"
    val jakartaInjectVersion = "2.0.1"
    val reactiveStreamsVersion = "1.0.4"
    val jbossLoggingVersion = "3.6.1.Final"
    val jacksonVersion = "2.16.1"
    val junitJupiterVersion = "5.10.2"
    val junitPlatformVersion = "1.10.2"
    val mockitoJupiterVersion = "5.14.2"
    val restAssuredVersion = "5.3.2"
    val awaitilityVersion = "4.2.0"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        // Common logging and util dependencies can go here
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
        // Make JUnit Jupiter + AssertJ available to every subproject test classpath
        add("testImplementation", platform("org.junit:junit-bom:$junitJupiterVersion"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testImplementation", "org.assertj:assertj-core:3.25.3")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Enable preview features if needed for Java 21 FFM
        options.compilerArgs.add("--enable-preview")
        options.compilerArgs.add("--add-modules=jdk.incubator.vector")
    }

    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--enable-preview",
            "--add-modules=jdk.incubator.vector",
            "--enable-native-access=ALL-UNNAMED",
        )
        useJUnitPlatform()
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs(
            "--enable-preview",
            "--add-modules=jdk.incubator.vector",
            "--enable-native-access=ALL-UNNAMED",
        )
    }

    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val mainSourceSet = sourceSets.named("main")
    val jandexOutputDir = layout.buildDirectory.dir("generated/jandex/main")

    val generateJandexIndex = tasks.register("generateJandexIndex") {
        dependsOn(tasks.named("compileJava"), tasks.named("processResources"))

        val classesDirs = mainSourceSet.map { it.output.classesDirs }
        val indexFile = jandexOutputDir.map { it.file("META-INF/jandex.idx") }

        inputs.files(classesDirs)
        outputs.file(indexFile)

        doLast {
            val indexer = org.jboss.jandex.Indexer()

            classesDirs.get().files
                .filter { it.exists() }
                .forEach { classesDir ->
                    classesDir.resolve("META-INF/jandex.idx").let { staleIndex ->
                        if (staleIndex.exists()) {
                            staleIndex.delete()
                        }
                    }
                    classesDir.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .forEach { classFile ->
                            classFile.inputStream().use(indexer::index)
                        }
                }

            val indexOutput = indexFile.get().asFile
            indexOutput.parentFile.mkdirs()
            indexOutput.outputStream().use { output ->
                org.jboss.jandex.IndexWriter(output).write(indexer.complete())
            }
        }
    }

    mainSourceSet.configure {
        output.dir(mapOf("builtBy" to generateJandexIndex), jandexOutputDir)
    }

    tasks.named("classes") {
        dependsOn(generateJandexIndex)
    }

    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("tech.kayys.aljabr:aljabr-tensor")).using(module("tech.kayys.aljabr:aljabr-tensor:0.1.1"))
            substitute(module("tech.kayys.aljabr:tafkir-ml-core")).using(module("tech.kayys.tafkir:tafkir-ml-core:0.1.0-SNAPSHOT"))
        }
        resolutionStrategy.eachDependency {
            if (requested.group == "io.quarkus" && requested.version.isNullOrBlank()) {
                useVersion(quarkusVersion)
                because("Align Quarkus dependency versions across the Gradle migration")
            }
            if (requested.group == "io.smallrye.reactive"
                && requested.name == "mutiny"
                && requested.version.isNullOrBlank()) {
                useVersion(mutinyVersion)
                because("Align Mutiny versions across mixed legacy Gradle modules")
            }
            if (requested.group == "io.smallrye.reactive"
                && requested.name == "smallrye-mutiny-vertx-web-client"
                && requested.version.isNullOrBlank()) {
                useVersion(smallryeMutinyVertxVersion)
                because("Align SmallRye Vert.x client versions across Gradle modules")
            }
            if (requested.group == "com.github.ben-manes.caffeine"
                && requested.name == "caffeine"
                && requested.version.isNullOrBlank()) {
                useVersion(caffeineVersion)
                because("Align Caffeine versions across mixed legacy Gradle modules")
            }
            if (requested.group == "org.apache.commons"
                && requested.name == "commons-collections4"
                && requested.version.isNullOrBlank()) {
                useVersion(commonsCollectionsVersion)
                because("Align Commons Collections versions across mixed legacy Gradle modules")
            }
            if (requested.group == "jakarta.validation"
                && requested.name == "jakarta.validation-api"
                && requested.version.isNullOrBlank()) {
                useVersion(jakartaValidationVersion)
                because("Align Jakarta Validation versions across mixed legacy Gradle modules")
            }
            if (requested.group == "jakarta.enterprise"
                && requested.name == "jakarta.enterprise.cdi-api"
                && requested.version.isNullOrBlank()) {
                useVersion(jakartaEnterpriseVersion)
                because("Align Jakarta CDI versions across mixed legacy Gradle modules")
            }
            if (requested.group == "jakarta.inject"
                && requested.name == "jakarta.inject-api"
                && requested.version.isNullOrBlank()) {
                useVersion(jakartaInjectVersion)
                because("Align Jakarta Inject versions across mixed legacy Gradle modules")
            }
            if (requested.group == "org.reactivestreams"
                && requested.name == "reactive-streams"
                && requested.version.isNullOrBlank()) {
                useVersion(reactiveStreamsVersion)
                because("Align Reactive Streams versions across mixed legacy Gradle modules")
            }
            if (requested.group == "org.jboss.logging"
                && requested.name == "jboss-logging"
                && requested.version.isNullOrBlank()) {
                useVersion(jbossLoggingVersion)
                because("Align JBoss Logging versions across mixed legacy Gradle modules")
            }
            if (requested.group == "com.fasterxml.jackson.core"
                && (requested.name == "jackson-core" || requested.name == "jackson-databind")
                && requested.version.isNullOrBlank()) {
                useVersion(jacksonVersion)
                because("Align Jackson core versions across mixed legacy Gradle modules")
            }
            if (requested.group == "com.fasterxml.jackson.datatype"
                && requested.name == "jackson-datatype-jsr310"
                && requested.version.isNullOrBlank()) {
                useVersion(jacksonVersion)
                because("Align Jackson datatype versions across mixed legacy Gradle modules")
            }
            if (requested.group == "org.junit.jupiter"
                && requested.name.startsWith("junit-jupiter")
                && requested.version.isNullOrBlank()) {
                useVersion(junitJupiterVersion)
                because("Align JUnit Jupiter versions across mixed legacy Gradle modules")
            }
            if (requested.group == "org.mockito"
                && requested.name == "mockito-junit-jupiter"
                && requested.version.isNullOrBlank()) {
                useVersion(mockitoJupiterVersion)
                because("Align Mockito JUnit integration versions across mixed legacy Gradle modules")
            }
            if (requested.group == "io.rest-assured"
                && requested.name == "rest-assured"
                && requested.version.isNullOrBlank()) {
                useVersion(restAssuredVersion)
                because("Align REST Assured versions for test dependencies")
            }
            if (requested.group == "org.awaitility"
                && requested.name == "awaitility"
                && requested.version.isNullOrBlank()) {
                useVersion(awaitilityVersion)
                because("Align Awaitility versions for test dependencies")
            }
            if (requested.group == "org.mockito"
                && requested.name == "mockito-core"
                && requested.version.isNullOrBlank()) {
                useVersion(mockitoJupiterVersion)
                because("Align Mockito core versions across mixed legacy Gradle modules")
            }
        }
    }

    afterEvaluate {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                mavenLocal()
            }
            if (publications.findByName("mavenJava") == null) {
                publications.create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }
    }
}





================================================
FILE: build.sh
================================================
#!/bin/bash
# Aqli - Project Build Script
# This script handles cleaning, building, and installing Aqli modules.
# Usage: ./build.sh [options]

set -e

# --- Visuals ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}--------------------------------------------------${NC}"
echo -e "${GREEN} Aqli Build Utility ${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"

# --- Default Options ---
CLEAN=false
SKIP_TESTS=true
NATIVE=false
INSTALL_LIBS=false
OFFLINE=""
THREADS="1C"

usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -c, --clean         Run 'mvn clean' before building"
    echo "  -t, --tests         Run tests (default: skipped)"
    echo "  -n, --native        Build native images (Quarkus native profile)"
    echo "  -l, --libs          Install native libraries using Makefile.native"
    echo "  -o, --offline       Run Maven in offline mode"
    echo "  -j, --threads <n>   Number of threads for Maven (default: 1C)"
    echo "  -h, --help          Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -c               # Clean and build everything (skip tests)"
    echo "  $0 -n               # Build native executables"
    echo "  $0 -c -l            # Clean build and install native libs"
    exit 0
}

# --- Parse Arguments ---
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -c|--clean) CLEAN=true ;;
        -t|--tests) SKIP_TESTS=false ;;
        -n|--native) NATIVE=true ;;
        -l|--libs) INSTALL_LIBS=true ;;
        -o|--offline) OFFLINE="-o" ;;
        -j|--threads) THREADS="$2"; shift ;;
        -h|--help) usage ;;
        *) echo -e "${RED}Unknown parameter: $1${NC}"; usage ;;
    esac
    shift
done

# --- 1. Java 25 Verification ---
echo -e "${BLUE}🔍 Checking Java environment...${NC}"
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q 'version "25'; then
    echo -e "${YELLOW}⚠ Java 25 is required but not found or not active.${NC}"
    
    # Try to load SDKMAN
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"
        if sdk current java | grep -q '25'; then
            echo -e "${GREEN}✓ Loaded Java 25 via SDKMAN${NC}"
        else
            echo -e "${RED}❌ Java 25 is not available in SDKMAN. Please run ./install.sh first.${NC}"
            exit 1
        fi
    else
        echo -e "${RED}❌ Please ensure Java 25 is installed and active.${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}✓ Found $(java -version 2>&1 | head -n 1)${NC}"
fi

# --- 2. Build Error Codes (if script exists) ---
if [ -f "./scripts/generate-error-codes.sh" ]; then
    echo -e "${BLUE}>>> Generating error codes...${NC}"
    ./scripts/generate-error-codes.sh
fi

# --- 3. Maven Build ---
MVN_CMD="mvn"
MVN_ARGS="-T $THREADS $OFFLINE"

if [ "$CLEAN" = true ]; then
    MVN_ARGS="$MVN_ARGS clean"
fi

MVN_ARGS="$MVN_ARGS install"

if [ "$SKIP_TESTS" = true ]; then
    MVN_ARGS="$MVN_ARGS -DskipTests"
fi

if [ "$NATIVE" = true ]; then
    echo -e "${YELLOW}ℹ Building native images (this may take a while)...${NC}"
    MVN_ARGS="$MVN_ARGS -Pnative -Dquarkus.native.container-build=false"
fi

echo -e "${BLUE}>>> Running build: $MVN_CMD $MVN_ARGS${NC}"
$MVN_CMD $MVN_ARGS

# --- 4. Install Native Libraries ---
if [ "$INSTALL_LIBS" = true ]; then
    if [ -f "Makefile.native" ]; then
        echo -e "${BLUE}>>> Installing native libraries...${NC}"
        make -f Makefile.native install-native-libs
    else
        echo -e "${YELLOW}⚠ Makefile.native not found, skipping library installation.${NC}"
    fi
fi

echo ""
echo -e "${GREEN}✓ Build process completed successfully!${NC}"
echo -e "${BLUE}--------------------------------------------------${NC}"



================================================
FILE: create-builder-script.sh
================================================
#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"

mkdir -p "$SCRIPTS_DIR"

RESET="$(printf '\033[0m')"
BOLD="$(printf '\033[1m')"
DIM="$(printf '\033[2m')"
GREEN="$(printf '\033[32m')"
YELLOW="$(printf '\033[33m')"
BLUE="$(printf '\033[34m')"
MAGENTA="$(printf '\033[35m')"
CYAN="$(printf '\033[36m')"
REVERSE="$(printf '\033[7m')"

cleanup_terminal() {
  if [[ -n "${ORIGINAL_STTY:-}" ]]; then
    stty "$ORIGINAL_STTY" < /dev/tty 2>/dev/null || true
  fi
  tput cnorm 2>/dev/null || true
  printf '%s' "$RESET"
}

trap cleanup_terminal EXIT

if [[ ! -t 0 || ! -t 1 ]]; then
  echo ":| This script needs an interactive terminal." >&2
  exit 1
fi

if ! command -v tput >/dev/null 2>&1; then
  echo ":( 'tput' is required for the terminal checklist UI." >&2
  exit 1
fi

ORIGINAL_STTY="$(stty -g < /dev/tty 2>/dev/null || true)"

BACKEND_VALUES=(metal cuda vulkan opencl all)
BACKEND_LABELS=(
  "Metal (macOS, CPU)"
  "CUDA (Windows, Linux, etc.)"
  "Vulkan (Linux, Windows, etc.)"
  "OpenCL (Linux, Windows, etc.)"
  "All"
)

FORMAT_VALUES=(safetensor gguf litert onnx all)
FORMAT_LABELS=(
  "Safetensor"
  "GGUF"
  "LiteRT"
  "ONNX"
  "All"
)

LLM_VALUES=(text vision audio multimodal stable-diffusion whispering all)
LLM_LABELS=(
  "Text"
  "Vision (image, video, 3d)"
  "Audio (speech, music)"
  "Multimodal (image+text, video+text, etc.)"
  "Stable Diffusion (image, video, etc.)"
  "Whispering (audio)"
  "All"
)

ARCH_VALUES=(native-java binding)
ARCH_LABELS=("Native Java" "Binding")

PROFILE_VALUES=(release snapshot)
PROFILE_LABELS=("Release" "Snapshot")

ALWAYS_MODULES=(
  ":core:aqli-core"
  ":core:aqli-error-code"
  ":core:aqli-model-repository"
  ":core:aqli-provider-core"
  ":core:aqli-tokenizer-core"
  ":sdk:aqli-sdk"
  ":sdk:aqli-sdk-api"
  ":sdk:aqli-sdk-core"
  ":sdk:aqli-sdk-local"
  ":sdk:aqli-sdk-session"
  ":spi:aqli-spi"
  ":spi:aqli-spi-inference"
  ":spi:aqli-spi-model"
  ":spi:aqli-spi-multimodal"
  ":spi:aqli-spi-plugin"
  ":spi:aqli-spi-provider"
  ":spi:aqli-spi-runtime"
  ":ui:aqli-api"
  ":ui:aqli-cli"
)

BACKEND_PROPERTY=()
PLATFORM_TAGS=()
MODULES=()
WARNINGS=()

append_unique() {
  local target="$1"
  local item="$2"
  eval "local current=(\"\${${target}[@]-}\")"
  local existing
  for existing in "${current[@]}"; do
    [[ "$existing" == "$item" ]] && return 0
  done
  eval "${target}+=(\"\$item\")"
}

append_many_unique() {
  local target="$1"
  shift
  local item
  for item in "$@"; do
    append_unique "$target" "$item"
  done
}

join_by() {
  local sep="$1"
  shift
  local out=""
  local item
  for item in "$@"; do
    if [[ -z "$out" ]]; then
      out="$item"
    else
      out="${out}${sep}${item}"
    fi
  done
  printf '%s' "$out"
}

contains_value() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

read_key() {
  local key rest
  stty -echo -icanon time 0 min 0 < /dev/tty
  while true; do
    if IFS= read -rsn1 key < /dev/tty; then
      break
    fi
    sleep 0.01
  done
  if [[ "$key" == $'\x1b' ]]; then
    IFS= read -rsn2 rest < /dev/tty || true
    key+="$rest"
  fi
  stty "$ORIGINAL_STTY" < /dev/tty 2>/dev/null || true
  printf '%s' "$key"
}

draw_menu() {
  local title="$1"
  local subtitle="$2"
  local kind="$3"
  local current="$4"
  shift 4
  local values=("$@")
  local labels_var="${title}_labels"
  local -a labels=()

  case "$title" in
    backend) labels=("${BACKEND_LABELS[@]}") ;;
    format) labels=("${FORMAT_LABELS[@]}") ;;
    llm) labels=("${LLM_LABELS[@]}") ;;
    architecture) labels=("${ARCH_LABELS[@]}") ;;
    profile) labels=("${PROFILE_LABELS[@]}") ;;
  esac

  clear
  printf '%s(•‿•) %s%s\n' "$BOLD$CYAN" "$subtitle" "$RESET"
  printf '%sUse Up/Down to move, Space to toggle, Enter to confirm.%s\n\n' "$DIM$MAGENTA" "$RESET"

  local i value label mark line_style
  for ((i = 0; i < ${#values[@]}; i++)); do
    value="${values[$i]}"
    label="${labels[$i]}"

    if [[ "$kind" == "multi" ]]; then
      if contains_value "$value" "${MENU_SELECTED[@]}"; then
        mark="[x]"
      else
        mark="[ ]"
      fi
    else
      if [[ "${MENU_SELECTED[0]-}" == "$value" ]]; then
        mark="(*)"
      else
        mark="( )"
      fi
    fi

    if [[ "$i" -eq "$current" ]]; then
      line_style="$REVERSE$BOLD"
      printf '%s> %s %s%s\n' "$line_style" "$mark" "$label" "$RESET"
    elif contains_value "$value" "${MENU_SELECTED[@]}"; then
      printf '%s  %s %s%s\n' "$GREEN" "$mark" "$label" "$RESET"
    else
      printf '  %s %s\n' "$mark" "$label"
    fi
  done

  printf '\n%s:) Selected:%s %s\n' "$YELLOW" "$RESET" "$(join_by ", " "${MENU_SELECTED[@]}")"
}

run_menu() {
  local menu_name="$1"
  local subtitle="$2"
  local kind="$3"
  local default_csv="$4"
  shift 4
  local values=("$@")

  MENU_SELECTED=()
  IFS=',' read -r -a MENU_SELECTED <<<"$default_csv"
  local cursor=0

  tput civis 2>/dev/null || true

  while true; do
    draw_menu "$menu_name" "$subtitle" "$kind" "$cursor" "${values[@]}"
    local key
    key="$(read_key)"

    case "$key" in
      $'\x1b[A'|k)
        cursor=$(( (cursor - 1 + ${#values[@]}) % ${#values[@]} ))
        ;;
      $'\x1b[B'|j)
        cursor=$(( (cursor + 1) % ${#values[@]} ))
        ;;
      " ")
        local value="${values[$cursor]}"
        if [[ "$kind" == "multi" ]]; then
          if [[ "$value" == "all" ]]; then
            MENU_SELECTED=("all")
          else
            local next=()
            local item found=0
            for item in "${MENU_SELECTED[@]}"; do
              [[ "$item" == "all" ]] && continue
              if [[ "$item" == "$value" ]]; then
                found=1
              else
                next+=("$item")
              fi
            done
            if [[ "$found" -eq 0 ]]; then
              next+=("$value")
            fi
            if [[ "${#next[@]}" -eq 0 ]]; then
              MENU_SELECTED=("$value")
            else
              MENU_SELECTED=("${next[@]}")
            fi
          fi
        else
          MENU_SELECTED=("$value")
        fi
        ;;
      ""|$'\n'|$'\r')
        if [[ "$kind" == "single" && "${#MENU_SELECTED[@]}" -eq 0 ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        elif [[ "$kind" == "single" ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        elif [[ "${#MENU_SELECTED[@]}" -eq 0 ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        fi
        break
        ;;
    esac
  done
}

expand_all_multi() {
  local values_name="$1"
  local selected_name="$2"
  eval "local selected=(\"\${${selected_name}[@]}\")"
  if contains_value "all" "${selected[@]}"; then
    local expanded=()
    case "$values_name" in
      BACKEND_VALUES) expanded=("${BACKEND_VALUES[@]}") ;;
      FORMAT_VALUES) expanded=("${FORMAT_VALUES[@]}") ;;
      LLM_VALUES) expanded=("${LLM_VALUES[@]}") ;;
    esac
    local filtered=()
    local item
    for item in "${expanded[@]}"; do
      [[ "$item" == "all" ]] && continue
      filtered+=("$item")
    done
    eval "${selected_name}=(\"\${filtered[@]}\")"
  fi
}

resolve_backend() {
  local backend="$1"
  case "$backend" in
    metal)
      append_many_unique BACKEND_PROPERTY cpu metal
      append_many_unique PLATFORM_TAGS macos cpu
      append_many_unique MODULES \
        ":backend:metal:aqli-backend-metal" \
        ":backend:metal:aqli-mlx-binding" \
        ":core:plugin:aqli-plugin-kernel-core"
      ;;
    cuda)
      append_many_unique BACKEND_PROPERTY cpu cuda
      append_many_unique PLATFORM_TAGS linux windows
      append_many_unique MODULES \
        ":backend:cuda:aqli-backend-cuda" \
        ":backend:cuda:aqli-kernel-cuda" \
        ":backend:cuda:aqli-plugin-kernel-cuda" \
        ":core:plugin:aqli-plugin-kernel-core"
      ;;
    vulkan)
      append_many_unique BACKEND_PROPERTY cpu
      append_many_unique PLATFORM_TAGS linux windows
      append_unique WARNINGS "Backend 'vulkan' has no dedicated modules in the current repo."
      ;;
    opencl)
      append_many_unique BACKEND_PROPERTY cpu
      append_many_unique PLATFORM_TAGS linux windows
      append_unique WARNINGS "Backend 'opencl' has no dedicated modules in the current repo."
      ;;
  esac
}

resolve_format() {
  local format="$1"
  case "$format" in
    safetensor)
      append_many_unique MODULES \
        ":runner:safetensor:aqli-runner-safetensor" \
        ":runner:safetensor:aqli-runner-stable-diffusion" \
        ":runner:safetensor:aqli-safetensor-api" \
        ":runner:safetensor:aqli-safetensor-core" \
        ":runner:safetensor:aqli-safetensor-engine" \
        ":runner:safetensor:aqli-safetensor-loader" \
        ":runner:safetensor:aqli-safetensor-quantization" \
        ":runner:safetensor:aqli-safetensor-spi"
      ;;
    gguf)
      append_many_unique MODULES \
        ":core:plugin:aqli-plugin-runner-gguf" \
        ":runner:gguf:aqli-gguf-converter" \
        ":runner:gguf:aqli-gguf-converter-java" \
        ":runner:gguf:aqli-gguf-core"
      ;;
    litert)
      append_many_unique MODULES \
        ":runner:litert:aqli-litert-core" \
        ":runner:litert:aqli-plugin-runner-litert" \
        ":runner:litert:aqli-runner-litert"
      ;;
    onnx)
      append_many_unique MODULES \
        ":runner:onnx:aqli-ml-export-onnx" \
        ":runner:onnx:aqli-ml-onnx" \
        ":runner:onnx:aqli-plugin-runner-onnx" \
        ":runner:onnx:aqli-runner-onnx"
      ;;
  esac
}

resolve_llm() {
  local llm="$1"
  case "$llm" in
    text)
      append_many_unique MODULES \
        ":models:aqli-model-gemma" \
        ":models:aqli-model-llama" \
        ":models:aqli-model-mistral" \
        ":models:aqli-model-phi" \
        ":models:aqli-model-qwen" \
        ":core:aqli-model-runner" \
        ":core:aqli-model-repo-hf" \
        ":core:aqli-model-repo-local"
      ;;
    vision)
      append_many_unique MODULES \
        ":ml:aqli-ml-cnn" \
        ":ml:aqli-ml-vision"
      ;;
    audio)
      append_many_unique MODULES \
        ":ml:aqli-ml-nlp" \
        ":ml:aqli-ml-audio"
      ;;
    multimodal)
      append_many_unique MODULES \
        ":models:aqli-model-qwen" \
        ":spi:aqli-spi-multimodal" \
        ":ml:aqli-ml-multimodal"
      ;;
    stable-diffusion)
      append_many_unique MODULES \
        ":runner:diffuser:aqli-diffuser" \
        ":runner:safetensor:aqli-runner-stable-diffusion"
      ;;
    whispering)
      append_many_unique MODULES \
        ":runner:onnx:aqli-runner-onnx" \
        ":ml:aqli-ml-nlp" \
        ":ml:aqli-ml-audio"
      ;;
  esac
}

resolve_architecture() {
  local arch="$1"
  case "$arch" in
    native-java)
      append_many_unique MODULES \
        ":core:aqli-tensor" \
        ":core:aqli-model-repo-hf" \
        ":core:aqli-model-repo-kaggle" \
        ":core:aqli-model-repo-local" \
        ":runner:gguf:aqli-gguf-core" \
        ":runner:safetensor:aqli-safetensor-core" \
        ":runner:safetensor:aqli-safetensor-engine"
      ;;
    binding)
      append_many_unique MODULES \
        ":backend:metal:aqli-mlx-binding" \
        ":runner:litert:aqli-litert-core" \
        ":runner:onnx:aqli-runner-onnx"
      ;;
  esac
}

sort_unique_lines() {
  printf '%s\n' "$@" | awk 'NF && !seen[$0]++' | sort
}

run_menu backend "Backend targeted" multi "metal" "${BACKEND_VALUES[@]}"
BACKEND_TARGETS=("${MENU_SELECTED[@]}")

run_menu format "Model format targeted" multi "all" "${FORMAT_VALUES[@]}"
FORMAT_TARGETS=("${MENU_SELECTED[@]}")

run_menu llm "LLM type" multi "all" "${LLM_VALUES[@]}"
LLM_TARGETS=("${MENU_SELECTED[@]}")

run_menu architecture "Model architecture" multi "native-java,binding" "${ARCH_VALUES[@]}"
ARCHITECTURE_TARGETS=("${MENU_SELECTED[@]}")
if [[ "${#ARCHITECTURE_TARGETS[@]}" -eq 0 ]]; then
  ARCHITECTURE_TARGETS=("native-java" "binding")
fi

run_menu profile "Other options" single "snapshot" "${PROFILE_VALUES[@]}"
BUILD_PROFILE="${MENU_SELECTED[0]}"

expand_all_multi BACKEND_VALUES BACKEND_TARGETS
expand_all_multi FORMAT_VALUES FORMAT_TARGETS
expand_all_multi LLM_VALUES LLM_TARGETS

append_many_unique MODULES "${ALWAYS_MODULES[@]}"

for item in "${BACKEND_TARGETS[@]}"; do
  resolve_backend "$item"
done

for item in "${FORMAT_TARGETS[@]}"; do
  resolve_format "$item"
done

for item in "${LLM_TARGETS[@]}"; do
  resolve_llm "$item"
done

for item in "${ARCHITECTURE_TARGETS[@]}"; do
  resolve_architecture "$item"
done

BACKEND_TARGETS_SORTED=($(sort_unique_lines "${BACKEND_TARGETS[@]-}"))
FORMAT_TARGETS_SORTED=($(sort_unique_lines "${FORMAT_TARGETS[@]-}"))
LLM_TARGETS_SORTED=($(sort_unique_lines "${LLM_TARGETS[@]-}"))
ARCHITECTURE_TARGETS_SORTED=($(sort_unique_lines "${ARCHITECTURE_TARGETS[@]-}"))
MODULES_SORTED=($(sort_unique_lines "${MODULES[@]-}"))
WARNINGS_SORTED=($(sort_unique_lines "${WARNINGS[@]-}"))

RESOLVED_BACKEND_PROPERTY="$(join_by "," "${BACKEND_PROPERTY[@]-}")"
if [[ -z "$RESOLVED_BACKEND_PROPERTY" ]]; then
  RESOLVED_BACKEND_PROPERTY="cpu"
fi

BACKEND_SLUG="$(join_by "-" "${BACKEND_TARGETS_SORTED[@]}")"
FORMAT_SLUG="$(join_by "-" "${FORMAT_TARGETS_SORTED[@]}")"
LLM_SLUG="$(join_by "-" "${LLM_TARGETS_SORTED[@]}")"
ARCHITECTURE_TARGET_CSV="$(join_by "," "${ARCHITECTURE_TARGETS_SORTED[@]}")"
ARCHITECTURE_SLUG="$(join_by "-" "${ARCHITECTURE_TARGETS_SORTED[@]}")"
SCRIPT_SLUG="$(join_by "-" "$BACKEND_SLUG" "$FORMAT_SLUG" "$LLM_SLUG" "$ARCHITECTURE_SLUG")"

SELECTION_FILE="$SCRIPTS_DIR/module-selection-current.env"
BUILD_SCRIPT="$SCRIPTS_DIR/build-macos.sh"
INSTALL_SCRIPT="$SCRIPTS_DIR/install-local-macos.sh"

{
  printf 'BACKEND_TARGETS="%s"\n' "$(join_by "," "${BACKEND_TARGETS_SORTED[@]}")"
  printf 'FORMAT_TARGETS="%s"\n' "$(join_by "," "${FORMAT_TARGETS_SORTED[@]}")"
  printf 'LLM_TARGETS="%s"\n' "$(join_by "," "${LLM_TARGETS_SORTED[@]}")"
  printf 'ARCHITECTURE_TARGETS="%s"\n' "$ARCHITECTURE_TARGET_CSV"
  printf 'ARCHITECTURE_TARGET="%s"\n' "$ARCHITECTURE_TARGET_CSV"
  printf 'BUILD_PROFILE="%s"\n' "$BUILD_PROFILE"
  printf 'RESOLVED_BACKEND_PROPERTY="%s"\n' "$RESOLVED_BACKEND_PROPERTY"
  printf 'MODULES="%s"\n' "$(join_by " " "${MODULES_SORTED[@]}")"
  printf 'WARNINGS="%s"\n' "$(join_by " | " "${WARNINGS_SORTED[@]}")"
  printf 'SCRIPT_SLUG="%s"\n' "$SCRIPT_SLUG"
} > "$SELECTION_FILE"

{
  echo '#!/bin/bash'
  echo 'set -euo pipefail'
  echo
  echo 'RESET="$(printf '\''\033[0m'\'')"'
  echo 'BOLD="$(printf '\''\033[1m'\'')"'
  echo 'GREEN="$(printf '\''\033[32m'\'')"'
  echo 'CYAN="$(printf '\''\033[36m'\'')"'
  echo 'YELLOW="$(printf '\''\033[33m'\'')"'
  echo 'MAGENTA="$(printf '\''\033[35m'\'')"'
  echo
  echo 'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"'
  echo 'cd "$ROOT_DIR"'
  echo 'source "$ROOT_DIR/scripts/module-selection-current.env"'
  echo 'ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"'
  echo 'if command -v /usr/libexec/java_home >/dev/null 2>&1; then'
  echo '  JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"'
  echo '  if [[ -n "${JAVA_25_HOME}" ]]; then'
  echo '    export JAVA_HOME="${JAVA_25_HOME}"'
  echo '    export PATH="${JAVA_HOME}/bin:${PATH}"'
  echo '  fi'
  echo 'fi'
  echo 'GRADLE_JAVA_HOME_ARG=()'
  echo 'if [[ -n "${JAVA_HOME:-}" ]]; then'
  echo '  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")'
  echo 'fi'
  echo 'append_java_tool_option() {'
  echo '  local option="$1"'
  echo '  case " ${JAVA_TOOL_OPTIONS:-} " in'
  echo '    *" ${option} "*) ;;'
  echo '    *) JAVA_TOOL_OPTIONS="${option}${JAVA_TOOL_OPTIONS:+ ${JAVA_TOOL_OPTIONS}}" ;;'
  echo '  esac'
  echo '}'
  echo 'append_java_tool_option "--enable-native-access=ALL-UNNAMED"'
  echo 'append_java_tool_option "--add-modules=jdk.incubator.vector"'
  echo 'export JAVA_TOOL_OPTIONS'
  echo 'is_legacy_gradle_module() {'
  echo '  case "$1" in'
  echo '    :ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;'
  echo '    *) return 1 ;;'
  echo '  esac'
  echo '}'
  echo 'read -r -a MODULE_ARRAY <<< "${MODULES:-}"'
  echo 'SUPPORTED_MODULES=()'
  echo 'SKIPPED_MODULES=()'
  echo 'for module in "${MODULE_ARRAY[@]}"; do'
  echo '  if is_legacy_gradle_module "$module"; then'
  echo '    SKIPPED_MODULES+=("$module")'
  echo '  else'
  echo '    SUPPORTED_MODULES+=("$module")'
  echo '  fi'
  echo 'done'
  echo 'GRADLE_BUILD_TASKS=(clean)'
  echo 'for module in "${SUPPORTED_MODULES[@]}"; do'
  echo '  GRADLE_BUILD_TASKS+=("${module}:build")'
  echo 'done'
  echo
  echo 'echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"'
  echo 'echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"'
  echo 'echo "${BOLD}${MAGENTA}:) Build mode:${RESET} Gradle-only install"'
  echo 'echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"'
  if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
    echo 'echo "$GREEN:) Module manifest resolved cleanly.$RESET"'
  else
    echo 'IFS=" | " read -r -a WARNINGS_ARR <<< "${WARNINGS}"'
    echo 'for item in "${WARNINGS_ARR[@]}"; do echo "${YELLOW}:| Warning:${RESET} ${item}"; done'
  fi
  echo
  echo 'if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then'
  echo '  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in wrapper build:"'
  echo '  for module in "${SKIPPED_MODULES[@]}"; do'
  echo '    echo "   - ${module}"'
  echo '  done'
  echo 'fi'
  echo
  echo './gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_BUILD_TASKS[@]}" \'
  echo '  -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \'
  echo '  -Paqli.profile="${BUILD_PROFILE}" \'
  echo '  -Paqli.model.formats="${FORMAT_TARGETS}" \'
  echo '  -Paqli.llm.types="${LLM_TARGETS}" \'
  echo '  -Paqli.architecture="${ARCHITECTURE_VALUE}"'
} > "$BUILD_SCRIPT"

{
  echo '#!/bin/bash'
  echo 'set -euo pipefail'
  echo
  echo 'RESET="$(printf '\''\033[0m'\'')"'
  echo 'BOLD="$(printf '\''\033[1m'\'')"'
  echo 'GREEN="$(printf '\''\033[32m'\'')"'
  echo 'CYAN="$(printf '\''\033[36m'\'')"'
  echo 'YELLOW="$(printf '\''\033[33m'\'')"'
  echo 'MAGENTA="$(printf '\''\033[35m'\'')"'
  echo
  echo 'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"'
  echo 'cd "$ROOT_DIR"'
  echo 'source "$ROOT_DIR/scripts/module-selection-current.env"'
  echo 'ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"'
  echo 'if command -v /usr/libexec/java_home >/dev/null 2>&1; then'
  echo '  JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"'
  echo '  if [[ -n "${JAVA_25_HOME}" ]]; then'
  echo '    export JAVA_HOME="${JAVA_25_HOME}"'
  echo '    export PATH="${JAVA_HOME}/bin:${PATH}"'
  echo '  fi'
  echo 'fi'
  echo 'GRADLE_JAVA_HOME_ARG=()'
  echo 'if [[ -n "${JAVA_HOME:-}" ]]; then'
  echo '  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")'
  echo 'fi'
  echo 'append_java_tool_option() {'
  echo '  local option="$1"'
  echo '  case " ${JAVA_TOOL_OPTIONS:-} " in'
  echo '    *" ${option} "*) ;;'
  echo '    *) JAVA_TOOL_OPTIONS="${option}${JAVA_TOOL_OPTIONS:+ ${JAVA_TOOL_OPTIONS}}" ;;'
  echo '  esac'
  echo '}'
  echo 'append_java_tool_option "--enable-native-access=ALL-UNNAMED"'
  echo 'append_java_tool_option "--add-modules=jdk.incubator.vector"'
  echo 'export JAVA_TOOL_OPTIONS'
  echo 'is_legacy_gradle_module() {'
  echo '  case "$1" in'
  echo '    :ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;'
  echo '    *) return 1 ;;'
  echo '  esac'
  echo '}'
  echo 'read -r -a MODULE_ARRAY <<< "${MODULES:-}"'
  echo 'SUPPORTED_MODULES=()'
  echo 'SKIPPED_MODULES=()'
  echo 'for module in "${MODULE_ARRAY[@]}"; do'
  echo '  if is_legacy_gradle_module "$module"; then'
  echo '    SKIPPED_MODULES+=("$module")'
  echo '  else'
  echo '    SUPPORTED_MODULES+=("$module")'
  echo '  fi'
  echo 'done'
  echo 'GRADLE_INSTALL_TASKS=()'
  echo 'for module in "${SUPPORTED_MODULES[@]}"; do'
  echo '  GRADLE_INSTALL_TASKS+=("${module}:publishToMavenLocal")'
  echo 'done'
  echo
  echo 'echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"'
  echo 'echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"'
  echo 'echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"'
  if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
    echo 'echo "$GREEN:) Module manifest resolved cleanly.$RESET"'
  else
    echo 'IFS=" | " read -r -a WARNINGS_ARR <<< "${WARNINGS}"'
    echo 'for item in "${WARNINGS_ARR[@]}"; do echo "${YELLOW}:| Warning:${RESET} ${item}"; done'
  fi
  echo
  echo 'if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then'
  echo '  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in local install wrapper:"'
  echo '  for module in "${SKIPPED_MODULES[@]}"; do'
  echo '    echo "   - ${module}"'
  echo '  done'
  echo 'fi'
  echo
  echo 'PUBLISH_RUNTIME_LOCAL="${AQLI_PUBLISH_RUNTIME_LOCAL:-false}"'
  echo 'export AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES="${AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES:-true}"'
  echo 'echo "${BOLD}${GREEN}:) Step 1/2:${RESET} packaging and installing aqli via the macOS local installer"'
  echo 'echo "${BOLD}${YELLOW}:| Install path:${RESET} ${AQLI_BIN_DIR:-$HOME/.local/bin}/aqli"'
  echo 'bash "$ROOT_DIR/scripts/install-local-runtime.sh"'
  echo
  echo 'echo "${BOLD}${GREEN}:) Step 2/2:${RESET} optional compatibility publish to local artifact cache"'
  echo 'if [[ "${PUBLISH_RUNTIME_LOCAL}" == "true" ]]; then'
  echo '  if [[ ${#GRADLE_INSTALL_TASKS[@]} -gt 0 ]]; then'
  echo '    if ./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_INSTALL_TASKS[@]}" \'
  echo '      -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \'
  echo '      -Paqli.profile="${BUILD_PROFILE}" \'
  echo '      -Paqli.model.formats="${FORMAT_TARGETS}" \'
  echo '      -Paqli.llm.types="${LLM_TARGETS}" \'
  echo '      -Paqli.architecture="${ARCHITECTURE_VALUE}"; then'
  echo '      echo "${GREEN}✓ Published supported runtime artifacts to local artifact cache${RESET}"'
  echo '    else'
  echo '      echo "${YELLOW}⚠ Optional Gradle publish failed after local install; CLI install is still complete.${RESET}"'
  echo '    fi'
  echo '  else'
  echo '    echo "${YELLOW}⚠ No supported Gradle runtime modules were selected for publish.${RESET}"'
  echo '  fi'
  echo 'else'
  echo '  echo "${YELLOW}⚠ Skipping local artifact publish by default. Set AQLI_PUBLISH_RUNTIME_LOCAL=true to enable it.${RESET}"'
  echo 'fi'
  echo 'echo "${BOLD}${GREEN}:D Done.${RESET} Aqli is installed locally, and compatibility publish is optional."'
} > "$INSTALL_SCRIPT"

chmod +x "$BUILD_SCRIPT" "$INSTALL_SCRIPT"

clear
printf '%s:) Generated%s %s\n' "$GREEN" "$RESET" "$BUILD_SCRIPT"
printf '%s:) Generated%s %s\n' "$GREEN" "$RESET" "$INSTALL_SCRIPT"
printf '%s:) Saved selection manifest to%s %s\n' "$CYAN" "$RESET" "$SELECTION_FILE"
if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
  printf '%s:D All manifest selections resolved without warnings.%s\n' "$MAGENTA" "$RESET"
else
  for item in "${WARNINGS_SORTED[@]}"; do
    printf '%s:| Warning:%s %s\n' "$YELLOW" "$RESET" "$item"
  done
fi



================================================
FILE: gradle.properties
================================================
# Aqli Engine Feature Flags
# Comma-separated list of backends to include: cpu, cuda, metal
aqli.backend=cpu,metal
# Strategy for backend selection: fallback, strict
aqli.strategy=fallback

# Enable inference APIs (Runtime, Diffusion, Model)
aqli.inference=true

# Optimization Flags
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# Ignore Kotlin JVM target mismatch for JDK 28 Babylon
kotlin.jvm.target.validation.mode=warning

# Note: Do not hardcode machine-specific local paths like /opt/homebrew here as it breaks CI and other environments.
# To specify Java home locally, use JAVA_HOME env var or pass -Dorg.gradle.java.home=...



================================================
FILE: gradlew
================================================
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/subprojects/plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"



================================================
FILE: gradlew.bat
================================================
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega



================================================
FILE: install-local-macos.sh
================================================
#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/install-local-macos.sh"

if [[ ! -x "$SCRIPT_PATH" ]]; then
  echo "Missing $SCRIPT_PATH"
  echo "Run ./create-builder-script.sh to generate it."
  exit 1
fi

exec "$SCRIPT_PATH" "$@"



================================================
FILE: LICENSE.md
================================================
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [2026] Bhangun Hartani

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.



================================================
FILE: Makefile.native
================================================
# Native Library Installation Makefile
# Helps copy native libraries to standard Aqli location (~/.aqli/libs/)

.PHONY: help install-native-libs install-gguf-libs install-onnx-libs install-libtorch-libs install-litert-libs clean-native-libs

AQLI_LIBS_DIR ?= $(HOME)/.aqli/libs
LLAMA_LIBS_DIR = $(AQLI_LIBS_DIR)/llama
ONNX_LIBS_DIR = $(AQLI_LIBS_DIR)/onnxruntime
LIBTORCH_LIBS_DIR = $(AQLI_LIBS_DIR)/libtorch
TFLITE_LIBS_DIR = $(AQLI_LIBS_DIR)/litert

# Detect OS
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
    LIB_EXT = .dylib
    LIB_PREFIX = lib
else ifeq ($(UNAME_S),Linux)
    LIB_EXT = .so
    LIB_PREFIX = lib
else
    # Windows (MinGW/Cygwin)
    LIB_EXT = .dll
    LIB_PREFIX = 
endif

help:
	@echo "Aqli Native Library Installation"
	@echo "==================================="
	@echo ""
	@echo "Usage:"
	@echo "  make install-native-libs     - Install all native libraries"
	@echo "  make install-gguf-libs       - Install GGUF/llama.cpp libraries"
	@echo "  make install-onnx-libs       - Install ONNX Runtime libraries"
	@echo "  make install-libtorch-libs   - Install LibTorch libraries"
	@echo "  make install-litert-libs     - Install TensorFlow Lite libraries"
	@echo "  make clean-native-libs       - Remove all installed libraries"
	@echo ""
	@echo "Standard location: $(AQLI_LIBS_DIR)"
	@echo ""

# Install all native libraries
install-native-libs: install-gguf-libs install-onnx-libs install-libtorch-libs install-litert-libs
	@echo "✓ All native libraries installed to $(AQLI_LIBS_DIR)"

# Install GGUF / llama.cpp libraries
install-gguf-libs:
	@echo "Installing GGUF/llama.cpp libraries..."
	@mkdir -p $(LLAMA_LIBS_DIR)
	@# Try multiple source locations
	@if [ -d "plugins/runner/gguf/aqli-ext-runner-gguf/src/main/resources/native-libs/cpu" ]; then \
		cp plugins/runner/gguf/aqli-ext-runner-gguf/src/main/resources/native-libs/cpu/*$(LIB_EXT)* $(LLAMA_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "extension/format/gguf/aqli-ext-runner-gguf/target/llama-cpp/lib" ]; then \
		cp extension/format/gguf/aqli-ext-runner-gguf/target/llama-cpp/lib/*$(LIB_EXT)* $(LLAMA_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "plugins/runner/gguf/source/llama-cpp/lib/cpu" ]; then \
		cp plugins/runner/gguf/source/llama-cpp/lib/cpu/*$(LIB_EXT)* $(LLAMA_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "extension/format/gguf/vendor/llama-cpp/lib/cpu" ]; then \
		cp extension/format/gguf/vendor/llama-cpp/lib/cpu/*$(LIB_EXT)* $(LLAMA_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "vendor/llama-cpp/lib/cpu" ]; then \
		cp vendor/llama-cpp/lib/cpu/*$(LIB_EXT)* $(LLAMA_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@# Set executable permissions
	@chmod +x $(LLAMA_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true
	@# Clear macOS quarantine
	@if [ "$(UNAME_S)" = "Darwin" ]; then \
		xattr -dr com.apple.quarantine $(LLAMA_LIBS_DIR) 2>/dev/null || true; \
	fi
	@echo "✓ GGUF libraries installed to $(LLAMA_LIBS_DIR)"
	@ls -lh $(LLAMA_LIBS_DIR)/ 2>/dev/null || echo "  (no libraries found)"

# Install ONNX Runtime libraries
install-onnx-libs:
	@echo "Installing ONNX Runtime libraries..."
	@mkdir -p $(ONNX_LIBS_DIR)
	@# Try multiple source locations
	@if [ -d "plugins/runner/onnx/aqli-runner-onnx/src/main/cpp/onnxruntime/build/onnxruntime-osx-arm64-1.19.2/lib" ]; then \
		cp plugins/runner/onnx/aqli-runner-onnx/src/main/cpp/onnxruntime/build/onnxruntime-osx-arm64-1.19.2/lib/*$(LIB_EXT)* $(ONNX_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "inference/format/onnx/onnxruntime/build/lib" ]; then \
		cp inference/format/onnx/onnxruntime/build/lib/*$(LIB_EXT)* $(ONNX_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@# Set executable permissions
	@chmod +x $(ONNX_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true
	@# Clear macOS quarantine
	@if [ "$(UNAME_S)" = "Darwin" ]; then \
		xattr -dr com.apple.quarantine $(ONNX_LIBS_DIR) 2>/dev/null || true; \
	fi
	@echo "✓ ONNX Runtime libraries installed to $(ONNX_LIBS_DIR)"
	@ls -lh $(ONNX_LIBS_DIR)/ 2>/dev/null || echo "  (no libraries found)"

# Install LibTorch libraries
install-libtorch-libs:
	@echo "Installing LibTorch libraries..."
	@mkdir -p $(LIBTORCH_LIBS_DIR)
	@# Try multiple source locations
	@if [ -d "extension/kernel/libtorch/build/lib" ]; then \
		cp extension/kernel/libtorch/build/lib/*$(LIB_EXT)* $(LIBTORCH_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "plugins/kernel/libtorch/build/lib" ]; then \
		cp plugins/kernel/libtorch/build/lib/*$(LIB_EXT)* $(LIBTORCH_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "extension/kernel/libtorch/src/main/resources/native/Darwin/arm64" ]; then \
		cp extension/kernel/libtorch/src/main/resources/native/Darwin/arm64/*$(LIB_EXT)* $(LIBTORCH_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@# Set executable permissions
	@chmod +x $(LIBTORCH_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true
	@# Clear macOS quarantine
	@if [ "$(UNAME_S)" = "Darwin" ]; then \
		xattr -dr com.apple.quarantine $(LIBTORCH_LIBS_DIR) 2>/dev/null || true; \
	fi
	@echo "✓ LibTorch libraries installed to $(LIBTORCH_LIBS_DIR)"
	@ls -lh $(LIBTORCH_LIBS_DIR)/ 2>/dev/null || echo "  (no libraries found)"

# Install TensorFlow Lite libraries
install-litert-libs:
	@echo "Installing TensorFlow Lite libraries..."
	@mkdir -p $(TFLITE_LIBS_DIR)
	@# Try multiple source locations
	@if [ -d "plugins/runner/litert/aqli-runner-litert/src/main/resources/native-libs" ]; then \
		cp plugins/runner/litert/aqli-runner-litert/src/main/resources/native-libs/*$(LIB_EXT)* $(TFLITE_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@if [ -d "extension/runner/litert/build/lib" ]; then \
		cp extension/runner/litert/build/lib/*$(LIB_EXT)* $(TFLITE_LIBS_DIR)/ 2>/dev/null || true; \
	fi
	@# Set executable permissions
	@chmod +x $(TFLITE_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true
	@# Clear macOS quarantine
	@if [ "$(UNAME_S)" = "Darwin" ]; then \
		xattr -dr com.apple.quarantine $(TFLITE_LIBS_DIR) 2>/dev/null || true; \
	fi
	@echo "✓ TensorFlow Lite libraries installed to $(TFLITE_LIBS_DIR)"
	@ls -lh $(TFLITE_LIBS_DIR)/ 2>/dev/null || echo "  (no libraries found)"

# Clean all installed libraries
clean-native-libs:
	@echo "Removing all installed native libraries..."
	@rm -rf $(AQLI_LIBS_DIR)/llama
	@rm -rf $(AQLI_LIBS_DIR)/onnxruntime
	@rm -rf $(AQLI_LIBS_DIR)/libtorch
	@rm -rf $(AQLI_LIBS_DIR)/litert
	@echo "✓ All native libraries removed from $(AQLI_LIBS_DIR)"

# Verify installation
verify-libs:
	@echo "Verifying native library installation..."
	@echo ""
	@echo "GGUF/llama.cpp:"
	@ls -lh $(LLAMA_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || echo "  Not installed"
	@echo ""
	@echo "ONNX Runtime:"
	@ls -lh $(ONNX_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || echo "  Not installed"
	@echo ""
	@echo "LibTorch:"
	@ls -lh $(LIBTORCH_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || echo "  Not installed"
	@echo ""
	@echo "TensorFlow Lite:"
	@ls -lh $(TFLITE_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || echo "  Not installed"
	@echo ""

# Download pre-built binaries (optional)
download-gguf:
	@echo "Downloading pre-built llama.cpp binaries..."
	@mkdir -p $(LLAMA_LIBS_DIR)
	@curl -L https://github.com/ggerganov/llama.cpp/releases/latest/download/llama-bin-macos-arm64.tar.gz | tar xz -C $(LLAMA_LIBS_DIR) || echo "Download failed"
	@chmod +x $(LLAMA_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true

download-onnx:
	@echo "Downloading pre-built ONNX Runtime..."
	@mkdir -p $(ONNX_LIBS_DIR)
	@curl -L https://github.com/microsoft/onnxruntime/releases/download/v1.19.2/onnxruntime-osx-arm64-1.19.2.tgz -o /tmp/onnxruntime.tgz
	@tar xzf /tmp/onnxruntime.tgz -C $(ONNX_LIBS_DIR) --strip-components=1
	@cp $(ONNX_LIBS_DIR)/lib/*$(LIB_EXT)* $(ONNX_LIBS_DIR)/ 2>/dev/null || true
	@rm -rf $(ONNX_LIBS_DIR)/lib
	@rm /tmp/onnxruntime.tgz
	@chmod +x $(ONNX_LIBS_DIR)/*$(LIB_EXT)* 2>/dev/null || true



================================================
FILE: settings.gradle.kts
================================================
rootProject.name = "aqli-engine"

// Include aljabr and tafkir as composite builds if their directories exist locally (during local multi-project development)
val aljabrDir = file("../aljabr")
if (aljabrDir.exists() && (aljabrDir.resolve("build.gradle.kts").isFile || aljabrDir.resolve("build.gradle").isFile || aljabrDir.resolve("settings.gradle.kts").isFile || aljabrDir.resolve("settings.gradle").isFile)) {
    includeBuild("../aljabr")
}

val tafkirDir = file("../tafkir")
if (tafkirDir.exists() && (tafkirDir.resolve("build.gradle.kts").isFile || tafkirDir.resolve("build.gradle").isFile || tafkirDir.resolve("settings.gradle.kts").isFile || tafkirDir.resolve("settings.gradle").isFile)) {
    includeBuild("../tafkir")
}
fun includeOptionalProject(projectPath: String, vararg candidatePaths: String) {
    val projectDir = candidatePaths
        .map { file(it) }
        .firstOrNull { candidate ->
            candidate.resolve("build.gradle.kts").isFile || candidate.resolve("build.gradle").isFile
        }
        ?: return

    val parts = projectPath.split(":")
    var currentPath = ""
    for (i in 0 until parts.size - 1) {
        val part = parts[i]
        currentPath = if (currentPath.isEmpty()) part else "$currentPath:$part"
        include(currentPath)
        
        val rootPart = parts[0]
        var mappedDir = currentPath.replace(":", "/")
        if (rootPart in listOf("core", "spi", "sdk", "plugin", "observability", "repository")) {
            mappedDir = "framework/" + mappedDir
        } else if (rootPart in listOf("backend", "runner", "quantizer", "optimization", "plugins", "repository", "adapter")) {
            mappedDir = "runtime/" + mappedDir
        } else if (rootPart == "runtime") {
            mappedDir = "runtime/core/" + mappedDir.removePrefix("runtime").removePrefix("/")
        }
        project(":$currentPath").projectDir = file(mappedDir)
    }

    include(projectPath)
    project(":$projectPath").projectDir = projectDir
}

// Optional core projects — include if their directories exist to avoid hard failures on partial checkouts
//includeOptionalProject("aqli-utils", "aqli-utils")
includeOptionalProject("core:aqli-adapter", "runtime/adapter/aqli-adapter")
includeOptionalProject("core:aqli-core", "framework/core/aqli-core")
includeOptionalProject("repository:aqli-model-database", "framework/repository/aqli-model-database")
includeOptionalProject("repository:aqli-model-repo-common", "runtime/repository/aqli-model-repo-common")
includeOptionalProject("core:aqli-model-repo-hf", "runtime/repository/aqli-model-repo-hf")
includeOptionalProject("core:aqli-model-repo-kaggle", "runtime/repository/aqli-model-repo-kaggle")
includeOptionalProject("core:aqli-model-repo-local", "runtime/repository/aqli-model-repo-local")
includeOptionalProject("spi:aqli-spi-runner", "framework/spi/aqli-spi-runner")
includeOptionalProject("observability:aqli-observability", "framework/observability/aqli-observability")
includeOptionalProject("core:aqli-tensor", "framework/core/aqli-tensor")
includeOptionalProject("core:aqli-tokenizer-core", "framework/core/aqli-tokenizer-core")
// Dynamically include model projects if they exist (avoid hard failure when some model dirs are missing)
val staticallyIncludedModelProjects = setOf<String>()

// Include statically listed ones only if their directories exist (also check aljabr/models)
staticallyIncludedModelProjects.forEach { name ->
    val aljabrAlt = "../aljabr/models/aljabr-model-${name.removePrefix("aqli-model-")}"
    includeOptionalProject("models:$name", "models/$name", "../aljabr/models/$name", aljabrAlt)
}

// Auto-include any additional model projects present under any 'models' directories in the repository
val discoveredModelDirs = mutableSetOf<java.io.File>()
file(".").walkTopDown().maxDepth(5).forEach { f ->
    if (f.isDirectory && f.name == "models") {
        f.listFiles { candidate ->
            candidate.isDirectory &&
                    candidate.name.startsWith("aqli-model-") &&
                    candidate.name !in staticallyIncludedModelProjects &&
                    (candidate.resolve("build.gradle.kts").isFile || candidate.resolve("build.gradle").isFile)
        }?.forEach { discoveredModelDirs.add(it.absoluteFile) }
    }
}

discoveredModelDirs.sortedBy { it.name }.forEach { modelProject ->
    val logicalPath = "models:${modelProject.name}"
    includeOptionalProject(logicalPath, modelProject.absolutePath)
}

// Optional optimization plugins
// includeOptionalProject("optimization:aqli-plugin-elastic-ep", "runtime/optimization/aqli-plugin-elastic-ep")
// includeOptionalProject("optimization:aqli-plugin-evicpress", "runtime/optimization/aqli-plugin-evicpress")
// includeOptionalProject("optimization:aqli-plugin-fa3", "runtime/optimization/aqli-plugin-fa3")
// includeOptionalProject("optimization:aqli-plugin-fa4", "runtime/optimization/aqli-plugin-fa4")
// includeOptionalProject("optimization:aqli-plugin-hybrid-attn", "runtime/optimization/aqli-plugin-hybrid-attn")
includeOptionalProject("optimization:aqli-plugin-kv-cache", "runtime/optimization/aqli-plugin-kv-cache")
// includeOptionalProject("optimization:aqli-plugin-paged-attention", "runtime/optimization/aqli-plugin-paged-attention")
// includeOptionalProject("optimization:aqli-plugin-perfmode", "runtime/optimization/aqli-plugin-perfmode")
// includeOptionalProject("optimization:aqli-plugin-prefill-decode", "runtime/optimization/aqli-plugin-prefill-decode")
// includeOptionalProject("optimization:aqli-plugin-prompt-cache", "runtime/optimization/aqli-plugin-prompt-cache")
// includeOptionalProject("optimization:aqli-plugin-qlora", "runtime/optimization/aqli-plugin-qlora")
// includeOptionalProject("optimization:aqli-plugin-wait-scheduler", "runtime/optimization/aqli-plugin-wait-scheduler")
// includeOptionalProject("optimization:aqli-plugin-weight-offload", "runtime/optimization/aqli-plugin-weight-offload")

// Optional plugins
includeOptionalProject("plugins:aqli-plugin-content-safety", "runtime/plugins/aqli-plugin-content-safety")
includeOptionalProject("plugins:aqli-plugin-mcp", "runtime/plugins/aqli-plugin-mcp")
includeOptionalProject("plugins:aqli-plugin-model-router", "runtime/plugins/aqli-plugin-model-router")
includeOptionalProject("plugins:wayang-plugin-openai", "../wayang/modules/provider/wayang-plugin-openai")
includeOptionalProject("plugins:wayang-plugin-anthropic", "../wayang/modules/provider/wayang-plugin-anthropic")
includeOptionalProject("plugins:wayang-plugin-gemini", "../wayang/modules/provider/wayang-plugin-gemini")
includeOptionalProject("plugins:wayang-plugin-cerebras", "../wayang/modules/provider/wayang-plugin-cerebras")
includeOptionalProject("plugins:wayang-plugin-mistral", "../wayang/modules/provider/wayang-plugin-mistral")
// includeOptionalProject("plugins:aqli-plugin-observability", "runtime/plugins/aqli-plugin-observability")
// includeOptionalProject("plugins:aqli-plugin-pii-redaction", "runtime/plugins/aqli-plugin-pii-redaction")
// includeOptionalProject("plugins:aqli-plugin-prompt", "runtime/plugins/aqli-plugin-prompt")
// includeOptionalProject("plugins:aqli-plugin-quota", "runtime/plugins/aqli-plugin-quota")
// includeOptionalProject("plugins:aqli-plugin-rag", "runtime/plugins/aqli-plugin-rag")
// includeOptionalProject("plugins:aqli-plugin-reasoning", "runtime/plugins/aqli-plugin-reasoning")
// includeOptionalProject("plugins:aqli-plugin-sampling", "runtime/plugins/aqli-plugin-sampling")
includeOptionalProject("plugins:aqli-plugin-semantic-cache", "runtime/plugins/aqli-plugin-semantic-cache")
// includeOptionalProject("plugins:aqli-plugin-streaming", "runtime/plugins/aqli-plugin-streaming")
// includeOptionalProject("plugins:aqli-safetensor-rag", "runtime/plugins/aqli-safetensor-rag")
includeOptionalProject("plugins:log-parser", "runtime/plugins/log-parser")
includeOptionalProject("plugins:gamelan", "runtime/plugins/gamelan")



// Optional quantizers
includeOptionalProject("quantizer:aqli-quantizer-autoround", "runtime/quantizer/aqli-quantizer-autoround")
includeOptionalProject("quantizer:aqli-quantizer-awq", "runtime/quantizer/aqli-quantizer-awq")
includeOptionalProject("quantizer:aqli-quantizer-gptq", "runtime/quantizer/aqli-quantizer-gptq")
includeOptionalProject("quantizer:aqli-quantizer-quip", "runtime/quantizer/aqli-quantizer-quip")
includeOptionalProject("quantizer:aqli-quantizer-turboquant", "runtime/quantizer/aqli-quantizer-turboquant")

// Optional runners and runtimes
includeOptionalProject("runner:aqli-diffusion", "runtime/runner/aqli-diffusion")
includeOptionalProject("runtime:aqli-runtime-core", "runtime/core/aqli-runtime-core")
// includeOptionalProject("runtime:distributed:aqli-runtime-distributed", "runtime/core/aqli-runtime-distributed")

// Optional SDK
includeOptionalProject("sdk:aqli-sdk", "framework/sdk/aqli-sdk")
includeOptionalProject("sdk:aqli-sdk-api", "framework/sdk/aqli-sdk-api")
includeOptionalProject("sdk:aqli-sdk-core", "framework/sdk/aqli-sdk-core")
includeOptionalProject("sdk:aqli-sdk-local", "framework/sdk/aqli-sdk-local")
// includeOptionalProject("sdk:aqli-sdk-remote", "framework/sdk/aqli-sdk-remote")
includeOptionalProject("sdk:aqli-sdk-protobuf", "framework/sdk/aqli-sdk-protobuf")
includeOptionalProject("ui:aqli-sdk-protobuf", "ui/aqli-sdk-protobuf")
if (file("framework/sdk/aqli-sdk-session").isDirectory) {
    include("sdk:aqli-sdk-session")
}

// Optional SPIs
includeOptionalProject("spi:aqli-spi", "framework/spi/aqli-spi")
includeOptionalProject("spi:aqli-spi-audio", "framework/spi/aqli-spi-audio")
includeOptionalProject("spi:aqli-spi-image", "framework/spi/aqli-spi-image")
includeOptionalProject("spi:aqli-spi-inference", "framework/spi/aqli-spi-inference")
includeOptionalProject("spi:aqli-spi-model", "framework/spi/aqli-spi-model")
includeOptionalProject("spi:aqli-spi-multimodal", "framework/spi/aqli-spi-multimodal")
includeOptionalProject("spi:aqli-spi-plugin", "framework/spi/aqli-spi-plugin")
includeOptionalProject("spi:aqli-spi-runtime", "framework/spi/aqli-spi-runtime")

// Optional UI
includeOptionalProject("ui:aqli-api", "ui/aqli-api")
includeOptionalProject("ui:aqli-cli", "ui/aqli-cli")

// Optional backends
includeOptionalProject("backend:blackwell:aqli-kernel-blackwell", "runtime/backend/blackwell/aqli-kernel-blackwell")
includeOptionalProject("backend:blackwell:aqli-plugin-kernel-blackwell", "runtime/backend/blackwell/aqli-plugin-kernel-blackwell")
includeOptionalProject("backend:cpu:aqli-backend-cpu", "runtime/backend/cpu/aqli-backend-cpu")
includeOptionalProject("backend:cuda:aqli-backend-cuda", "runtime/backend/cuda/aqli-backend-cuda")
includeOptionalProject("backend:cuda:aqli-kernel-cuda", "runtime/backend/cuda/aqli-kernel-cuda")
includeOptionalProject("backend:cuda:aqli-plugin-kernel-cuda", "runtime/backend/cuda/aqli-plugin-kernel-cuda")
includeOptionalProject("backend:directml:aqli-plugin-kernel-directml", "runtime/backend/directml/aqli-plugin-kernel-directml")
includeOptionalProject("backend:metal:aqli-backend-metal", "runtime/backend/metal/aqli-backend-metal")
includeOptionalProject("backend:metal:aqli-mlx-binding", "runtime/backend/metal/aqli-mlx-binding")
includeOptionalProject("backend:rocm:aqli-kernel-rocm", "runtime/backend/rocm/aqli-kernel-rocm")
includeOptionalProject("backend:rocm:aqli-plugin-kernel-rocm", "runtime/backend/rocm/aqli-plugin-kernel-rocm")

// Optional core plugins
includeOptionalProject("plugin:aqli-plugin-core", "framework/plugin/aqli-plugin-core")
includeOptionalProject("plugin:aqli-plugin-kernel-core", "framework/plugin/aqli-plugin-kernel-core")
includeOptionalProject("plugin:aqli-plugin-optimization-core", "framework/plugin/aqli-plugin-optimization-core")
includeOptionalProject("plugin:aqli-plugin-runner-core", "framework/plugin/aqli-plugin-runner-core")
includeOptionalProject("plugin:aqli-plugin-runner-gguf", "framework/plugin/aqli-plugin-runner-gguf")

// Optional runners
includeOptionalProject("runner:diffuser:aqli-diffuser", "runtime/runner/diffuser/aqli-diffuser")
includeOptionalProject("runner:gguf:aqli-gguf-converter", "runtime/runner/gguf/aqli-gguf-converter")
includeOptionalProject("runner:gguf:aqli-gguf-converter-java", "runtime/runner/gguf/aqli-gguf-converter-java")
includeOptionalProject("runner:gguf:aqli-gguf-core", "runtime/runner/gguf/aqli-gguf-core")
includeOptionalProject("runner:gguf:aqli-runner-gguf", "runtime/runner/gguf/aqli-runner-gguf")
includeOptionalProject("runner:gguf:aqli-plugin-runner-gguf", "runtime/runner/gguf/aqli-plugin-runner-gguf")
includeOptionalProject("runner:gguf:aqli-gguf-feature-text", "runtime/runner/gguf/aqli-gguf-feature-text")
includeOptionalProject("runner:litert:aqli-litert-core", "runtime/runner/litert/aqli-litert-core")
includeOptionalProject("runner:litert:aqli-plugin-runner-litert", "runtime/runner/litert/aqli-plugin-runner-litert")
includeOptionalProject("runner:litert:aqli-runner-litert", "runtime/runner/litert/aqli-runner-litert")

includeOptionalProject("runner:onnx:aqli-plugin-runner-onnx", "runtime/runner/onnx/aqli-plugin-runner-onnx")
includeOptionalProject("runner:onnx:aqli-runner-onnx", "runtime/runner/onnx/aqli-runner-onnx")

includeOptionalProject("runner:safetensor:aqli-safetensor-engine", "runtime/runner/safetensor/aqli-safetensor-engine")
includeOptionalProject("runner:safetensor:aqli-runner-flux", "runtime/runner/safetensor/aqli-runner-flux")
// includeOptionalProject("runner:safetensor:aqli-runner-safetensor", "runtime/runner/safetensor/aqli-runner-safetensor")
// includeOptionalProject("runner:safetensor:aqli-runner-stable-diffusion", "runtime/runner/safetensor/aqli-runner-stable-diffusion")
// includeOptionalProject("runner:safetensor:aqli-safetensor-api", "runtime/runner/safetensor/aqli-safetensor-api")
// includeOptionalProject("runner:safetensor:aqli-safetensor-core", "runtime/runner/safetensor/aqli-safetensor-core")
// includeOptionalProject("runner:safetensor:aqli-safetensor-loader", "runtime/runner/safetensor/aqli-safetensor-loader")
// includeOptionalProject("runner:safetensor:aqli-safetensor-quantization", "runtime/runner/safetensor/aqli-safetensor-quantization")
// includeOptionalProject("runner:safetensor:aqli-safetensor-spi", "runtime/runner/safetensor/aqli-safetensor-spi")
// includeOptionalProject("runner:tensorrt:aqli-runner-tensorrt", "runtime/runner/tensorrt/aqli-runner-tensorrt")
// includeOptionalProject("runner:tensorrt:aqli-plugin-runner-tensorrt", "runtime/runner/tensorrt/aqli-plugin-tensorrt")
// includeOptionalProject("runner:torch:aqli-runner-libtorch", "runtime/runner/torch/aqli-runner-libtorch")








================================================
FILE: TestDylib.java
================================================
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TestDylib {
    public static void main(String[] args) throws Throwable {
        Path bridge = Path.of(System.getProperty("user.home"), ".aqli", "libs", "libaqli_gguf_fast.dylib").toAbsolutePath();
        System.load(bridge.toString());
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.libraryLookup(bridge, Arena.global());
        
        MethodHandle open = linker.downcallHandle(lookup.find("aqli_gguf_open").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        MethodHandle generate = linker.downcallHandle(lookup.find("aqli_gguf_generate").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        MethodHandle close = linker.downcallHandle(lookup.find("aqli_gguf_close").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        String modelStr = "/Users/bhangun/.aqli/models/blobs/unsloth/gemma-4-12b-it-GGUF/unsloth__gemma-4-12b-it-GGUF/gemma-4-12b-it-Q4_K_M.gguf";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment model = arena.allocateFrom(modelStr, StandardCharsets.UTF_8);
            MemorySegment backendDir = arena.allocateFrom(System.getProperty("user.home") + "/.aqli/libs/llama", StandardCharsets.UTF_8);
            MemorySegment error = arena.allocate(8192);
            
            System.out.println("Opening...");
            MemorySegment handle = (MemorySegment) open.invokeExact(model, backendDir, 512, 512, 512, 8, 8, -1, 1, 0, 0, error, 8192L);
            if (handle.address() == 0L) {
                System.out.println("Error opening: " + error.getString(0));
                return;
            }
            
            MemorySegment prompt = arena.allocateFrom("<bos><start_of_turn>user\nwho are you<end_of_turn>\n<start_of_turn>model\n", StandardCharsets.UTF_8);
            MemorySegment output = arena.allocate(65536);
            
            System.out.println("Generating 1...");
            int flag = 0;
            int generated = (int) generate.invokeExact(handle, prompt, 20, 0.0f, 40, 0.9f, flag, output, 65536L, error, 8192L);
            System.out.println("Generated " + generated + ":\n" + output.getString(0));

            System.out.println("Generating 2...");
            generated = (int) generate.invokeExact(handle, prompt, 20, 0.0f, 40, 0.9f, 0, output, 65536L, error, 8192L);
            System.out.println("Generated " + generated + ":\n" + output.getString(0));
            
            close.invokeExact(handle);
        }
    }
}



================================================
FILE: buildSrc/build.gradle.kts
================================================
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverage.kt
================================================
package tech.kayys.aqli.buildlogic

data class DirectFixtureCoverage(
    val id: String,
    val modelType: String,
    val architectures: List<String>,
    val tokenizerMarker: String,
)



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageFileSupport.kt
================================================
package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageFileSupport {
    fun fixtureModelType(config: Map<*, *>): String =
        config["model_type"]?.toString()?.trim().orEmpty()

    fun fixtureArchitectures(config: Map<*, *>): List<String> =
        (config["architectures"] as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun containsJavaSourceText(module: File, relativeDir: String, text: String): Boolean {
        val sourceDir = sourceDir(module, relativeDir)
        if (sourceDir == null) {
            return false
        }

        return containsJavaSourceText(javaSourceFiles(sourceDir), text)
    }

    fun javaSourceTextIfContains(module: File, relativeDir: String, text: String): String? {
        val sourceDir = sourceDir(module, relativeDir)
        if (sourceDir == null) {
            return null
        }

        val sourceFiles = javaSourceFiles(sourceDir)
        if (!containsJavaSourceText(sourceFiles, text)) {
            return null
        }

        return javaSourceText(sourceFiles)
    }

    private fun sourceDir(module: File, relativeDir: String): File? =
        module.resolve(relativeDir).takeIf { it.isDirectory }

    private fun javaSourceFiles(sourceDir: File): List<File> =
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .sortedBy { it.relativeTo(sourceDir).path }
            .toList()

    private fun containsJavaSourceText(sourceFiles: List<File>, text: String): Boolean =
        sourceFiles.any { sourceFile ->
            sourceFile.useLines { lines ->
                lines.any { it.contains(text) }
            }
        }

    private fun javaSourceText(sourceFiles: List<File>): String {
        val sourceText = StringBuilder()
        sourceFiles.forEach { sourceFile ->
            if (sourceText.isNotEmpty()) {
                sourceText.append('\n')
            }
            sourceText.append(sourceFile.readText())
        }
        return sourceText.toString()
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageFixtureParser.kt
================================================
package tech.kayys.aqli.buildlogic

import groovy.json.JsonSlurper
import java.io.File

object DirectFixtureCoverageFixtureParser {
    fun parse(
        module: File,
        mainJavaText: String,
        config: File,
        problems: MutableList<String>,
    ): DirectFixtureCoverage? {
        val fixturePath = config.relativeTo(module).path
        val parsed = parseConfig(module, fixturePath, config, problems)
        if (parsed != null) {
            validateClaims(module, mainJavaText, fixturePath, parsed, problems)
        }

        val dir = config.parentFile
        val tokenizerMarker = DirectFixtureCoverageReportSpec.tokenizerMarkers.firstOrNull {
            dir.resolve(it).isFile
        }
        if (tokenizerMarker == null) {
            problems += "${module.name}: ${dir.relativeTo(module).path} should include tokenizer.json or vocab marker"
        }
        return parsed?.let {
            DirectFixtureCoverage(
                id = dir.name,
                modelType = DirectFixtureCoverageFileSupport.fixtureModelType(it),
                architectures = DirectFixtureCoverageFileSupport.fixtureArchitectures(it),
                tokenizerMarker = tokenizerMarker.orEmpty(),
            )
        }
    }

    private fun parseConfig(
        module: File,
        fixturePath: String,
        config: File,
        problems: MutableList<String>,
    ): Map<*, *>? =
        try {
            JsonSlurper().parse(config) as? Map<*, *>
        } catch (error: Exception) {
            problems += "${module.name}: $fixturePath is not parseable JSON (${error.javaClass.simpleName})"
            null
        }

    private fun validateClaims(
        module: File,
        mainJavaText: String,
        fixturePath: String,
        parsed: Map<*, *>,
        problems: MutableList<String>,
    ): Unit {
        val modelType = DirectFixtureCoverageFileSupport.fixtureModelType(parsed)
        if (modelType.isBlank()) {
            problems += "${module.name}: $fixturePath should declare model_type"
        } else if (!mainJavaText.contains("\"$modelType\"")) {
            problems += "${module.name}: $fixturePath model_type '$modelType' is not claimed in main Java sources"
        }
        val architectures = parsed["architectures"] as? List<*>
        if (architectures.isNullOrEmpty()) {
            problems += "${module.name}: $fixturePath should declare at least one architecture"
        } else {
            val architectureClaims = DirectFixtureCoverageFileSupport.fixtureArchitectures(parsed)
            if (architectureClaims.isEmpty()) {
                problems += "${module.name}: $fixturePath should declare at least one non-blank architecture"
            }
            architectureClaims.forEach { architecture ->
                if (!mainJavaText.contains("\"$architecture\"")) {
                    problems += "${module.name}: $fixturePath architecture '$architecture' is not claimed in main Java sources"
                }
            }
        }
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageGuards.kt
================================================
package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException

object DirectFixtureCoverageGuards {
    fun requireProblemsClear(problems: List<String>): Unit {
        if (problems.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Direct model-family fixture coverage problems:")
                append(problems.joinToString(separator = "\n") { "- $it" })
            })
        }
    }

    fun requireReportEntries(
        directModuleCount: Int,
        uniqueFixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Int {
        if (reportEntries.size != directModuleCount) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: directModuleCount=$directModuleCount, ")
                append("reportEntryCount=${reportEntries.size}")
            })
        }
        val fixtureCount = reportEntries.sumOf { it.fixtures.size }
        if (fixtureCount != uniqueFixtureCount) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: fixtureCount=$fixtureCount, ")
                append("uniqueFixtureCount=$uniqueFixtureCount")
            })
        }
        val incompleteFixtureDetails = reportEntries
            .flatMap { moduleReport ->
                moduleReport.fixtures.filter { fixture ->
                    fixture.id.isBlank()
                            || fixture.modelType.isBlank()
                            || fixture.architectures.isEmpty()
                            || fixture.tokenizerMarker.isBlank()
                }.map { fixture -> "${moduleReport.moduleName}:${fixture.id.ifBlank { "<blank>" }}" }
            }
        if (incompleteFixtureDetails.isNotEmpty()) {
            throw GradleException(buildString {
                append("Direct model-family fixture report drift: incomplete fixtureDetails for ")
                append(incompleteFixtureDetails.joinToString(", "))
            })
        }
        return fixtureCount
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageModuleReport.kt
================================================
package tech.kayys.aqli.buildlogic

data class DirectFixtureCoverageModuleReport(
    val moduleName: String,
    val fixtures: List<DirectFixtureCoverage>,
)



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportContract.kt
================================================
package tech.kayys.aqli.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File

private typealias Spec = DirectFixtureCoverageReportSpec

object DirectFixtureCoverageReportContract {
    fun requireReportContract(report: Map<*, *>, schema: Map<*, *>): Unit {
        val problems = mutableListOf<String>()
        val schemaProperties = schema["properties"] as? Map<*, *>
        val schemaVersionProperty = schemaProperties?.get("schemaVersion") as? Map<*, *>
        val schemaModulesProperty = schemaProperties?.get("modules") as? Map<*, *>
        val schemaModuleItem = schemaModulesProperty?.get("items") as? Map<*, *>
        val schemaModuleProperties = schemaModuleItem?.get("properties") as? Map<*, *>
        val schemaFixtureDetails = schemaModuleProperties?.get("fixtureDetails") as? Map<*, *>
        val schemaFixtureDetailItem = schemaFixtureDetails?.get("items") as? Map<*, *>
        val schemaFixtureDetailProperties = schemaFixtureDetailItem?.get("properties") as? Map<*, *>
        val schemaTokenizerMarker = schemaFixtureDetailProperties?.get("tokenizerMarker") as? Map<*, *>

        if (schema["\$id"] != Spec.schemaId) {
            problems += "schema \$id should be ${Spec.schemaId}"
        }
        val schemaRootRequired = requiredFieldSet(schema["required"])
        if (schemaRootRequired != Spec.rootFieldSet) {
            problems += fieldDriftMessage("schema root required", Spec.rootFieldSet, schemaRootRequired)
        }
        if ((schemaVersionProperty?.get("const") as? Number)?.toInt() != Spec.schemaVersion) {
            problems += "schemaVersion const should be ${Spec.schemaVersion}"
        }
        val schemaModuleRequired = requiredFieldSet(schemaModuleItem?.get("required"))
        if (schemaModuleRequired != Spec.moduleFieldSet) {
            problems += fieldDriftMessage("schema module required", Spec.moduleFieldSet, schemaModuleRequired)
        }
        val schemaFixtureDetailRequired = requiredFieldSet(schemaFixtureDetailItem?.get("required"))
        if (schemaFixtureDetailRequired != Spec.detailFieldSet) {
            problems += fieldDriftMessage(
                "schema fixtureDetails required",
                Spec.detailFieldSet,
                schemaFixtureDetailRequired,
            )
        }
        if (schemaTokenizerMarker?.get("enum") != Spec.tokenizerMarkers) {
            problems += "schema tokenizerMarker enum drifted"
        }

        val reportFields = fieldSet(report)
        if (reportFields != Spec.rootFieldSet) {
            problems += fieldDriftMessage("report root", Spec.rootFieldSet, reportFields)
        }
        if ((report["schemaVersion"] as? Number)?.toInt() != Spec.schemaVersion) {
            problems += "report schemaVersion should be ${Spec.schemaVersion}"
        }
        val directModuleCount = (report["directModuleCount"] as? Number)?.toInt()
        val fixtureCount = (report["fixtureCount"] as? Number)?.toInt()
        val modules = report["modules"] as? List<*>
        if (directModuleCount == null || directModuleCount < 1) {
            problems += "report directModuleCount should be a positive integer"
        }
        if (fixtureCount == null || fixtureCount < 1) {
            problems += "report fixtureCount should be a positive integer"
        }
        if (modules == null || modules.isEmpty()) {
            problems += "report modules should be a non-empty list"
        } else {
            if (directModuleCount != modules.size) {
                problems += "report directModuleCount=$directModuleCount but modules.size=${modules.size}"
            }
            var observedFixtureCount = 0
            modules.forEach { moduleEntry ->
                val module = moduleEntry as? Map<*, *>
                if (module == null) {
                    problems += "report module entry should be an object"
                    return@forEach
                }
                val moduleFields = fieldSet(module)
                if (moduleFields != Spec.moduleFieldSet) {
                    problems += fieldDriftMessage(
                        "report module ${module["module"]}",
                        Spec.moduleFieldSet,
                        moduleFields,
                    )
                }
                val moduleName = module["module"]?.toString().orEmpty()
                if (!moduleName.matches(Spec.moduleNameRegex)) {
                    problems += "report module '$moduleName' should match aqli-model-* naming"
                }
                val moduleFixtureCount = (module["fixtureCount"] as? Number)?.toInt()
                val fixtures = (module["fixtures"] as? List<*>)?.map { it?.toString().orEmpty() }
                val fixtureDetails = module["fixtureDetails"] as? List<*>
                if (moduleFixtureCount == null || moduleFixtureCount < 1) {
                    problems += "$moduleName fixtureCount should be a positive integer"
                }
                if (fixtures == null || fixtures.any { it.isBlank() }) {
                    problems += "$moduleName fixtures should be non-blank strings"
                }
                if (fixtureDetails == null || fixtureDetails.isEmpty()) {
                    problems += "$moduleName fixtureDetails should be non-empty"
                }
                if (fixtures != null && moduleFixtureCount != fixtures.size) {
                    problems += "$moduleName fixtureCount=$moduleFixtureCount but fixtures.size=${fixtures.size}"
                }
                if (fixtureDetails != null && moduleFixtureCount != fixtureDetails.size) {
                    problems += "$moduleName fixtureCount=$moduleFixtureCount but fixtureDetails.size=${fixtureDetails.size}"
                }
                val detailIds = fixtureDetails
                    ?.mapNotNull { detail -> (detail as? Map<*, *>)?.get("id")?.toString() }
                    ?: emptyList()
                if (fixtures != null && fixtures != detailIds) {
                    problems += "$moduleName fixtures and fixtureDetails ids differ"
                }
                observedFixtureCount += moduleFixtureCount ?: 0
                fixtureDetails?.forEach { detailEntry ->
                    val detail = detailEntry as? Map<*, *>
                    if (detail == null) {
                        problems += "$moduleName fixtureDetail should be an object"
                        return@forEach
                    }
                    val detailFields = fieldSet(detail)
                    if (detailFields != Spec.detailFieldSet) {
                        problems += fieldDriftMessage(
                            "$moduleName fixtureDetail ${detail["id"]}",
                            Spec.detailFieldSet,
                            detailFields,
                        )
                    }
                    val id = detail["id"]?.toString().orEmpty()
                    val modelType = detail["modelType"]?.toString().orEmpty()
                    val architectures = (detail["architectures"] as? List<*>)?.map { it?.toString().orEmpty() }
                    val tokenizerMarker = detail["tokenizerMarker"]?.toString().orEmpty()
                    if (id.isBlank() || modelType.isBlank()) {
                        problems += "$moduleName fixtureDetail id/modelType should be non-blank"
                    }
                    if (architectures == null || architectures.isEmpty() || architectures.any { it.isBlank() }) {
                        problems += "$moduleName:$id architectures should be non-empty strings"
                    }
                    if (tokenizerMarker !in Spec.tokenizerMarkers) {
                        problems += "$moduleName:$id tokenizerMarker '$tokenizerMarker' is unsupported"
                    }
                }
            }
            if (fixtureCount != observedFixtureCount) {
                problems += "report fixtureCount=$fixtureCount but observedFixtureCount=$observedFixtureCount"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Direct model-family fixture report contract problems:\n" +
                        problems.joinToString(separator = "\n") { "- $it" },
            )
        }
    }

    fun requireGeneratedReports(
        report: Map<*, *>,
        schema: Map<*, *>,
        reportFile: File,
        schemaFile: File,
    ): Unit {
        requireReportContract(report, schema)
        requireReportContract(
            parseJsonFileMap(reportFile),
            parseJsonFileMap(schemaFile),
        )
    }

    fun expectContractFailure(
        description: String,
        expectedMessage: String,
        report: Map<*, *>,
        schema: Map<*, *>,
    ): Unit {
        val failure = try {
            requireReportContract(report, schema)
            null
        } catch (error: GradleException) {
            error
        }
        if (failure == null) {
            throw GradleException("Expected direct fixture coverage contract failure for $description")
        }
        if (!failure.message.orEmpty().contains(expectedMessage)) {
            throw GradleException(buildString {
                append("Expected direct fixture coverage contract failure for $description to contain '$expectedMessage' ")
                append("but was: ${failure.message}")
            })
        }
    }

    private fun fieldDriftMessage(scope: String, expected: Set<String>, actual: Set<String>?): String =
        "$scope fields drifted; expected=${expected.sorted()}, actual=${actual?.sorted() ?: "<missing>"}"

    private fun fieldSet(value: Map<*, *>): Set<String> =
        value.keys.map { it.toString() }.toSet()

    private fun requiredFieldSet(value: Any?): Set<String>? =
        (value as? List<*>)?.map { it.toString() }?.toSet()

    private fun parseJsonFileMap(file: File): Map<*, *> =
        JsonSlurper().parse(file) as Map<*, *>
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportFailureSmoke.kt
================================================
package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException

object DirectFixtureCoverageReportFailureSmoke {
    private const val fixtureCountDriftDescription = "fixture count drift"
    private const val fixtureCountDriftMessage = "report fixtureCount=2 but observedFixtureCount=1"
    private const val fixtureIdDetailDriftDescription = "fixture id/detail drift"
    private const val fixtureIdDetailDriftMessage = "fixtures and fixtureDetails ids differ"
    private const val unsupportedTokenizerDescription = "unsupported tokenizer marker"
    private val unsupportedTokenizerMessage =
        "tokenizerMarker '${DirectFixtureCoverageSmokeFixtures.unsupportedTokenizerMarker}' is unsupported"
    private const val incompleteFixtureDetailsMessage = "incomplete fixtureDetails"

    fun requireSmokeContract(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        contractFailureCases(reportPayload).forEach { failureCase ->
            expectContractFailure(failureCase, schemaPayload)
        }
        requireIncompleteReportEntryFailure()
    }

    private fun contractFailureCases(reportPayload: Map<String, Any>): List<ContractFailureCase> =
        listOf(
            fixtureCountDriftCase(reportPayload),
            fixtureIdDetailDriftCase(reportPayload),
            unsupportedTokenizerCase(reportPayload),
        )

    private fun fixtureCountDriftCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = fixtureCountDriftDescription,
            expectedMessage = fixtureCountDriftMessage,
            report = reportPayload + ("fixtureCount" to 2),
        )

    private fun fixtureIdDetailDriftCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = fixtureIdDetailDriftDescription,
            expectedMessage = fixtureIdDetailDriftMessage,
            report = reportPayload + ("modules" to listOf(
                DirectFixtureCoverageSmokeFixtures.mismatchedFixtureDetailModuleMap(),
            )),
        )

    private fun unsupportedTokenizerCase(reportPayload: Map<String, Any>): ContractFailureCase =
        ContractFailureCase(
            description = unsupportedTokenizerDescription,
            expectedMessage = unsupportedTokenizerMessage,
            report = reportPayload + ("modules" to listOf(
                DirectFixtureCoverageSmokeFixtures.unsupportedTokenizerModuleMap(),
            )),
        )

    private fun requireIncompleteReportEntryFailure(): Unit {
        val incompleteFailure = try {
            DirectFixtureCoverageGuards.requireReportEntries(
                directModuleCount = 1,
                uniqueFixtureCount = 1,
                reportEntries = DirectFixtureCoverageSmokeFixtures.incompleteReportEntries(),
            )
            null
        } catch (error: GradleException) {
            error
        }
        if (!incompleteFailure?.message.orEmpty().contains(incompleteFixtureDetailsMessage)) {
            throw GradleException("Direct fixture coverage report entry smoke failure drifted")
        }
    }

    private fun expectContractFailure(failureCase: ContractFailureCase, schema: Map<*, *>): Unit =
        DirectFixtureCoverageReportContract.expectContractFailure(
            description = failureCase.description,
            expectedMessage = failureCase.expectedMessage,
            report = failureCase.report,
            schema = schema,
        )

    private data class ContractFailureCase(
        val description: String,
        val expectedMessage: String,
        val report: Map<*, *>,
    )
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportRendering.kt
================================================
package tech.kayys.aqli.buildlogic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

object DirectFixtureCoverageReportRendering {
    fun prettyJsonWithTrailingNewline(value: Any?): String =
        buildString {
            appendLine(JsonOutput.prettyPrint(JsonOutput.toJson(value)))
        }

    fun jsonRoundTripMap(value: Any?): Map<*, *> =
        JsonSlurper().parseText(prettyJsonWithTrailingNewline(value)) as Map<*, *>

    fun reportPayload(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Map<String, Any> = mapOf(
        "schemaVersion" to DirectFixtureCoverageReportSpec.schemaVersion,
        "directModuleCount" to directModuleCount,
        "fixtureCount" to fixtureCount,
        "modules" to reportEntries
            .sortedBy { it.moduleName }
            .map { moduleReport ->
                mapOf(
                    "module" to moduleReport.moduleName,
                    "fixtureCount" to moduleReport.fixtures.size,
                    "fixtures" to moduleReport.fixtures.map { it.id },
                    "fixtureDetails" to moduleReport.fixtures.map { fixture ->
                        mapOf(
                            "id" to fixture.id,
                            "modelType" to fixture.modelType,
                            "architectures" to fixture.architectures,
                            "tokenizerMarker" to fixture.tokenizerMarker,
                        )
                    },
                )
            },
    )

    fun textReport(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
    ): String = buildString {
        appendLine("Direct model-family fixture coverage")
        appendLine("schemaVersion=${DirectFixtureCoverageReportSpec.schemaVersion}")
        appendLine("directModuleCount=$directModuleCount")
        appendLine("fixtureCount=$fixtureCount")
        appendLine()
        reportEntries
            .sortedBy { it.moduleName }
            .forEach { moduleReport ->
                appendLine(
                    "${moduleReport.moduleName} (${moduleReport.fixtures.size}): " +
                            moduleReport.fixtures.joinToString(", ") { it.id },
                )
                moduleReport.fixtures.forEach { fixture ->
                    appendLine(buildString {
                        append("  - ${fixture.id}: model_type=${fixture.modelType}, ")
                        append("architectures=${fixture.architectures.joinToString("|")}, ")
                        append("tokenizer=${fixture.tokenizerMarker}")
                    })
                }
            }
    }

    fun schemaPayload(): Map<String, Any> = mapOf(
        "\$schema" to "https://json-schema.org/draft/2020-12/schema",
        "\$id" to DirectFixtureCoverageReportSpec.schemaId,
        "title" to "Aqli direct model-family fixture coverage report",
        "type" to "object",
        "additionalProperties" to false,
        "required" to DirectFixtureCoverageReportSpec.rootFields,
        "properties" to mapOf(
            "schemaVersion" to mapOf("const" to DirectFixtureCoverageReportSpec.schemaVersion),
            "directModuleCount" to mapOf("type" to "integer", "minimum" to 1),
            "fixtureCount" to mapOf("type" to "integer", "minimum" to 1),
            "modules" to mapOf(
                "type" to "array",
                "minItems" to 1,
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to DirectFixtureCoverageReportSpec.moduleFields,
                    "properties" to mapOf(
                        "module" to mapOf(
                            "type" to "string",
                            "pattern" to DirectFixtureCoverageReportSpec.moduleNamePattern,
                        ),
                        "fixtureCount" to mapOf("type" to "integer", "minimum" to 1),
                        "fixtures" to mapOf(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to mapOf("type" to "string", "minLength" to 1),
                        ),
                        "fixtureDetails" to mapOf(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to DirectFixtureCoverageReportSpec.detailFields,
                                "properties" to mapOf(
                                    "id" to mapOf("type" to "string", "minLength" to 1),
                                    "modelType" to mapOf("type" to "string", "minLength" to 1),
                                    "architectures" to mapOf(
                                        "type" to "array",
                                        "minItems" to 1,
                                        "items" to mapOf("type" to "string", "minLength" to 1),
                                    ),
                                    "tokenizerMarker" to mapOf(
                                        "type" to "string",
                                        "enum" to DirectFixtureCoverageReportSpec.tokenizerMarkers,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportSmoke.kt
================================================
package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException
import java.nio.file.Files

private typealias SmokeSpec = DirectFixtureCoverageReportSpec
private typealias SmokeFixtures = DirectFixtureCoverageSmokeFixtures

object DirectFixtureCoverageReportSmoke {
    fun requireSmokeContract(): Unit {
        DirectFixtureCoverageSpecSmoke.requireSmokeContract()
        val smokeReportEntries = SmokeFixtures.reportEntries()
        val reportPayload = DirectFixtureCoverageReportRendering.reportPayload(
            directModuleCount = 1,
            fixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        val schemaPayload = DirectFixtureCoverageReportRendering.schemaPayload()

        requireGuardSmoke(smokeReportEntries)
        requireTextReportSmoke(smokeReportEntries)
        requireReportContractSmoke(reportPayload, schemaPayload)
        requireGeneratedReportSmoke(reportPayload, schemaPayload)
        requireChangeAwareWriterSmoke(smokeReportEntries)
        DirectFixtureCoverageReportFailureSmoke.requireSmokeContract(reportPayload, schemaPayload)
    }

    private fun requireGuardSmoke(smokeReportEntries: List<DirectFixtureCoverageModuleReport>): Unit {
        DirectFixtureCoverageGuards.requireProblemsClear(emptyList())
        val coverageFailure = try {
            DirectFixtureCoverageGuards.requireProblemsClear(
                listOf("${SmokeFixtures.moduleName}: missing fixture"),
            )
            null
        } catch (error: GradleException) {
            error
        }
        if (!coverageFailure?.message.orEmpty().contains("- ${SmokeFixtures.moduleName}: missing fixture")) {
            throw GradleException("Direct fixture coverage problem smoke failure drifted")
        }
        val fixtureCount = DirectFixtureCoverageGuards.requireReportEntries(
            directModuleCount = 1,
            uniqueFixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        if (fixtureCount != 1) {
            throw GradleException("Direct fixture coverage report entry smoke count drifted")
        }
    }

    private fun requireTextReportSmoke(smokeReportEntries: List<DirectFixtureCoverageModuleReport>): Unit {
        val textReport = DirectFixtureCoverageReportRendering.textReport(
            directModuleCount = 1,
            fixtureCount = 1,
            reportEntries = smokeReportEntries,
        )
        if (!textReport.contains("schemaVersion=${SmokeSpec.schemaVersion}")
            || !textReport.contains("${SmokeFixtures.moduleName} (1): ${SmokeFixtures.fixtureId}")
            || !textReport.contains(
                "model_type=${SmokeFixtures.fixtureModelType}, architectures=${SmokeFixtures.architecture}, " +
                        "tokenizer=${SmokeFixtures.fixtureTokenizerMarker}",
            )
        ) {
            throw GradleException("Direct fixture coverage text report smoke output drifted")
        }
    }

    private fun requireReportContractSmoke(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        DirectFixtureCoverageReportContract.requireReportContract(reportPayload, schemaPayload)
        DirectFixtureCoverageReportContract.requireReportContract(
            DirectFixtureCoverageReportRendering.jsonRoundTripMap(reportPayload),
            DirectFixtureCoverageReportRendering.jsonRoundTripMap(schemaPayload),
        )
    }

    private fun requireGeneratedReportSmoke(reportPayload: Map<String, Any>, schemaPayload: Map<String, Any>): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-coverage-").toFile()
        try {
            val smokeReportFile = smokeDir.resolve("model-family-direct-fixtures.json")
            val smokeSchemaFile = smokeDir.resolve("model-family-direct-fixtures.schema.json")
            smokeReportFile.writeText(DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(reportPayload))
            smokeSchemaFile.writeText(DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(schemaPayload))
            DirectFixtureCoverageReportContract.requireGeneratedReports(
                report = reportPayload,
                schema = schemaPayload,
                reportFile = smokeReportFile,
                schemaFile = smokeSchemaFile,
            )
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun requireChangeAwareWriterSmoke(
        smokeReportEntries: List<DirectFixtureCoverageModuleReport>,
    ): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-writer-").toFile()
        try {
            val textReportFile = smokeDir.resolve("model-family-direct-fixtures.txt")
            val jsonReportFile = smokeDir.resolve("model-family-direct-fixtures.json")
            val schemaFile = smokeDir.resolve("model-family-direct-fixtures.schema.json")
            val reportFiles = listOf(textReportFile, jsonReportFile, schemaFile)
            DirectFixtureCoverageReportWriter.writeReports(
                directModuleCount = 1,
                fixtureCount = 1,
                reportEntries = smokeReportEntries,
                textReportFile = textReportFile,
                jsonReportFile = jsonReportFile,
                schemaFile = schemaFile,
            )
            val pinnedModifiedAt = 1_700_000_000_000L
            reportFiles.forEach { reportFile ->
                if (!reportFile.setLastModified(pinnedModifiedAt)) {
                    throw GradleException("Direct fixture coverage writer smoke could not pin report timestamp")
                }
            }

            DirectFixtureCoverageReportWriter.writeReports(
                directModuleCount = 1,
                fixtureCount = 1,
                reportEntries = smokeReportEntries,
                textReportFile = textReportFile,
                jsonReportFile = jsonReportFile,
                schemaFile = schemaFile,
            )
            if (reportFiles.any { it.lastModified() != pinnedModifiedAt }) {
                throw GradleException("Direct fixture coverage writer rewrote unchanged reports")
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportSpec.kt
================================================
package tech.kayys.aqli.buildlogic

object DirectFixtureCoverageReportSpec {
    const val schemaVersion = 2

    val schemaId =
        "https://aqli.ai/schemas/build/model-family-direct-fixtures.v$schemaVersion.schema.json"

    const val moduleNamePattern = "^aqli-model-[a-z0-9-]+$"
    const val moduleNamePrefix = "aqli-model-"
    const val mainJavaSourcePath = "src/main/java"
    const val testJavaSourcePath = "src/test/java"
    const val fixtureRootPath = "src/test/resources/model-family-fixtures"
    const val directInferenceMarker = "DIRECT_SAFETENSOR_INFERENCE"
    const val fixtureValidatorCall = "ModelFamilyFixtureValidator.validate"

    val fixtureInputIncludes = listOf(
        "$moduleNamePrefix*/$mainJavaSourcePath/**/*.java",
        "$moduleNamePrefix*/$testJavaSourcePath/**/*.java",
        "$moduleNamePrefix*/$fixtureRootPath/**",
    )

    val moduleNameRegex = Regex(moduleNamePattern)
    val rootFields = listOf("schemaVersion", "directModuleCount", "fixtureCount", "modules")
    val moduleFields = listOf("module", "fixtureCount", "fixtures", "fixtureDetails")
    val detailFields = listOf("id", "modelType", "architectures", "tokenizerMarker")
    val rootFieldSet = rootFields.toSet()
    val moduleFieldSet = moduleFields.toSet()
    val detailFieldSet = detailFields.toSet()

    val tokenizerMarkers = listOf(
        "tokenizer.json",
        "vocab.json",
        "tokenizer/tokenizer.json",
        "tokenizer/vocab.json",
    )
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageReportWriter.kt
================================================
package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageReportWriter {
    fun writeReports(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
        textReportFile: File,
        jsonReportFile: File,
        schemaFile: File,
    ): DirectFixtureCoverageWrittenReports {
        writeTextIfChanged(
            textReportFile,
            DirectFixtureCoverageReportRendering.textReport(
                directModuleCount = directModuleCount,
                fixtureCount = fixtureCount,
                reportEntries = reportEntries,
            ),
        )

        val reportPayload = DirectFixtureCoverageReportRendering.reportPayload(
            directModuleCount = directModuleCount,
            fixtureCount = fixtureCount,
            reportEntries = reportEntries,
        )
        writeTextIfChanged(
            jsonReportFile,
            DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(reportPayload),
        )

        val schemaPayload = DirectFixtureCoverageReportRendering.schemaPayload()
        writeTextIfChanged(
            schemaFile,
            DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(schemaPayload),
        )
        DirectFixtureCoverageReportContract.requireGeneratedReports(reportPayload, schemaPayload, jsonReportFile, schemaFile)

        return DirectFixtureCoverageWrittenReports(
            textReportFile = textReportFile,
            jsonReportFile = jsonReportFile,
            schemaFile = schemaFile,
        )
    }

    private fun writeTextIfChanged(file: File, text: String) {
        if (file.exists() && file.readText() == text) {
            return
        }

        file.parentFile.mkdirs()
        file.writeText(text)
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageScanner.kt
================================================
package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageScanner {
    fun scan(modelModulesDir: File): DirectFixtureCoverageScanResult {
        val modules = modelModulesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(DirectFixtureCoverageReportSpec.moduleNamePrefix) }
            ?.sortedBy { it.name }
            ?: emptyList()

        val problems = mutableListOf<String>()
        val reportEntries = mutableListOf<DirectFixtureCoverageModuleReport>()
        val fixtureOwners = linkedMapOf<String, String>()
        var directModuleCount = 0

        modules.forEach { module ->
            val mainJavaText = DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                module,
                DirectFixtureCoverageReportSpec.mainJavaSourcePath,
                DirectFixtureCoverageReportSpec.directInferenceMarker,
            )
            if (mainJavaText == null) {
                return@forEach
            }

            directModuleCount += 1
            scanDirectModule(
                module = module,
                mainJavaText = mainJavaText,
                hasFixtureValidatorCall = DirectFixtureCoverageFileSupport.containsJavaSourceText(
                    module,
                    DirectFixtureCoverageReportSpec.testJavaSourcePath,
                    DirectFixtureCoverageReportSpec.fixtureValidatorCall,
                ),
                fixtureOwners = fixtureOwners,
                reportEntries = reportEntries,
                problems = problems,
            )
        }
        if (directModuleCount == 0) {
            problems += "no direct-safetensor model-family modules were detected under $modelModulesDir"
        }

        return DirectFixtureCoverageScanResult(
            modelModuleCount = modules.size,
            directModuleCount = directModuleCount,
            uniqueFixtureCount = fixtureOwners.size,
            reportEntries = reportEntries,
            problems = problems,
        )
    }

    private fun scanDirectModule(
        module: File,
        mainJavaText: String,
        hasFixtureValidatorCall: Boolean,
        fixtureOwners: MutableMap<String, String>,
        reportEntries: MutableList<DirectFixtureCoverageModuleReport>,
        problems: MutableList<String>,
    ): Unit {
        val fixtureRoot = module.resolve(DirectFixtureCoverageReportSpec.fixtureRootPath)
        val fixtureConfigs = fixtureConfigs(fixtureRoot)
        if (fixtureConfigs.isEmpty()) {
            problems += "${module.name}: missing ${DirectFixtureCoverageReportSpec.fixtureRootPath}/*/config.json"
        } else {
            registerFixtureOwners(module, fixtureConfigs, fixtureOwners, problems)
        }

        val moduleFixtureDetails = fixtureConfigs.mapNotNull { config ->
            DirectFixtureCoverageFixtureParser.parse(module, mainJavaText, config, problems)
        }
        if (fixtureConfigs.isNotEmpty()) {
            reportEntries += DirectFixtureCoverageModuleReport(
                moduleName = module.name,
                fixtures = moduleFixtureDetails.sortedBy { it.id },
            )
        }

        if (!hasFixtureValidatorCall) {
            problems += "${module.name}: tests should call ${DirectFixtureCoverageReportSpec.fixtureValidatorCall}"
        }
    }

    private fun registerFixtureOwners(
        module: File,
        fixtureConfigs: List<File>,
        fixtureOwners: MutableMap<String, String>,
        problems: MutableList<String>,
    ): Unit {
        fixtureConfigs.map { it.parentFile.name }.forEach { fixtureName ->
            val previousOwner = fixtureOwners.putIfAbsent(fixtureName, module.name)
            if (previousOwner != null) {
                problems += "fixture '$fixtureName' is declared by both $previousOwner and ${module.name}"
            }
        }
    }

    private fun fixtureConfigs(fixtureRoot: File): List<File> {
        if (!fixtureRoot.exists()) {
            return emptyList()
        }

        return fixtureRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.map { it.resolve("config.json") }
            ?.filter { it.isFile }
            ?.sortedBy { it.parentFile.name }
            ?.toList()
            ?: emptyList()
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageScanResult.kt
================================================
package tech.kayys.aqli.buildlogic

data class DirectFixtureCoverageScanResult(
    val modelModuleCount: Int,
    val directModuleCount: Int,
    val uniqueFixtureCount: Int,
    val reportEntries: List<DirectFixtureCoverageModuleReport>,
    val problems: List<String>,
)



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageSmokeFixtures.kt
================================================
package tech.kayys.aqli.buildlogic

object DirectFixtureCoverageSmokeFixtures {
    const val moduleName = "aqli-model-smoke"
    const val fixtureId = "smoke"
    const val fixtureModelType = "smoke"
    const val architecture = "SmokeForCausalLM"
    const val fixtureTokenizerMarker = "tokenizer.json"
    const val mismatchedFixtureDetailId = "other"
    const val unsupportedTokenizerMarker = "merges.txt"

    fun reportEntries(): List<DirectFixtureCoverageModuleReport> =
        listOf(
            DirectFixtureCoverageModuleReport(
                moduleName = moduleName,
                fixtures = listOf(fixture()),
            ),
        )

    fun incompleteReportEntries(): List<DirectFixtureCoverageModuleReport> =
        listOf(
            DirectFixtureCoverageModuleReport(
                moduleName = moduleName,
                fixtures = listOf(fixture(modelType = "")),
            ),
        )

    fun fixture(modelType: String = fixtureModelType): DirectFixtureCoverage =
        DirectFixtureCoverage(
            id = fixtureId,
            modelType = modelType,
            architectures = listOf(architecture),
            tokenizerMarker = fixtureTokenizerMarker,
        )

    fun reportModuleMap(
        fixtureDetailId: String = fixtureId,
        tokenizerMarker: String = fixtureTokenizerMarker,
    ): Map<String, Any> =
        mapOf(
            "module" to moduleName,
            "fixtureCount" to 1,
            "fixtures" to listOf(fixtureId),
            "fixtureDetails" to listOf(
                mapOf(
                    "id" to fixtureDetailId,
                    "modelType" to fixtureModelType,
                    "architectures" to listOf(architecture),
                    "tokenizerMarker" to tokenizerMarker,
                ),
            ),
        )

    fun mismatchedFixtureDetailModuleMap(): Map<String, Any> =
        reportModuleMap(fixtureDetailId = mismatchedFixtureDetailId)

    fun unsupportedTokenizerModuleMap(): Map<String, Any> =
        reportModuleMap(tokenizerMarker = unsupportedTokenizerMarker)
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageSpecSmoke.kt
================================================
package tech.kayys.aqli.buildlogic

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files

private typealias SpecSmokeFixtures = DirectFixtureCoverageSmokeFixtures

object DirectFixtureCoverageSpecSmoke {
    fun requireSmokeContract(): Unit {
        val spec = DirectFixtureCoverageReportSpec
        if (!spec.moduleNameRegex.matches("${spec.moduleNamePrefix}smoke")) {
            throw GradleException("Direct fixture coverage module prefix no longer matches module regex")
        }
        if (spec.fixtureInputIncludes != listOf(
                "${spec.moduleNamePrefix}*/${spec.mainJavaSourcePath}/**/*.java",
                "${spec.moduleNamePrefix}*/${spec.testJavaSourcePath}/**/*.java",
                "${spec.moduleNamePrefix}*/${spec.fixtureRootPath}/**",
            )
        ) {
            throw GradleException("Direct fixture coverage input include spec drifted")
        }
        if (spec.directInferenceMarker.isBlank() || spec.fixtureValidatorCall.isBlank()) {
            throw GradleException("Direct fixture coverage scanner marker spec drifted")
        }
        requireFileSupportSmoke(spec)
        requireScannerCountSmoke(spec)
        requireTaskMessageSmoke()
    }

    private fun requireFileSupportSmoke(spec: DirectFixtureCoverageReportSpec): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-source-").toFile()
        try {
            val directModule = smokeDir.resolve("direct")
            val nonDirectModule = smokeDir.resolve("non-direct")
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "z/Direct.java",
                "final class Direct { static final String MODE = \"${spec.directInferenceMarker}\"; }",
            )
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "a/Before.java",
                "final class Before {}",
            )
            writeJavaSource(
                nonDirectModule,
                spec.mainJavaSourcePath,
                "Only.java",
                "final class Only {}",
            )

            if (DirectFixtureCoverageFileSupport.containsJavaSourceText(
                    nonDirectModule,
                    spec.mainJavaSourcePath,
                    spec.directInferenceMarker,
                )
            ) {
                throw GradleException("Direct fixture coverage source smoke detected a missing marker")
            }
            val directSource = DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                directModule,
                spec.mainJavaSourcePath,
                spec.directInferenceMarker,
            ) ?: throw GradleException("Direct fixture coverage source smoke missed a direct marker")
            if (DirectFixtureCoverageFileSupport.javaSourceTextIfContains(
                    nonDirectModule,
                    spec.mainJavaSourcePath,
                    spec.directInferenceMarker,
                ) != null
            ) {
                throw GradleException("Direct fixture coverage source smoke aggregated a non-direct module")
            }
            if (directSource.indexOf("final class Before") !in 0 until directSource.indexOf("final class Direct")) {
                throw GradleException("Direct fixture coverage source smoke ordering drifted")
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun writeJavaSource(module: File, relativeDir: String, relativePath: String, text: String) {
        val sourceFile = module.resolve(relativeDir).resolve(relativePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(text)
    }

    private fun requireScannerCountSmoke(spec: DirectFixtureCoverageReportSpec): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-scanner-").toFile()
        try {
            val directModule = smokeDir.resolve("${spec.moduleNamePrefix}direct-smoke")
            val nonDirectModule = smokeDir.resolve("${spec.moduleNamePrefix}plain-smoke")
            writeJavaSource(
                directModule,
                spec.mainJavaSourcePath,
                "DirectSmoke.java",
                """
                final class DirectSmoke {
                    static final String MARKER = "${spec.directInferenceMarker}";
                    static final String MODEL = "${SpecSmokeFixtures.fixtureModelType}";
                    static final String ARCH = "${SpecSmokeFixtures.architecture}";
                }
                """.trimIndent(),
            )
            writeJavaSource(
                directModule,
                spec.testJavaSourcePath,
                "DirectSmokeTest.java",
                "final class DirectSmokeTest { void test() { ${spec.fixtureValidatorCall}(null); } }",
            )
            writeFixtureConfig(directModule, spec)
            writeJavaSource(
                nonDirectModule,
                spec.mainJavaSourcePath,
                "PlainSmoke.java",
                "final class PlainSmoke {}",
            )

            val scan = DirectFixtureCoverageScanner.scan(smokeDir)
            DirectFixtureCoverageGuards.requireProblemsClear(scan.problems)
            if (scan.modelModuleCount != 2 || scan.directModuleCount != 1 || scan.uniqueFixtureCount != 1) {
                throw GradleException(
                    "Direct fixture coverage scanner count smoke drifted: " +
                            "modelModuleCount=${scan.modelModuleCount}, " +
                            "directModuleCount=${scan.directModuleCount}, " +
                            "uniqueFixtureCount=${scan.uniqueFixtureCount}",
                )
            }
            if (scan.reportEntries.map { it.moduleName } != listOf(directModule.name)) {
                throw GradleException(
                    "Direct fixture coverage scanner report module smoke drifted: " +
                            "modules=${scan.reportEntries.map { it.moduleName }}",
                )
            }
            if (scan.reportEntries.single().fixtures.map { it.id } != listOf(SpecSmokeFixtures.fixtureId)) {
                throw GradleException(
                    "Direct fixture coverage scanner fixture id smoke drifted: " +
                            "fixtureIds=${scan.reportEntries.single().fixtures.map { it.id }}",
                )
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }

    private fun writeFixtureConfig(module: File, spec: DirectFixtureCoverageReportSpec) {
        val fixtureDir = module.resolve(spec.fixtureRootPath).resolve(SpecSmokeFixtures.fixtureId)
        fixtureDir.mkdirs()
        fixtureDir.resolve("config.json").writeText(
            """
            {
              "model_type": "${SpecSmokeFixtures.fixtureModelType}",
              "architectures": ["${SpecSmokeFixtures.architecture}"]
            }
            """.trimIndent(),
        )
        fixtureDir.resolve(SpecSmokeFixtures.fixtureTokenizerMarker).writeText("{}")
    }

    private fun requireTaskMessageSmoke(): Unit {
        val smokeDir = Files.createTempDirectory("aqli-direct-fixture-messages-").toFile()
        try {
            val writtenReports = DirectFixtureCoverageWrittenReports(
                textReportFile = smokeDir.resolve("report.txt"),
                jsonReportFile = smokeDir.resolve("report.json"),
                schemaFile = smokeDir.resolve("report.schema.json"),
            )
            val scan = DirectFixtureCoverageScanResult(
                modelModuleCount = 2,
                directModuleCount = 1,
                uniqueFixtureCount = 1,
                reportEntries = SpecSmokeFixtures.reportEntries(),
                problems = emptyList(),
            )
            val validationSummary = DirectFixtureCoverageTaskMessages.validationSummary(
                scan = scan,
                writtenReports = writtenReports,
                projectDir = smokeDir,
            )
            if (!validationSummary.contains("1 direct model-family module")
                || !validationSummary.contains("report.txt, report.json, report.schema.json")
            ) {
                throw GradleException(
                    "Direct fixture coverage task validation summary smoke drifted: $validationSummary",
                )
            }
            val timingSummary = DirectFixtureCoverageTaskMessages.timingSummary(
                scan = scan,
                startedAt = 0,
                scannedAt = 1_000_000,
                guardedAt = 3_000_000,
                reportedAt = 6_000_000,
            )
            if (!timingSummary.contains("modules=2, directModules=1, fixtures=1")
                || !timingSummary.contains("scan=1ms, guard=2ms, report=3ms, total=6ms")
            ) {
                throw GradleException(
                    "Direct fixture coverage task timing summary smoke drifted: $timingSummary",
                )
            }
        } finally {
            smokeDir.deleteRecursively()
        }
    }
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageTaskMessages.kt
================================================
package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageTaskMessages {
    fun validationSummary(
        scan: DirectFixtureCoverageScanResult,
        writtenReports: DirectFixtureCoverageWrittenReports,
        projectDir: File,
    ): String =
        "Validated fixture coverage for ${scan.directModuleCount} direct model-family " +
                "module${pluralSuffix(scan.directModuleCount)}. " +
                "Reports: ${writtenReports.relativeSummary(projectDir)}"

    fun timingSummary(
        scan: DirectFixtureCoverageScanResult,
        startedAt: Long,
        scannedAt: Long,
        guardedAt: Long,
        reportedAt: Long,
    ): String =
        "Direct fixture coverage timings: modules=${scan.modelModuleCount}, " +
                "directModules=${scan.directModuleCount}, " +
                "fixtures=${scan.uniqueFixtureCount}, " +
                "scan=${elapsedMillis(startedAt, scannedAt)}ms, " +
                "guard=${elapsedMillis(scannedAt, guardedAt)}ms, " +
                "report=${elapsedMillis(guardedAt, reportedAt)}ms, " +
                "total=${elapsedMillis(startedAt, reportedAt)}ms"

    private fun elapsedMillis(startedAt: Long, finishedAt: Long): Long =
        (finishedAt - startedAt) / 1_000_000

    private fun pluralSuffix(count: Int): String =
        if (count == 1) "" else "s"
}



================================================
FILE: buildSrc/src/main/kotlin/tech/kayys/aqli/buildlogic/DirectFixtureCoverageWrittenReports.kt
================================================
package tech.kayys.aqli.buildlogic

import java.io.File

data class DirectFixtureCoverageWrittenReports(
    val textReportFile: File,
    val jsonReportFile: File,
    val schemaFile: File,
) {
    fun relativeSummary(rootDir: File): String =
        listOf(textReportFile, jsonReportFile, schemaFile)
            .joinToString(separator = ", ") { it.relativeTo(rootDir).path }
}



================================================
FILE: config/default.config.yaml
================================================
aqli:
  provider:
    runner: runner-gguf
    defaultModel: google/gemma-4-E2B-it-Q4_K_M

  plugins:
    - name:
      id:
      path:
      enable: true


  strategies:
    speculative:
      enabled: true
      priority: 100
      parameters:
        gamma: 5                    # draft tokens per step
        draftProvider: "local-llama-1b"
        targetProvider: "local-llama-70b"
        acceptanceThreshold: 0.8
    
    cascade:
      enabled: true
      priority: 80
      parameters:
        modelChain: ["local-7b", "cloud-70b", "cloud-405b"]
        confidenceThreshold: 0.85
        maxSwitches: 2
    
    modelSwitch:
      enabled: true
      priority: 60
      parameters:
        trigger: "memory-pressure"  # or "latency", "quality", "manual"
        preserveKV: true            # migrate KV cache on switch
    
    splitPhase:
      enabled: false                # disabled by default
      priority: 40
      parameters:
        prefillProvider: "cloud-a100"
        decodeProvider: "local-4090"
        splitThreshold: 512         # tokens
    
    moe:
      enabled: false
      priority: 20
      parameters:
        expertMap:
          "coding": "local-code-16b"
          "math": "cloud-math-70b"
          "general": "local-7b"

  memory:
    pool:
      type: "lock-free"             # or "synchronized", "jemalloc"
      maxBytes: 32_000_000_000L     # 32GB
    kvCache:
      type: "paged"                 # or "monolithic"
      blockSize: 16                 # tokens per block
      pageSize: 256                 # bytes alignment


================================================
FILE: data/keys.json
================================================
["community"]


================================================
FILE: docs/API_EXAMPLES.md
================================================
# Aqli API — Complete curl Examples

**Base URL**: `http://localhost:9131`
**Authentication**: Add header `-H 'X-API-Key: community'`

Note: Some clients (including curl without explicit Accept headers) may receive an HTML 404 page from Quarkus. If you see "Resource not found" HTML, add `-H 'Accept: application/json'` to your request. Examples below include the Accept header where appropriate.

---

## 🏥 Health Check

### Check Server Status
```bash
curl http://localhost:9131/health
```

**Response:**
```json
{
  "status": "ok"
}
```

---

## 📦 Models Management

### List All Available Models

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models'
```

**Options:**
```bash
# List only runnable models
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models?runnableOnly=true'

# Limit results to 10
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models?limit=10'

# OpenAI-compatible format
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models?compat=openai'

# Filter by namespace
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models?namespace=huggingface'
```

**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "id": "gpt-3.5-turbo",
      "object": "model",
      "created": 1234567890,
      "owned_by": "aqli"
    }
  ]
}
```

### Get Model Details

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models/gpt-3.5-turbo'
```

**Response:**
```json
{
  "modelId": "gpt-3.5-turbo",
  "format": "gguf",
  "description": "GPT-3.5 Turbo model",
  "size": 4294967296
}
```

### Get Model Capabilities

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models/gpt-3.5-turbo/capabilities'
```

**Response:**
```json
{
  "modelId": "gpt-3.5-turbo",
  "capabilities": {
    "streaming": true,
    "tools": true,
    "vision": false,
    "function_calling": true
  }
}
```

### Pull (Download) a Model

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "modelSpec": "gpt-3.5-turbo:latest",
    "revision": "main",
    "force": false
  }' \
  'http://localhost:9131/v1/models/pull'
```

**Response:**
```json
{
  "status": "pull_started",
  "jobId": "job-12345-abcde"
}
```

### Stream Model Pull Progress

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models/pull/stream/job-12345-abcde' \
  -H 'Accept: text/event-stream'
```

**Response (SSE Stream):**
```
data: {"jobId":"job-12345-abcde","status":"downloading","progress":25,"total":100}
data: {"jobId":"job-12345-abcde","status":"downloading","progress":50,"total":100}
data: {"jobId":"job-12345-abcde","status":"completed","progress":100,"total":100}
```

### Delete a Model

```bash
curl -X DELETE -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models/gpt-3.5-turbo'
```

**Response:** 204 No Content

---

## 💬 Chat Completions (OpenAI-Compatible)

### Simple Chat Completion (Non-Streaming)

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "Hello! What is 2 + 2?"
      }
    ],
    "max_tokens": 100,
    "temperature": 0.7
  }' \
  'http://localhost:9131/v1/chat/completions'
```

**Response:**
```json
{
  "id": "chatcmpl-8MlyL5dPT8qXX0q7L0F4Z5E6",
  "object": "chat.completion",
  "created": 1699107661,
  "model": "gpt-3.5-turbo",
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 5,
    "total_tokens": 15
  },
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "2 + 2 = 4"
      },
      "finish_reason": "stop",
      "index": 0
    }
  ]
}
```

### Chat with System Prompt

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful assistant that always responds in Spanish."
      },
      {
        "role": "user",
        "content": "Hello!"
      }
    ]
  }' \
  'http://localhost:9131/v1/chat/completions'
```

### Streaming Chat Completion (Server-Sent Events)

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "Write a haiku about clouds"
      }
    ],
    "stream": true,
    "temperature": 0.8
  }' \
  'http://localhost:9131/v1/chat/completions'
```

**Response (SSE Stream):**
```
data: {"id":"chatcmpl-8MlyL5dPT8qXX0q7L0F4Z5E6","choices":[{"delta":{"role":"assistant","content":"Clouds"},"index":0}]}
data: {"id":"chatcmpl-8MlyL5dPT8qXX0q7L0F4Z5E6","choices":[{"delta":{"content":" drift"},"index":0}]}
data: {"id":"chatcmpl-8MlyL5dPT8qXX0q7L0F4Z5E6","choices":[{"delta":{"content":" gently"},"index":0}]}
data: [DONE]
```

### Chat with Function Calling (Tools)

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "What is the weather in Seattle?"
      }
    ],
    "tools": [
      {
        "type": "function",
        "function": {
          "name": "get_weather",
          "description": "Get weather for a location",
          "parameters": {
            "type": "object",
            "properties": {
              "location": {
                "type": "string",
                "description": "City name"
              }
            },
            "required": ["location"]
          }
        }
      }
    ],
    "tool_choice": "auto"
  }' \
  'http://localhost:9131/v1/chat/completions'
```

### Chat with Vision (Image Analysis)

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-4-vision",
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "text",
            "text": "What is in this image?"
          },
          {
            "type": "image_url",
            "image_url": {
              "url": "https://example.com/image.jpg"
            }
          }
        ]
      }
    ]
  }' \
  'http://localhost:9131/v1/chat/completions'
```

### Chat with RAG Context

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "Based on the documentation, how do I use this feature?"
      }
    ],
    "metadata": {
      "rag_context": [
        {
          "source": "docs/feature.md",
          "content": "To use this feature, you need to...",
          "relevance": 0.95
        }
      ]
    }
  }' \
  'http://localhost:9131/v1/chat/completions'
```

---

## 🔌 Embeddings

### Create Embeddings

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "The quick brown fox",
    "encoding_format": "float"
  }' \
  'http://localhost:9131/v1/embeddings'
```

**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.123, 0.456, 0.789, ...],
      "index": 0
    }
  ],
  "model": "text-embedding-ada-002",
  "usage": {
    "prompt_tokens": 4,
    "total_tokens": 4
  }
}
```

### Batch Embeddings

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "text-embedding-ada-002",
    "input": [
      "First sentence",
      "Second sentence",
      "Third sentence"
    ]
  }' \
  'http://localhost:9131/v1/embeddings'
```

---

## 🤖 Inference

### Raw Inference Request

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{
    "modelId": "gpt-3.5-turbo",
    "prompt": "The answer to life is",
    "maxTokens": 50,
    "temperature": 0.7,
    "topP": 0.9,
    "topK": 40
  }' \
  'http://localhost:9131/v1/inference'
```

**Response:**
```json
{
  "modelId": "gpt-3.5-turbo",
  "text": "The answer to life is 42, according to Douglas Adams.",
  "finishReason": "stop",
  "tokenCount": 12,
  "executionTimeMs": 245
}
```

### Streaming Inference

```bash
curl -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "modelId": "gpt-3.5-turbo",
    "prompt": "Story about a robot:",
    "stream": true,
    "maxTokens": 100
  }' \
  'http://localhost:9131/v1/inference'
```

---

## 📋 Providers

### List Available Providers

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/providers'
```

**Response:**
```json
{
  "providers": [
    {
      "id": "cpu",
      "name": "CPU Provider",
      "type": "native",
      "status": "available",
      "capabilities": ["text-generation", "embeddings"]
    },
    {
      "id": "metal",
      "name": "Metal Provider",
      "type": "gpu",
      "status": "available",
      "capabilities": ["text-generation", "embeddings"]
    }
  ]
}
```

### Get Provider Details

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/providers/metal'
```

---

## 🔍 System Info

### Get System Information

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/system'
```

**Response:**
```json
{
  "version": "0.1.0",
  "platform": "darwin-aarch64",
  "javaVersion": "25.0.0",
  "memoryMb": 24576,
  "cpuCores": 10,
  "backends": ["cpu", "metal"],
  "formats": ["gguf", "onnx", "safetensor", "litert"],
  "uptime": 3600
}
```

---

## 🔐 Admin

### Get Server Configuration

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/admin/config'
```

---

## 📚 Jobs Management

### Get Job Status

```bash
curl -H 'X-API-Key: community' \
  'http://localhost:9131/v1/jobs/job-12345-abcde'
```

**Response:**
```json
{
  "jobId": "job-12345-abcde",
  "type": "pull",
  "status": "in_progress",
  "progress": 75,
  "startedAt": "2024-01-15T10:30:00Z",
  "result": null
}
```

### Cancel Job

```bash
curl -X DELETE -H 'X-API-Key: community' \
  'http://localhost:9131/v1/jobs/job-12345-abcde'
```

---

## 🛠️ Common Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `model` | string | Model identifier |
| `messages` | array | Chat message history |
| `max_tokens` | integer | Max completion tokens (default: 256) |
| `temperature` | float | Sampling temperature (0.0-2.0, default: 0.7) |
| `top_p` | float | Nucleus sampling parameter (default: 1.0) |
| `top_k` | integer | Top-K sampling (default: 40) |
| `stream` | boolean | Enable streaming SSE response |
| `stop` | array | Stop sequences |
| `tools` | array | Available function definitions |
| `tool_choice` | string | Tool selection strategy: "auto", "none", specific name |

---

## 🔐 Authentication

All requests (except `/health`) require one of:

### API Key Header
```bash
curl -H 'X-API-Key: community' http://localhost:9131/v1/models
```

### Bearer Token
```bash
curl -H 'Authorization: Bearer community' http://localhost:9131/v1/models
```

---

## ⚡ Performance Tips

1. **Use streaming** for long completions to see results faster
2. **Batch embeddings** instead of single requests
3. **Limit results** with query parameters to reduce payload
4. **Use appropriate temperature**: Lower (0.3-0.5) for consistency, higher (0.8-1.2) for creativity
5. **Specify exact model** instead of querying model list multiple times

---

## 🐛 Error Handling

All errors follow this format:

```json
{
  "error": {
    "message": "Model not found",
    "type": "not_found",
    "code": 404
  }
}
```

### Common Error Codes
- `400`: Invalid request format
- `401`: Authentication failed
- `404`: Resource not found
- `429`: Rate limit exceeded
- `500`: Internal server error

---

## 📝 Testing with jq

Parse and format responses:

```bash
curl -s -H 'Accept: application/json' -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models' | jq '.data[] | {id, object}'
```

Extract nested data:

```bash
curl -s -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"Hi"}]}' \
  'http://localhost:9131/v1/chat/completions' | jq '.choices[0].message.content'
```

---

## 🧪 Automated Testing

Use the provided test script to validate all endpoints:

```bash
# Run all tests
bash API_TEST_SCRIPT.sh http://localhost:9131 community false

# Run with verbose output
bash API_TEST_SCRIPT.sh http://localhost:9131 community true

# Use custom API key
bash API_TEST_SCRIPT.sh http://localhost:9131 "your-api-key" false
```

**Expected output**: 8/9 tests passing (authentication test may vary based on security configuration)

---

## 🚀 Quick Start Example

Complete script to get started:

```bash
#!/bin/bash

API_KEY="community"
BASE_URL="http://localhost:9131"
HEADERS=(-H "X-API-Key: $API_KEY" -H "Accept: application/json" -H "Content-Type: application/json")

# 1. Health check
echo "1. Checking server health..."
curl -s "${HEADERS[@]}" "$BASE_URL/health" | jq .

# 2. List models
echo "2. Listing available models..."
curl -s "${HEADERS[@]}" "$BASE_URL/v1/models" | jq '.[] | {id, object}' | head -20

# 3. Simple chat
echo "3. Sending chat message..."
curl -s -X POST "${HEADERS[@]}" \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"Hello Aqli!"}]}' \
  "$BASE_URL/v1/chat/completions" | jq '.choices[0].message'
```

---

## 📋 Environment Variables Setup

For convenience, set these environment variables:

```bash
export AQLI_API_KEY="community"
export AQLI_BASE_URL="http://localhost:9131"

# Then use in curl:
curl -H "X-API-Key: $AQLI_API_KEY" \
  -H "Accept: application/json" \
  "$AQLI_BASE_URL/v1/models"
```





================================================
FILE: docs/API_QUICK_REFERENCE.md
================================================
# Aqli API Quick Reference

## Overview

Aqli exposes an **OpenAI-compatible REST API** for:
- Chat completions (streaming & non-streaming)
- Embeddings
- Model management
- Provider information
- System diagnostics

---

## 🚀 Getting Started (5 minutes)

### 1. Start the Server

```bash
cd aqli
./scripts/run-dev.sh serve --debug
```

Server starts on: `http://localhost:9131`

### 2. Test Health

```bash
curl -H 'Accept: application/json' http://localhost:9131/health
# Response: {"status":"ok"}
```

### 3. List Models

```bash
curl -H 'X-API-Key: community' \
     -H 'Accept: application/json' \
     http://localhost:9131/v1/models
```

### 4. Send Chat Message

```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Hello"}],
    "max_tokens": 100
  }' \
  http://localhost:9131/v1/chat/completions
```

---

## 📚 Essential Headers

Every request (except `/health`) requires **one of**:

```bash
# Option 1: API Key Header (Recommended)
-H 'X-API-Key: community'

# Option 2: Bearer Token
-H 'Authorization: Bearer community'
```

**Always include Accept header for JSON responses:**
```bash
-H 'Accept: application/json'
```

---

## 🔑 Common Endpoints

| Endpoint | Method | Purpose | Auth Required |
|----------|--------|---------|---------------|
| `/health` | GET | Server health | No |
| `/v1/models` | GET | List models | Yes |
| `/v1/models/:id` | GET | Model details | Yes |
| `/v1/chat/completions` | POST | Chat completion | Yes |
| `/v1/embeddings` | POST | Create embeddings | Yes |
| `/v1/providers` | GET | List providers | Yes |
| `/v1/system` | GET | System info | Yes |

---

## 💬 Chat Completions

### Non-Streaming Request

```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {"role": "system", "content": "You are helpful."},
      {"role": "user", "content": "What is 2+2?"}
    ],
    "temperature": 0.7,
    "max_tokens": 100
  }' \
  http://localhost:9131/v1/chat/completions
```

### Streaming Request

```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }' \
  http://localhost:9131/v1/chat/completions
```

Response is **Server-Sent Events** format:
```
data: {"choices":[{"delta":{"content":"Hello"}}]}
data: {"choices":[{"delta":{"content":" there"}}]}
data: [DONE]
```

---

## 🔌 Embeddings

```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "text-embedding-ada-002",
    "input": "The quick brown fox"
  }' \
  http://localhost:9131/v1/embeddings
```

**Batch embeddings:**
```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "text-embedding-ada-002",
    "input": ["Text 1", "Text 2", "Text 3"]
  }' \
  http://localhost:9131/v1/embeddings
```

---

## 📊 Response Format

### Success Response (200)
```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1699107661,
  "model": "gpt-3.5-turbo",
  "choices": [
    {
      "message": {"role": "assistant", "content": "..."},
      "finish_reason": "stop",
      "index": 0
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 5,
    "total_tokens": 15
  }
}
```

### Error Response (4xx/5xx)
```json
{
  "error": {
    "message": "Model not found",
    "type": "not_found",
    "code": 404
  }
}
```

---

## ⚙️ Query Parameters

### `/v1/models` Query Parameters
- `limit` (int, default 50) - Max results to return
- `runnableOnly` (boolean) - Only return loadable models
- `compat` (string) - `openai` for OpenAI-compatible format
- `namespace` (string) - Filter by model namespace

Examples:
```bash
# List 10 models
/v1/models?limit=10

# Only runnable models
/v1/models?runnableOnly=true

# OpenAI format
/v1/models?compat=openai
```

---

## 🛠️ Common Parameter Reference

For chat completions:

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| `model` | string | - | - | Model identifier |
| `messages` | array | - | - | Message history |
| `temperature` | float | 0.7 | 0.0-2.0 | Sampling temperature |
| `max_tokens` | int | 256 | 1-4096 | Max response tokens |
| `top_p` | float | 1.0 | 0.0-1.0 | Nucleus sampling |
| `top_k` | int | 40 | 1-1000 | Top-K sampling |
| `stream` | boolean | false | - | Stream responses |
| `stop` | array | - | - | Stop sequences |
| `tools` | array | - | - | Available functions |

---

## 🔍 Useful Tools

### Using jq to parse responses:

```bash
# Extract first choice content
curl -s -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models' | jq '.data[] | .id' | head -5

# Pretty print
curl -s -X POST -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"Hi"}]}' \
  'http://localhost:9131/v1/chat/completions' | jq '.'
```

### Measure response time:

```bash
curl -w "Time: %{time_total}s\n" \
  -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models'
```

### Save response to file:

```bash
curl -O -H 'X-API-Key: community' \
  'http://localhost:9131/v1/models' -o models.json
```

---

## 📝 Example: Python Client

```python
import requests
import json

API_KEY = "community"
BASE_URL = "http://localhost:9131"
headers = {
    "X-API-Key": API_KEY,
    "Accept": "application/json",
    "Content-Type": "application/json"
}

# List models
response = requests.get(f"{BASE_URL}/v1/models", headers=headers)
models = response.json()
print(f"Available models: {len(models)}")

# Send chat message
payload = {
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 50
}
response = requests.post(
    f"{BASE_URL}/v1/chat/completions",
    headers=headers,
    json=payload
)
result = response.json()
print(result['choices'][0]['message']['content'])
```

---

## 🐛 Troubleshooting

### Issue: "Resource not found" (HTML response)

**Solution**: Add `Accept: application/json` header
```bash
# Wrong
curl http://localhost:9131/v1/models

# Correct
curl -H 'Accept: application/json' http://localhost:9131/v1/models
```

### Issue: "Invalid API key" (403)

**Solution**: Verify API key
```bash
# Check default key
curl -H 'X-API-Key: community' -H 'Accept: application/json' \
  http://localhost:9131/v1/models
```

### Issue: Connection refused

**Solution**: Verify server is running
```bash
# Check port
netstat -anv | grep 9131

# Check health
curl http://localhost:9131/health
```

### Issue: Slow responses

**Solution**: Models may not be loaded
- Check `/v1/models` - should list available models
- Load a model: `/v1/models/pull` endpoint
- Use `/v1/system` to check available resources

---

## 📖 Full API Documentation

See `API_EXAMPLES.md` for comprehensive endpoint documentation with response examples.

Run automated tests:
```bash
bash API_TEST_SCRIPT.sh http://localhost:9131 community false
```

---

## 🚀 Deployment

### Production Setup

1. Change default API key:
```bash
export AQLI_API_KEYS="your-secure-key-1,your-secure-key-2"
```

2. Set resources:
```bash
export JAVA_OPTS="-Xmx32g -XX:MaxDirectMemorySize=32g"
```

3. Use reverse proxy (nginx):
```nginx
server {
    listen 443 ssl;
    server_name api.example.com;
    
    location / {
        proxy_pass http://localhost:9131;
        proxy_set_header X-API-Key $http_x_api_key;
    }
}
```

---

## 📞 Support

- **Logs**: `~/.aqli/server/logs/server.log`
- **Issue reporting**: Check logs, include curl output with `-v` flag
- **Rate limiting**: Not implemented yet (coming soon)




================================================
FILE: docs/error-codes.md
================================================
# Error Codes
Documentation generation skipped - ErrorCodeDoc utility removed.



================================================
FILE: docs/install-profiles.md
================================================
# Aqli Install Profiles

Aqli can be built and installed with different runtime preferences. Use an
install profile when you want the installer to choose defaults for backend,
model formats, architecture, and runtime behavior.

## Release Installer

```bash
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile auto
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile metal
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile cpu
```

You can also use an environment variable:

```bash
AQLI_INSTALL_PROFILE=metal \
  curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash
```

The installer writes the selected profile to:

```text
$AQLI_HOME/config/profile.env
$AQLI_HOME/config/profile.json
```

On macOS and Linux release installs, `/usr/local/bin/aqli` is a small wrapper
that sources `profile.env` before it launches the installed runtime binary.

## Source Build Installer

For source builds, run the builder UI and choose an install preference profile:

```bash
./create-builder-script.sh
./scripts/install-local-macos.sh
```

The builder writes the resolved module selection to:

```text
scripts/module-selection-current.env
```

## Profiles

| Profile | Best for | Main defaults |
| --- | --- | --- |
| `auto` | Most users | Detects `metal` on macOS, `cuda` on NVIDIA Linux, otherwise `cpu` |
| `cpu` | Portable installs and CI | CPU backend, GGUF, ONNX, safetensor, text models |
| `metal` | Apple Silicon local AI | Metal backend, GGUF, LiteRT, ONNX, safetensor, text and multimodal |
| `cuda` | NVIDIA workstations and servers | CUDA backend, GGUF, ONNX, safetensor, all LLM types |
| `mobile` | Flutter/mobile edge work | Mobile runtime, LiteRT, ONNX, text, speech-to-text, vision |
| `full` | Development builds | All runtime families, all formats, all model types |

Use `--list-profiles` to print the release installer choices.



================================================
FILE: docs/OBSERVABILITY.md
================================================
[Empty file]


================================================
FILE: docs/QUICKSTART.md
================================================
# Aqli Quickstart

Get started with Aqli ML framework in 5 minutes.

---

## 1. Install

### Option A: JBang (No build tools)

```bash
curl -Ls https://sh.jbang.dev | bash -s -
```

### Option B: Maven

```xml
<dependency>
    <groupId>tech.kayys.aqli</groupId>
    <artifactId>aqli-ml-api</artifactId>
    <version>0.3.0-SNAPSHOT</version>
</dependency>
```

### Option C: Clone & Build

```bash
git clone https://github.com/bhangun/aqli.git
cd aqli
mvn clean install -DskipTests
```

---

## 2. Verify Installation

```bash
# Via JBang
jbang examples/jbang/common/hello_aqli.java

# Via Maven
cd framework/lib/aqli-ml-examples
mvn exec:java -Dexec.mainClass=tech.kayys.aqli.train.examples.SimpleFFNExample
```

Expected output:
```
✓ Aqli SDK installed successfully
✓ Tensor operations working
```

---

## 3. Create Your First Model

### Using JBang

Create `my_model.java`:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS tech.kayys.aqli:aqli-ml-ml:0.3.0-SNAPSHOT
//COMPILE_OPTIONS --enable-preview --add-modules jdk.incubator.vector
//RUNTIME_OPTIONS --enable-preview --add-modules jdk.incubator.vector
//REPOS local,mavencentral,github=https://maven.pkg.github.com/bhangun/aqli

import tech.kayys.aqli.ml.Aqli;
import tech.kayys.aqli.ml.tensor.Tensor;
import tech.kayys.aqli.ml.nn.Linear;
import tech.kayys.aqli.ml.nn.ReLU;
import tech.kayys.aqli.ml.nn.Sequential;

public class my_model {
    public static void main(String[] args) {
        // Build a simple neural network
        Sequential model = Sequential.builder()
            .add(new Linear(784, 256))
            .add(new ReLU())
            .add(new Linear(256, 10))
            .build();

        // Create random input (simulating a 28x28 image)
        Tensor input = Aqli.randn(1, 784);

        // Forward pass
        Tensor output = model.forward(input);

        System.out.println("Model: 784 → 256 → 10");
        System.out.println("Input shape: [1, 784]");
        System.out.println("Output shape: " + java.util.Arrays.toString(output.shape()));
    }
}
```

Run:
```bash
jbang my_model.java
```

---

## 4. Run Inference

```java
import tech.kayys.aqli.ml.runner.*;

ModelRunner runner = ModelRunner.builder()
    .modelPath("model.onnx")
    .device(RunnerDevice.CUDA)
    .build();

InferenceResult result = runner.infer(
    InferenceInput.fromFloats(inputData, 1, 3, 224, 224)
);

System.out.println("Output: " + result.primaryOutput().asFloats());
System.out.println("Latency: " + result.latency());

runner.close();
```

---

## 5. Explore Examples

```bash
# Browse all examples
ls examples/jbang/

# Run comprehensive demo
jbang examples/jbang/sdk/unified_framework_demo.java --demo all

# Open Jupyter notebooks
cd examples/jupyter/
jupyter notebook
```

---

## Next Steps

- **[Examples Setup](examples/docs/SETUP.md) - Detailed example setup guide
- **[SDK Installation](docs/setup/sdk-installation.md) - Full installation guide
- **[Framework Guide](website/aqli-ai.github.io/docs/framework/) - ML framework docs
- **[Inference Guide](website/aqli-ai.github.io/docs/framework/inference.md) - Model inference docs



================================================
FILE: docs/README_API.md
================================================
# Aqli API Documentation

Complete API documentation for the Aqli agentic AI inference server.

## 📚 Documentation Files

This directory contains comprehensive API documentation:

### **Quick Start** → [`API_QUICK_REFERENCE.md`](API_QUICK_REFERENCE.md)
- 5-minute quick start
- Common endpoints table
- Essential headers
- Example requests for chat, embeddings, models
- Troubleshooting guide
- **START HERE** ⭐

### **Full Examples** → [`API_EXAMPLES.md`](API_EXAMPLES.md)
- Detailed endpoint documentation
- All parameters explained
- Request/response examples for every endpoint
- Error handling
- Advanced features (streaming, tools, vision, RAG)
- Performance tips

### **Test Suite** → [`API_TEST_SCRIPT.sh`](API_TEST_SCRIPT.sh)
- Automated endpoint testing
- 9 comprehensive test cases
- Colored output with pass/fail
- Verbose mode for debugging

---

## 🚀 Start the Server

```bash
cd aqli
./scripts/run-dev.sh serve --debug
```

Server runs on **`http://localhost:9131`**

---

## ✅ Quick Test

```bash
# Health check (no auth needed)
curl -H 'Accept: application/json' http://localhost:9131/health

# List models (auth required)
curl -H 'X-API-Key: community' \
     -H 'Accept: application/json' \
     http://localhost:9131/v1/models

# Chat completion
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"Hello"}]}' \
  http://localhost:9131/v1/chat/completions

# Run full test suite
bash API_TEST_SCRIPT.sh http://localhost:9131 community false
```

---

## 🔐 Authentication

Every endpoint (except `/health`) requires:

```bash
# API Key method (recommended)
-H 'X-API-Key: community'

# Bearer token method
-H 'Authorization: Bearer community'
```

---

## 📋 Supported Features

✅ **Chat Completions**
- Streaming & non-streaming
- System prompts
- Function calling (tools)
- Vision capabilities
- RAG context injection

✅ **Embeddings**
- Single & batch processing
- Multiple encoding formats

✅ **Model Management**
- List/search models
- Pull/download models
- Delete models
- Query capabilities

✅ **Inference**
- Raw inference API
- Provider routing
- Model capabilities detection

✅ **System Info**
- Server diagnostics
- Resource availability
- Supported backends

---

## 📊 Test Results

Run `API_TEST_SCRIPT.sh` to verify all endpoints:

```
🏥 Health Check
✓ Server health check (HTTP 200)

📦 Models Management
✓ List all models (HTTP 200)
✓ List runnable models (limited) (HTTP 200)
✓ List models in OpenAI format (HTTP 200)

💬 Chat Completions
✓ Simple chat completion (HTTP 200)

🔌 Embeddings
✓ Create embedding (HTTP 200)

🔍 System Info
✓ Get system information (HTTP 200)

📋 Providers
✓ List available providers (HTTP 200)

Test Results: 8 passed, 1 failed out of 9 tests
```

---

## 🛠️ API Endpoints Summary

### Health & System
- `GET /health` - Server health (no auth)
- `GET /v1/system` - System information
- `GET /v1/providers` - Available providers

### Models
- `GET /v1/models` - List models
- `GET /v1/models/:id` - Model details
- `GET /v1/models/:id/capabilities` - Model capabilities
- `POST /v1/models/pull` - Download model
- `DELETE /v1/models/:id` - Delete model

### Chat & Inference
- `POST /v1/chat/completions` - Chat (OpenAI compatible)
- `POST /v1/inference` - Raw inference
- `POST /v1/embeddings` - Create embeddings

### Admin
- `GET /v1/admin/api-keys` - List API keys
- `POST /v1/admin/api-keys` - Create API key
- `DELETE /v1/admin/api-keys/:key` - Revoke API key

---

## 📝 Usage Examples

### Python

```python
import requests

headers = {
    "X-API-Key": "community",
    "Accept": "application/json",
    "Content-Type": "application/json"
}

# Chat
response = requests.post(
    "http://localhost:9131/v1/chat/completions",
    headers=headers,
    json={
        "model": "gpt-3.5-turbo",
        "messages": [{"role": "user", "content": "Hello!"}]
    }
)
print(response.json())
```

### JavaScript/Node.js

```javascript
const fetch = require('node-fetch');

const headers = {
  'X-API-Key': 'community',
  'Accept': 'application/json',
  'Content-Type': 'application/json'
};

const response = await fetch('http://localhost:9131/v1/chat/completions', {
  method: 'POST',
  headers,
  body: JSON.stringify({
    model: 'gpt-3.5-turbo',
    messages: [{ role: 'user', content: 'Hello!' }]
  })
});

const result = await response.json();
console.log(result);
```

### cURL with Streaming

```bash
curl -X POST \
  -H 'X-API-Key: community' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [{"role": "user", "content": "Write a poem"}],
    "stream": true
  }' \
  http://localhost:9131/v1/chat/completions
```

---

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| "Resource not found" (HTML) | Add `-H 'Accept: application/json'` |
| "Missing API key" (401) | Add `-H 'X-API-Key: community'` |
| "Connection refused" | Verify server running: `curl http://localhost:9131/health` |
| Slow responses | Check if models loaded: `curl -H 'X-API-Key: community' http://localhost:9131/v1/models` |

---

## 📖 Documentation Structure

```
aqli/
├── README_API.md              # This file
├── API_QUICK_REFERENCE.md     # Quick start & common tasks
├── API_EXAMPLES.md            # Detailed endpoint docs
├── API_TEST_SCRIPT.sh         # Automated test suite
└── scripts/
    └── run-dev.sh             # Start server
```

---

## 🔧 Configuration

### Server Port
Edit `ui/aqli-api/src/main/resources/application.properties`:
```properties
quarkus.http.port=9131
```

### API Keys
Stored in `~/.aqli/server/data/keys.json`  
Default key: `community`

### Logging
Logs written to: `~/.aqli/server/logs/server.log`

### Resource Limits
Set environment variables:
```bash
export JAVA_OPTS="-Xmx32g -XX:MaxDirectMemorySize=32g"
```

---

## 📦 Supported Models

Place model files in:
- GGUF: `~/.aqli/models/gguf/`
- SafeTensors: `~/.aqli/models/safetensors/`
- Others: See model repository configuration

List available models:
```bash
curl -H 'X-API-Key: community' \
     -H 'Accept: application/json' \
     http://localhost:9131/v1/models
```

---

## 🚀 Deployment Checklist

- [ ] Change default API key from `community`
- [ ] Configure TLS/HTTPS (reverse proxy)
- [ ] Set resource limits (`JAVA_OPTS`)
- [ ] Load required models
- [ ] Monitor logs at `~/.aqli/server/logs/`
- [ ] Test all endpoints with `API_TEST_SCRIPT.sh`
- [ ] Configure firewall rules
- [ ] Setup log rotation/archival

---

## 📞 Support

- **Documentation**: See files listed above
- **Logs**: `~/.aqli/server/logs/server.log`
- **Debug mode**: `./scripts/run-dev.sh serve --debug`
- **Test**: `bash API_TEST_SCRIPT.sh <url> <key> true`

---

**Last Updated**: 2026-06-27  
**API Version**: v1 (OpenAI compatible)  
**Status**: Production Ready



================================================
FILE: framework/core/aqli-core/README.md
================================================
# Aqli Core

This module provides the core abstractions and interfaces for aqli core in the Aqli Inference Engine.

## Responsibilities
- Define pure interfaces without hardware dependencies.
- Establish the data structures for aqli core operations.
- Ensure strict Separation of Concerns (SoC) from orchestrator-level logic.

## Integration
This module is part of the `framework/core` tier and is consumed by `runtime` implementations.



================================================
FILE: framework/core/aqli-core/build.gradle.kts
================================================
plugins {
    java
}

// Read Feature Flags from gradle.properties or command line
val backends: List<String> = project.findProperty("aqli.backend")?.toString()?.split(",") ?: listOf("cpu")

val enableInference: Boolean = project.findProperty("aqli.inference")?.toString()?.toBoolean() ?: true

println("⚙️ Configuring aqli-core build:")
println("   - Backends: $backends")
println("   - Inference Enabled: $enableInference")

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

dependencies {
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.smallrye.config:smallrye-config:3.10.1")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    // Optional intra-build project dependencies — only add if those projects are present
    val spiAqli = findProject(":spi:aqli-spi")
    if (spiAqli != null) {
        add("implementation", spiAqli)
    } else {
        println("   [Warning] spi:aqli-spi not present; skipping project dependency")
    }
    val spiInference = findProject(":spi:aqli-spi-inference")
    if (spiInference != null) {
        add("implementation", spiInference)
    } else {
        println("   [Warning] spi:aqli-spi-inference not present; skipping project dependency")
    }
    val spiModel = findProject(":spi:aqli-spi-model")
    if (spiModel != null) {
        add("implementation", spiModel)
    } else {
        println("   [Warning] spi:aqli-spi-model not present; skipping project dependency")
    }
    // Depend on published aljabr core aggregator for shared math/tensor/tokenizer foundations
    implementation("tech.kayys.aljabr:aljabr-core:0.1.0-SNAPSHOT")
    
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.2")
}

tasks.test {
    useJUnitPlatform()
}

// Dynamically include/exclude packages based on feature flags
sourceSets {
    main {
        java {


            // Exclude inference/runner modules if not needed
            if (!enableInference) {
                println("   [Optimizer] Excluded 'runtime' and 'diffusion' packages.")
                exclude("tech/kayys/aqli/runtime/**")
                exclude("tech/kayys/aqli/diffusion/**")
            }

            // Exclude backend implementations that are not requested
            if (!backends.contains("cuda")) {
                println("   [Optimizer] Excluded 'backend/cuda' package.")
                exclude("tech/kayys/aqli/backend/cuda/**")
            }
            if (!backends.contains("metal")) {
                println("   [Optimizer] Excluded 'backend/metal' package.")
                exclude("tech/kayys/aqli/backend/metal/**")
            }
            if (!backends.contains("cpu")) {
                println("   [Optimizer] Excluded 'backend/cpu' package.")
                exclude("tech/kayys/aqli/backend/cpu/**")
            }
        }
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/package-info.java
================================================
/**
 * Provides the core abstractions and interfaces for aqli core.
 * <p>
 * This package is part of the Aqli framework layer and contains no
 * hardware-specific implementations or agent-level orchestration logic.
 * </p>
 */
package tech.kayys.aqli.core;



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/config/AqliConfig.java
================================================
package tech.kayys.aqli.core.config;



import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class AqliConfig {
    private static AqliConfig instance;

    private final long minMemoryMB;
    private final int maxSequenceLength;
    private final int maxBatchSize;
    private final boolean enableFlashAttention;

    private AqliConfig(Builder builder) {
        this.minMemoryMB = builder.minMemoryMB;
        this.maxSequenceLength = builder.maxSequenceLength;
        this.maxBatchSize = builder.maxBatchSize;
        this.enableFlashAttention = builder.enableFlashAttention;

        validate();
    }

    public static AqliConfig getDefault() {
        if (instance == null) {
            instance = builder().build();
        }
        return instance;
    }

    private void validate() {
        long availableMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        if (availableMB < minMemoryMB) {
            throw new IllegalStateException(
                    String.format("Insufficient memory: need %d MB, have %d MB",
                            minMemoryMB, availableMB));
        }

        if (maxSequenceLength <= 0) {
            throw new InferenceException(ErrorCode.VALIDATION_INVALID_FORMAT, "maxSequenceLength must be > 0");
        }

        if (maxBatchSize <= 0) {
            throw new InferenceException(ErrorCode.VALIDATION_INVALID_FORMAT, "maxBatchSize must be > 0");
        }
    }

    public long getMinMemoryMB() {
        return minMemoryMB;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public boolean isFlashAttentionEnabled() {
        return enableFlashAttention;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long minMemoryMB = 512;
        private int maxSequenceLength = 2048;
        private int maxBatchSize = 32;
        private boolean enableFlashAttention = true;

        public Builder minMemoryMB(long mb) {
            this.minMemoryMB = mb;
            return this;
        }

        public Builder maxSequenceLength(int length) {
            this.maxSequenceLength = length;
            return this;
        }

        public Builder maxBatchSize(int size) {
            this.maxBatchSize = size;
            return this;
        }

        public Builder enableFlashAttention(boolean enable) {
            this.enableFlashAttention = enable;
            return this;
        }

        public AqliConfig build() {
            return new AqliConfig(this);
        }
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/config/HardwareConfig.java
================================================
package tech.kayys.aqli.core.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class HardwareConfig {

    @ConfigProperty(name = "hardware.cuda-enabled", defaultValue = "false")
    boolean cudaEnabled;

    @ConfigProperty(name = "hardware.rocm-enabled", defaultValue = "false")
    boolean rocmEnabled;

    @ConfigProperty(name = "hardware.tpu-enabled", defaultValue = "false")
    boolean tpuEnabled;

    @ConfigProperty(name = "hardware.apple-silicon-enabled", defaultValue = "false")
    boolean appleSiliconEnabled;

    @ConfigProperty(name = "hardware.openvino-enabled", defaultValue = "false")
    boolean openVINOEnabled;

    @ConfigProperty(name = "hardware.available-memory", defaultValue = "8589934592")
    long availableMemory;

    public boolean cudaEnabled() {
        return cudaEnabled;
    }

    public boolean rocmEnabled() {
        return rocmEnabled;
    }

    public boolean tpuEnabled() {
        return tpuEnabled;
    }

    public boolean appleSiliconEnabled() {
        return appleSiliconEnabled;
    }

    public boolean openVINOEnabled() {
        return openVINOEnabled;
    }

    public long availableMemory() {
        return availableMemory;
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/data/GData.java
================================================
package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import tech.kayys.aljabr.core.tensor.Tensor;

/**
 * Unified data contract for all Aqli runtime data types.
 */
public sealed interface GData permits GTensor, GScalar, GHandle {
    String id();
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/data/GHandle.java
================================================
package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class GHandle implements GData {
    private final String id;
    private final Object ref;

    public GHandle(String id, Object ref) {
        this.id = id;
        this.ref = ref;
    }

    public Object ref() {
        return ref;
    }

    @Override
    public String id() {
        return id;
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/data/GScalar.java
================================================
package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class GScalar implements GData {
    private final String id;
    private final float value;

    public GScalar(String id, float value) {
        this.id = id;
        this.value = value;
    }

    public float value() {
        return value;
    }

    @Override
    public String id() {
        return id;
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/data/GTensor.java
================================================
package tech.kayys.aqli.core.data;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import tech.kayys.aljabr.core.tensor.Tensor;

public final class GTensor implements GData {
    private final String id;
    private final Tensor tensor;

    public GTensor(String id, Tensor tensor) {
        this.id = id;
        this.tensor = tensor;
    }

    public Tensor tensor() {
        return tensor;
    }

    @Override
    public String id() {
        return id;
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/memory/Buffer.java
================================================
package tech.kayys.aqli.core.memory;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import java.lang.foreign.MemorySegment;

public interface Buffer {
    MemorySegment segment();

    long sizeBytes();

    void retain();

    void release();

    default void copyFrom(MemorySegment src, long bytes) {
        segment().asSlice(0, bytes).copyFrom(src.asSlice(0, bytes));
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/memory/CpuBuffer.java
================================================
package tech.kayys.aqli.core.memory;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import java.lang.foreign.*;

public final class CpuBuffer implements Buffer {
    private final Arena arena;
    private final MemorySegment segment;
    private final boolean owned;
    private int refCount = 1;

    public CpuBuffer(long sizeBytes) {
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(sizeBytes);
        this.owned = true;
    }

    public CpuBuffer(MemorySegment segment, Arena arena) {
        this.segment = segment;
        this.arena = arena;
        this.owned = false;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public long sizeBytes() {
        return segment.byteSize();
    }

    @Override
    public synchronized void retain() {
        refCount++;
    }

    @Override
    public synchronized void release() {
        refCount--;
        if (refCount == 0 && owned) {
            arena.close();
        }
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/model/LoadedModel.java
================================================
package tech.kayys.aqli.core.model;

import tech.kayys.aqli.ir.GGraph;
import tech.kayys.aqli.core.weight.WeightStore;
import tech.kayys.aljabr.spi.spec.*;
import tech.kayys.aljabr.core.tensor.DeviceType;
import tech.kayys.aljabr.core.model.ModelFormat;

public final class LoadedModel {
    public final GGraph graph;
    public final WeightStore weights;

    public LoadedModel(GGraph graph, WeightStore weights) {
        this.graph = graph;
        this.weights = weights;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/params/ParamPath.java
================================================
package tech.kayys.aqli.core.params;
import java.util.*;

/**
 * 
 * PARAM NAMING — CURRENT PROBLEM
 * - error-prone
 * - inconsistent across modules
 * - hard to refactor
 * - impossible to safely map (ONNX / GGUF / HF)
 * ============================
 * PARAM NAMING — NEW APPROACH
 * We will use structured, hierarchical paths:
 * layers.0.attn.q_proj.weight
 * layers.0.attn.k_proj.weight
 * layers.0.attn.v_proj.weight
 * layers.0.attn.o_proj.weight
 * layers.0.mlp.gate_proj.weight
 * layers.0.mlp.up_proj.weight
 * layers.0.mlp.down_proj.weight
 * layers.0.input_layernorm.weight
 * layers.0.post_attention_layernorm.weight
 * 
 * ParamPath base = ParamPath.root("layers").index(0);
 * String qWeight = base.child("attn").child("q_proj").child("weight").key();
 * Result:
 * layers.0.attn.q_proj.weight
 * Benefits
 * - deterministic
 * - hierarchical
 * - compatible with HuggingFace / PyTorch
 * - no typo bugs
 * - easy mapping
 * 
 * Example usage:
 * ParamPath p =
 * ParamPath.root("layers").index(0).child("attn").child("q_proj").child("weight");
 * System.out.println(p.key());
 * // layers.0.attn.q_proj.weight
 */

public final class ParamPath {
    private final List<String> parts;

    private ParamPath(List<String> parts) {
        this.parts = parts;
    }

    public static ParamPath root(String name) {
        return new ParamPath(List.of(name));
    }

    public ParamPath child(String name) {
        List<String> newParts = new ArrayList<>(parts);
        newParts.add(name);
        return new ParamPath(newParts);
    }

    public ParamPath index(int i) {
        return child(String.valueOf(i));
    }

    public String key() {
        return String.join(".", parts);
    }

    @Override
    public String toString() {
        return key();
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/params/ParamRegistry.java
================================================
package tech.kayys.aqli.core.params;

import tech.kayys.aljabr.core.tensor.Tensor;
import java.util.*;

/**
 * Usage (NO MORE STRING CONCAT)
 * ParamPath base = ParamPath.root("layers").index(0);
 * String qWeight = base.child("attn").child("q_proj").child("weight").key();
 * Result:
 * layers.0.attn.q_proj.weight
 * Benefits
 * deterministic
 * hierarchical
 * compatible with HuggingFace / PyTorch
 * no typo bugs
 * easy mapping
 */

public final class ParamRegistry {
    private final Map<String, Tensor> params = new HashMap<>();

    public void put(ParamPath path, Tensor t) {
        params.put(path.key(), t);
    }

    public Tensor get(ParamPath path) {
        return params.get(path.key());
    }

    public Map<String, Tensor> all() {
        return params;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/tensor/DefaultTensor.java
================================================
package tech.kayys.aljabr.core.tensor;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aqli.core.memory.Buffer;
import tech.kayys.aljabr.core.backend.ComputeBackend;

public final class DefaultTensor implements Tensor {
    private final Shape shape;
    private final DType dtype;
    private final DeviceType device;
    private final Buffer buffer;
    private final ComputeBackend backend;

    public DefaultTensor(
            Shape shape,
            DType dtype,
            DeviceType device,
            Buffer buffer,
            ComputeBackend backend) {
        this.shape = shape;
        this.dtype = dtype;
        this.device = device;
        this.buffer = buffer;
        this.backend = backend;
    }

    @Override
    public Shape shape() {
        return shape;
    }

    @Override
    public DType dtype() {
        return dtype;
    }

    @Override
    public DeviceType device() {
        return device;
    }

    @Override
    public ComputeBackend backend() {
        return backend;
    }

    public Buffer buffer() {
        return buffer;
    }

    @Override
    public Tensor add(Tensor other) {
        return backend.add(this, other);
    }

    @Override
    public Tensor sub(Tensor other) {
        // Implement via backend or simple fallback if backend doesn't have sub yet
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "sub not implemented in backend yet");
    }

    @Override
    public Tensor mul(Tensor other) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "T x T mul not implemented yet");
    }

    @Override
    public Tensor mul(float scalar) {
        return backend.mul(this, scalar);
    }

    @Override
    public Tensor div(float scalar) {
        return backend.mul(this, 1.0f / scalar);
    }

    @Override
    public Tensor matmul(Tensor other) {
        return backend.matmul(this, other);
    }

    @Override
    public Tensor reshape(long... newShape) {
        return backend.reshape(this, newShape);
    }

    private Tensor grad;
    private boolean requiresGrad;

    @Override
    public Tensor grad() {
        return grad;
    }

    @Override
    public void setGrad(Tensor grad) {
        this.grad = grad;
    }

    @Override
    public boolean requiresGrad() {
        return requiresGrad;
    }

    @Override
    public void setRequiresGrad(boolean requiresGrad) {
        this.requiresGrad = requiresGrad;
    }

    @Override
    public float item() {
        // Implement item access (read from buffer)
        return 0; // Placeholder for now
    }

    @Override
    public void backward() {
        // This will be implemented via AutogradEngine
    }

    @Override
    public Tensor softmax() {
        return backend.softmax(this);
    }

    @Override
    public Tensor slice(long[] offsets, long[] sizes) {
        return backend.slice(this, offsets, sizes);
    }

    @Override
    public Tensor pow(float exponent) {
        return backend.pow(this, exponent);
    }

    @Override
    public Tensor mean() {
        return backend.mean(this);
    }

    @Override
    public Tensor abs() {
        return backend.abs(this);
    }

    @Override
    public Tensor crossEntropy(Tensor target) {
        return backend.crossEntropy(this, target);
    }

    @Override
    public Tensor binaryCrossEntropy(Tensor target) {
        return backend.binaryCrossEntropy(this, target);
    }

    @Override
    public Tensor cast(DType dtype) {
        return backend.cast(this, dtype);
    }

    @Override
    public Tensor to(DeviceType device) {
        return backend.to(this, device);
    }

    @Override
    public Tensor div(Tensor other) {
        return backend.div(this, other);
    }

    @Override
    public Tensor add(float scalar) {
        return backend.addScalar(this, scalar);
    }

    @Override
    public Tensor zerosLike() {
        return backend.zerosLike(this);
    }

    @Override
    public Tensor sqrt() {
        return backend.sqrt(this);
    }

    @Override
    public Tensor relu() {
        return backend.relu(this);
    }

    @Override
    public Tensor sigmoid() {
        return backend.sigmoid(this);
    }

    @Override
    public Tensor tanh() {
        return backend.tanh(this);
    }

    @Override
    public Tensor log() {
        return backend.log(this);
    }

    @Override
    public Tensor exp() {
        return backend.exp(this);
    }

    @Override
    public Tensor silu() {
        return backend.silu(this);
    }

    @Override
    public Tensor flatten() {
        return backend.flatten(this);
    }

    @Override
    public Tensor unsqueeze(int dim) {
        return backend.unsqueeze(this, dim);
    }

    @Override
    public Tensor squeeze() {
        return backend.squeeze(this);
    }

    @Override
    public Tensor transpose() {
        return backend.transpose(this);
    }

    @Override
    public Tensor transpose(int dim0, int dim1) {
        return backend.transpose(this, dim0, dim1);
    }

    @Override
    public long numel() {
        return shape.numel();
    }

    @Override
    public float[] toFloatArray() {
        return buffer.segment().toArray(java.lang.foreign.ValueLayout.JAVA_FLOAT);
    }

    @Override
    public void release() {
        if (buffer != null) {
            buffer.release();
        }
    }

    @Override
    public Tensor eval() {
        return this;
    }

    @Override
    public Tensor gelu() {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor softmax(int dim) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor logSoftmax(int dim) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor mean(int dim, boolean keepDim) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor sum() {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor sum(int dim, boolean keepDim) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public java.util.List<Tensor> split(int axis, int parts) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor layerNorm(long[] normalizedShape, Tensor weight, Tensor bias, float eps) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor rmsNorm(Tensor weight, float eps) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor batchNorm(Tensor weight, Tensor bias, Tensor runningMean, Tensor runningVar, boolean training,
            float momentum, float eps) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor conv2d(Tensor weight, Tensor bias, int stride, int padding, int dilation, int groups) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor maxPool2d(int kernelSize, int stride, int padding) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor adaptiveAvgPool2d(int outputH, int outputW) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor dropout(float p, boolean training) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor attention(Tensor K, Tensor V) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor embedding(Tensor weight, long paddingIdx) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }

    @Override
    public Tensor applyRoPE(int dim, float base, boolean useNeox) {
        throw new InferenceException(ErrorCode.CONFIG_UNSUPPORTED, "Not implemented yet");
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/util/NativeLibraryManager.java
================================================
package tech.kayys.aqli.core.util;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized native library management for Aqli inference engines.
 */
public class NativeLibraryManager {

    private static final Logger log = Logger.getLogger(NativeLibraryManager.class.getName());
    private static final String DEFAULT_BASE_DIR = Path.of(System.getProperty("user.home"), ".aqli", "libs")
            .toString();

    private final String libraryName;
    private final String libraryBaseName;
    private final Path baseDir;
    private Path installedLibraryPath;

    public NativeLibraryManager(String libraryName) {
        this.libraryName = libraryName;
        this.libraryBaseName = normalizeLibraryName(libraryName);
        this.baseDir = resolveBaseDirectory();
    }

    public static Path getBaseDirectory() {
        return Path.of(resolveBaseDirectoryFromEnv());
    }

    private static String resolveBaseDirectoryFromEnv() {
        String aqliNativeDir = System.getenv("AQLI_NATIVE_LIB_DIR");
        if (aqliNativeDir != null && !aqliNativeDir.isBlank()) {
            log.fine("Using AQLI_NATIVE_LIB_DIR: " + aqliNativeDir);
            return aqliNativeDir.trim();
        }
        log.fine("Using default native lib directory: " + DEFAULT_BASE_DIR);
        return DEFAULT_BASE_DIR;
    }

    private Path resolveBaseDirectory() {
        String aqliNativeDir = System.getenv("AQLI_NATIVE_LIB_DIR");
        if (aqliNativeDir != null && !aqliNativeDir.isBlank()) {
            return Path.of(aqliNativeDir.trim());
        }
        return Path.of(DEFAULT_BASE_DIR);
    }

    public Path getLibraryDirectory() {
        Path libDir = baseDir.resolve(libraryName);
        if (!Files.exists(libDir)) {
            try {
                Files.createDirectories(libDir);
                log.fine("Created native library directory: " + libDir);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to create native library directory " + libDir, e);
            }
        }
        return libDir;
    }

    public Path getLibraryFilePath() {
        String libFileName = getLibraryFileName();
        return getLibraryDirectory().resolve(libFileName);
    }

    public String getLibraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + libraryBaseName + ".dylib";
        } else if (os.contains("win")) {
            return libraryBaseName + ".dll";
        } else {
            return "lib" + libraryBaseName + ".so";
        }
    }

    public static String getNativeExtension() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return ".dylib";
        } else if (os.contains("win")) {
            return ".dll";
        } else {
            return ".so";
        }
    }

    public Path copyToStandardLocation(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source library does not exist: " + sourcePath);
        }

        Path destDir = getLibraryDirectory();
        Path destPath = destDir.resolve(sourcePath.getFileName());

        Files.createDirectories(destDir);
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

        if (!isWindows()) {
            destPath.toFile().setExecutable(true);
        }

        log.info("Copied native library " + sourcePath.getFileName() + " to standard location: " + destPath);
        this.installedLibraryPath = destPath;
        return destPath;
    }

    public List<Path> copyLibrariesToStandardLocation(Path sourceDir, List<String> libraryNames) throws IOException {
        List<Path> copiedPaths = new ArrayList<>();
        Path destDir = getLibraryDirectory();
        Files.createDirectories(destDir);

        for (String libName : libraryNames) {
            String fileName = normalizeLibraryName(libName);
            String fullFileName = "lib" + fileName + getNativeExtension();
            Path sourcePath = sourceDir.resolve(fullFileName);

            if (Files.exists(sourcePath)) {
                Path destPath = destDir.resolve(fullFileName);
                Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

                if (!isWindows()) {
                    destPath.toFile().setExecutable(true);
                }

                copiedPaths.add(destPath);
                log.fine("Copied " + fullFileName + " to " + destPath);
            } else {
                log.fine("Library not found: " + sourcePath);
            }
        }

        if (!copiedPaths.isEmpty()) {
            log.info("Copied " + copiedPaths.size() + " native libraries to " + destDir);
        }

        return copiedPaths;
    }

    public boolean loadLibrary() {
        Path libPath = getLibraryFilePath();
        if (!Files.exists(libPath)) {
            log.warning("Native library not found at " + libPath);
            return false;
        }

        try {
            System.load(libPath.toAbsolutePath().toString());
            log.info("Loaded native library: " + libPath);
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.log(Level.SEVERE, "Failed to load native library " + libPath, e);
            return false;
        }
    }

    public static boolean loadLibrary(Path libPath) {
        if (!Files.exists(libPath)) {
            log.warning("Native library not found at " + libPath);
            return false;
        }

        try {
            System.load(libPath.toAbsolutePath().toString());
            log.info("Loaded native library: " + libPath);
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.log(Level.SEVERE, "Failed to load native library " + libPath, e);
            return false;
        }
    }

    public boolean libraryExists() {
        return Files.exists(getLibraryFilePath());
    }

    public Optional<Path> getInstalledLibraryPath() {
        return Optional.ofNullable(installedLibraryPath);
    }

    public static void clearMacQuarantine(Path libDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!(os.contains("mac") || os.contains("darwin"))) {
            return;
        }

        Path xattr = Path.of("/usr/bin/xattr");
        if (!Files.isExecutable(xattr)) {
            return;
        }

        try {
            Process process = new ProcessBuilder(
                    xattr.toString(), "-dr", "com.apple.quarantine", libDir.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit == 0) {
                log.fine("Cleared macOS quarantine flags for: " + libDir);
            } else {
                log.fine("xattr quarantine cleanup exited with code " + exit + " for " + libDir);
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Unable to clear macOS quarantine for " + libDir, e);
        }
    }

    private static String normalizeLibraryName(String name) {
        if (name.startsWith("lib")) {
            name = name.substring(3);
        }
        if (name.endsWith(".dylib") || name.endsWith(".so") || name.endsWith(".dll")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/util/ThreadPools.java
================================================
package tech.kayys.aqli.core.util;


import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPools {
    private static final ForkJoinPool COMMON_PARALLEL = ForkJoinPool.commonPool();
    private static ExecutorService sharedExecutor;
    private static ScheduledExecutorService scheduledExecutor;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        sharedExecutor = Executors.newFixedThreadPool(
                Math.max(4, cores),
                new AqliThreadFactory());
        scheduledExecutor = Executors.newScheduledThreadPool(
                Math.max(2, cores / 2),
                new AqliThreadFactory());
    }

    public static ExecutorService getSharedPool() {
        return sharedExecutor;
    }

    public static ScheduledExecutorService getScheduledPool() {
        return scheduledExecutor;
    }

    public static ForkJoinPool getParallelPool() {
        return COMMON_PARALLEL;
    }

    public static void shutdown() {
        sharedExecutor.shutdown();
        scheduledExecutor.shutdown();
    }

    private static class AqliThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("aqli-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/utils/download/ChecksumValidator.java
================================================
/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * @author Bhangun
 */

package tech.kayys.aqli.model.download;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Validates file integrity using checksums (SHA-256 by default)
 */
@ApplicationScoped
public class ChecksumValidator {

    private static final Logger LOG = Logger.getLogger(ChecksumValidator.class);
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    /**
     * Verifies that the file at the given path matches the expected checksum
     */
    public boolean verify(Path filePath, String expectedChecksum) throws IOException {
        return verify(filePath, expectedChecksum, DEFAULT_ALGORITHM);
    }

    /**
     * Verifies that the file at the given path matches the expected checksum using
     * the specified algorithm
     */
    public boolean verify(Path filePath, String expectedChecksum, String algorithm) throws IOException {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            LOG.warnf("No checksum provided for verification of %s", filePath);
            return true; // Or false, depending on policy. For production, probably false.
        }

        String actualChecksum = calculate(filePath, algorithm);
        boolean matches = expectedChecksum.equalsIgnoreCase(actualChecksum);

        if (!matches) {
            LOG.errorf("Checksum mismatch for %s. Expected: %s, Actual: %s", filePath, expectedChecksum,
                    actualChecksum);
        } else {
            LOG.debugf("Checksum verified for %s", filePath);
        }

        return matches;
    }

    /**
     * Calculates the checksum of a file
     */
    public String calculate(Path filePath, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new InferenceException(ErrorCode.CONFIG_INVALID,
                    "Checksum algorithm not found: " + algorithm, e)
                    .addContext("algorithm", algorithm);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/utils/download/DownloadManager.java
================================================
/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * @author Bhangun
 */

package tech.kayys.aqli.model.download;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.spi.download.DownloadProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Advanced DownloadManager with parallel chunk support and resumability
 */
@ApplicationScoped
public class DownloadManager {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class);
    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks
    private static final int MAX_PARALLEL_CHUNKS = 4;

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_CHUNKS * 2);

    /**
     * Download a file in parallel using a range-providing function
     */
    public CompletionStage<Path> downloadParallel(
            String uri,
            Path targetPath,
            long totalBytes,
            BiFunction<Long, Long, InputStream> rangeProvider,
            DownloadProgressListener listener) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                return doDownloadParallel(uri, targetPath, totalBytes, rangeProvider, listener);
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e);
                }
                throw new InferenceException(ErrorCode.NETWORK_BAD_RESPONSE,
                        "Parallel download failed for " + uri, e)
                        .addContext("uri", uri)
                        .addContext("targetPath", targetPath.toString())
                        .addContext("totalBytes", totalBytes);
            }
        }, executor);
    }

    private Path doDownloadParallel(
            String uri,
            Path targetPath,
            long totalBytes,
            BiFunction<Long, Long, InputStream> rangeProvider,
            DownloadProgressListener listener) throws IOException, InterruptedException, ExecutionException {

        if (listener != null)
            listener.onStart(totalBytes);

        Files.createDirectories(targetPath.getParent());
        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".downloading");

        // Prepare file size
        try (FileChannel fc = FileChannel.open(tempPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            if (totalBytes > 0) {
                fc.position(totalBytes - 1);
                fc.write(ByteBuffer.wrap(new byte[] { 0 }));
            }
        }

        int chunkCount = totalBytes > 0 ? (int) Math.ceil((double) totalBytes / DEFAULT_CHUNK_SIZE) : 1;
        AtomicLong totalDownloaded = new AtomicLong(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        LOG.infof("Starting parallel download for %s (%d chunks)", uri, chunkCount);

        for (int i = 0; i < chunkCount; i++) {
            long start = (long) i * DEFAULT_CHUNK_SIZE;
            long end = totalBytes > 0 ? Math.min(start + DEFAULT_CHUNK_SIZE - 1, totalBytes - 1) : -1;

            futures.add(CompletableFuture.runAsync(() -> {
                try (InputStream is = rangeProvider.apply(start, end);
                        FileChannel fc = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {

                    long position = start;
                    int bytesRead;
                    byte[] bytes = new byte[8192];

                    while ((bytesRead = is.read(bytes)) != -1) {
                        fc.write(ByteBuffer.wrap(bytes, 0, bytesRead), position);
                        position += bytesRead;
                        long currentTotal = totalDownloaded.addAndGet(bytesRead);

                        if (listener != null && totalBytes > 0) {
                            listener.onProgress(currentTotal, totalBytes, (double) currentTotal / totalBytes);
                        }
                    }
                } catch (IOException e) {
                    throw new InferenceException(ErrorCode.NETWORK_BAD_RESPONSE,
                            "Chunk download failed for " + uri, e)
                            .addContext("uri", uri)
                            .addContext("targetPath", tempPath.toString())
                            .addContext("start", start)
                            .addContext("end", end);
                }
            }, executor));

            if (futures.size() >= MAX_PARALLEL_CHUNKS) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                futures.clear();
            }
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        }

        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        if (listener != null)
            listener.onComplete(totalDownloaded.get());
        LOG.infof("Parallel download complete: %s", targetPath);
        return targetPath;
    }

    public void shutdown() {
        executor.shutdown();
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/utils/hw/HardwareDetector.java
================================================
/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * @author Bhangun
 */

package tech.kayys.aqli.model.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aqli.core.config.HardwareConfig;
import tech.kayys.aljabr.spi.spec.HardwareCapabilities;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Hardware capability detector
 */
@ApplicationScoped
@io.quarkus.arc.DefaultBean
public class HardwareDetector {

    private static final Logger LOG = Logger.getLogger(HardwareDetector.class);

    @Inject
    HardwareConfig hardwareConfig;

    public HardwareCapabilities detect() {
        return HardwareCapabilities.builder()
                .hasCUDA(isCUDAAvailable())
                .availableMemory(hardwareConfig.availableMemory())
                .cpuCores(Runtime.getRuntime().availableProcessors())
                .build();
    }

    public boolean cudaEnabled() {
        return isCUDAAvailable();
    }

    public boolean hasCUDA() {
        return isCUDAAvailable();
    }

    private boolean isCUDAAvailable() {
        if (!hardwareConfig.cudaEnabled()) {
            LOG.info("CUDA is disabled by configuration");
            return false;
        }

        // 1. Check for NVIDIA Management Library (NVML) which is part of the driver
        String os = System.getProperty("os.name").toLowerCase();
        String libName = os.contains("win") ? "nvml.dll" : "libnvidia-ml.so";

        // Try to find the library in common locations or system path
        // This is a heuristic but fairly reliable for driver presence
        boolean hasLib = false;
        if (os.contains("win")) {
            hasLib = Files
                    .exists(Paths.get(System.getenv("SystemRoot"), "System32", libName));
        } else {
            hasLib = Files.exists(Paths.get("/usr/lib/x86_64-linux-gnu", libName)) ||
                    Files.exists(Paths.get("/usr/lib64", libName));
        }

        if (hasLib) {
            return true;
        }

        // 2. Fallback: Try executing nvidia-smi
        try {
            Process process = new ProcessBuilder("nvidia-smi", "-L").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/core/weight/WeightStore.java
================================================
package tech.kayys.aqli.core.weight;

import tech.kayys.aljabr.core.tensor.Tensor;

/**
 * 
 * GGraph
 * ↓
 * GValue (id only)
 * ↓
 * WeightStore (external tensor storage)
 * 
 * This should avoiding:
 * ❌ huge memory duplication
 * ❌ impossible to stream / lazy load
 * ❌ breaks distributed runtime
 * ❌ kills quantization flexibility
 * :
 */
public interface WeightStore {
    Tensor get(String key);

    boolean contains(String key);
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/AdapterRegistry.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.ModelWeightLoader;
import tech.kayys.aljabr.core.tensor.WeightAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

public final class AdapterRegistry {
    private final List<ModelWeightLoader> loaders = new ArrayList<>();

    public void register(ModelWeightLoader loader) {
        loaders.add(loader);
    }

    public WeightAdapter load(Path path) {
        for (ModelWeightLoader l : loaders) {
            if (l.supports(path)) {
                return l.load(path);
            }
        }
        throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Unsupported format: " + path);
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GAttrBool.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public record GAttrBool(boolean value) implements GAttrValue {
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GAttrFloat.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public record GAttrFloat(float value) implements GAttrValue {
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GAttrInt.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public record GAttrInt(int value) implements GAttrValue {
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GAttrString.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public record GAttrString(String value) implements GAttrValue {
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GAttrValue.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

/**
 * 
 * 
 * Examples
 * public record GAttrInt(int value) implements GAttrValue {}
 * public record GAttrFloat(float value) implements GAttrValue {}
 * public record GAttrString(String value) implements GAttrValue {}
 * public record GAttrBool(boolean value) implements GAttrValue {}
 * 🔁
 * 
 * This is mandatory for:
 * serialization
 * validation
 * plugin safety
 */
public sealed interface GAttrValue
                permits GAttrInt, GAttrFloat, GAttrString, GAttrBool {
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GContext.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.*;

public final class GContext {
    private final Map<GValueId, GValue> values = new HashMap<>();

    public void define(GValue value) {
        values.put(value.id(), value);
    }

    public GValue get(GValueId id) {
        GValue v = values.get(id);
        if (v == null)
            throw new IRException("Undefined value: " + id);
        return v;
    }

    public boolean exists(GValueId id) {
        return values.containsKey(id);
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GGraph.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GGraph {
    private final List<GOp> ops;
    private final List<GValueId> inputs;
    private final List<GValueId> outputs;

    public GGraph(
            List<GOp> ops,
            List<GValueId> inputs,
            List<GValueId> outputs) {
        this.ops = ops;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public List<GOp> ops() {
        return ops;
    }

    public List<GValueId> inputs() {
        return inputs;
    }

    public List<GValueId> outputs() {
        return outputs;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GGraphBuilder.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import java.util.*;
import java.util.stream.Collectors;

public final class GGraphBuilder {
    private final List<GOp> ops = new ArrayList<>();
    private final Set<GValueId> defined = new HashSet<>();
    private final List<GValueId> inputs = new ArrayList<>();
    private final List<GValueId> outputs = new ArrayList<>();

    public GValueId input(String name, GType type, Shape shape) {
        GValueId id = new GValueId(name);
        defined.add(id);
        inputs.add(id);
        return id;
    }

    public GValueId op(
            String opType,
            List<GValueId> inputs,
            String outputName,
            Map<String, GAttrValue> attrs) {
        for (GValueId in : inputs) {
            if (!defined.contains(in))
                throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Undefined input: " + in);
        }
        GValueId out = new GValueId(outputName);
        OpDescriptor desc = new OpDescriptor(new OpId(opType));

        ops.add(new GOp(
                desc,
                outputName,
                inputs.stream().map(GValueRef::new).collect(Collectors.toList()),
                List.of(out),
                attrs));

        defined.add(out);
        return out;
    }

    public void output(GValueId id) {
        outputs.add(id);
    }

    public GGraph build() {
        return new GGraph(ops, inputs, outputs);
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GMeta.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GMeta {
    public final String sourceFormat;
    public final String modelName;
    public final Map<String, String> tags;

    public GMeta(String sourceFormat, String modelName, Map<String, String> tags) {
        this.sourceFormat = sourceFormat;
        this.modelName = modelName;
        this.tags = tags;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/AqliGraph.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.List;

/**
 * [ Model Formats ]
 * (safetensors / gguf / onnx / tflite / torch / etc.)
 * ↓
 * [ Loaders ]
 * (format-specific parsing)
 * ↓
 * [ Adapters ]
 * (normalize → unified structure)
 * ↓
 * 🔥 [ AQLI IR ] 🔥 ← THIS IS THE CORE YOU ARE MISSING
 * ↓
 * [ Optimizer / Compiler ]
 * (fusion, quantization, layout)
 * ↓
 * [ Runtime ]
 * (your current work: tensor + kernels + kv + adaptive)
 * ↓
 * [ Backend ]
 * (CPU / GPU / Metal / etc.)
 * 
 * class MatMulNode implements AqliNode {}
 * class AttentionNode implements AqliNode {}
 * class Conv2DNode implements AqliNode {}
 * class RMSNormNode implements AqliNode {}
 */
public final class AqliGraph {

    private final List<AqliNode> nodes;

    public AqliGraph(List<AqliNode> nodes) {
        this.nodes = nodes;
    }

    public List<AqliNode> nodes() {
        return nodes;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/AqliNode.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public interface AqliNode {
    String op(); // "matmul", "attention", "conv", etc.

    String name();

    List<AqliNode> inputs();

    Map<String, GAttrValue> attrs();
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GOp.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;
import java.util.*;

public final class GOp {
    private final OpDescriptor op;
    private final String name;
    private final List<GValueRef> inputs;
    private final List<GValueId> outputs;
    private final Map<String, GAttrValue> attrs;

    public GOp(String opName,
            String name,
            List<GValueRef> inputs,
            List<GValueId> outputs,
            Map<String, GAttrValue> attrs) {
        this(new OpDescriptor(new OpId(opName)), name, inputs, outputs, attrs);
    }

    public GOp(OpDescriptor op,
            String name,
            List<GValueRef> inputs,
            List<GValueId> outputs,
            Map<String, GAttrValue> attrs) {
        this.op = op;
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.attrs = attrs;
    }

    public OpDescriptor op() {
        return op;
    }

    public String name() {
        return name;
    }

    public List<GValueRef> inputs() {
        return inputs;
    }

    public List<GValueId> outputs() {
        return outputs;
    }

    public String opType() {
        return op != null ? op.id().name() : "unknown";
    }

    public Map<String, GAttrValue> attrs() {
        return attrs;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GradGraphAnalyzer.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import tech.kayys.aqli.ir.*;
import java.util.*;

public final class GradGraphAnalyzer {
    public static Map<GValueId, Integer> buildUseCount(GGraph graph) {
        Map<GValueId, Integer> useCount = new HashMap<>();
        for (GOp op : graph.ops()) {
            for (GValueRef in : op.inputs()) {
                useCount.merge(in.id(), 1, Integer::sum);
            }
        }
        return useCount;
    }

    public static Map<GValueId, GOp> buildProducerMap(GGraph graph) {
        Map<GValueId, GOp> map = new HashMap<>();
        for (GOp op : graph.ops()) {
            for (GValueId out : op.outputs()) {
                map.put(out, op);
            }
        }
        return map;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GraphValidator.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;
import java.util.stream.Collectors;

public final class GraphValidator {
    private final OpSchemaRegistry registry;

    public GraphValidator(OpSchemaRegistry registry) {
        this.registry = registry;
    }

    public GContext validate(GGraph graph) {
        GContext ctx = new GContext();
        for (GOp op : graph.ops()) {
            OpSchema schema = registry.get(op.opType());
            if (schema == null)
                throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Unknown op: " + op.opType());

            List<GValue> inputs = op.inputs().stream()
                    .map(ref -> ctx.get(ref.id()))
                    .collect(Collectors.toList());

            schema.validator().validate(inputs, op.attrs());
            Shape[] shapes = schema.shapeInfer().infer(inputs, op.attrs());
            GType[] types = schema.typeInfer().infer(inputs, op.attrs());

            for (int i = 0; i < op.outputs().size(); i++) {
                GValueId outId = op.outputs().get(i);
                GValue out = new GValue.Builder(outId)
                        .type(types[i])
                        .shape(shapes[i])
                        .build();
                ctx.define(out);
            }
        }
        return ctx;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GType.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public enum GType {
    F32,
    F16,
    BF16,
    I8,
    I4,
    BOOL
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GValue.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.Objects;

public final class GValue {
    private final GValueId id;
    private final GType type;
    private final Shape shape;
    private final String storageKey;
    private final boolean requiresGrad;
    private final boolean isConst;
    private final boolean isInput;
    private final boolean isOutput;

    private GValue(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.shape = builder.shape;
        this.storageKey = builder.storageKey;
        this.requiresGrad = builder.requiresGrad;
        this.isConst = builder.isConst;
        this.isInput = builder.isInput;
        this.isOutput = builder.isOutput;
    }

    public GValueId id() {
        return id;
    }

    public GType type() {
        return type;
    }

    public Shape shape() {
        return shape;
    }

    public String storageKey() {
        return storageKey;
    }

    public boolean requiresGrad() {
        return requiresGrad;
    }

    public boolean isConst() {
        return isConst;
    }

    public boolean isInput() {
        return isInput;
    }

    public boolean isOutput() {
        return isOutput;
    }

    public static class Builder {
        private final GValueId id;
        private GType type;
        private Shape shape;
        private String storageKey;
        private boolean requiresGrad = false;
        private boolean isConst = false;
        private boolean isInput = false;
        private boolean isOutput = false;

        public Builder(GValueId id) {
            this.id = Objects.requireNonNull(id);
        }

        public Builder type(GType type) {
            this.type = type;
            return this;
        }

        public Builder shape(Shape shape) {
            this.shape = shape;
            return this;
        }

        public Builder storageKey(String key) {
            this.storageKey = key;
            return this;
        }

        public Builder requiresGrad(boolean requiresGrad) {
            this.requiresGrad = requiresGrad;
            return this;
        }

        public Builder constVal(boolean isConst) {
            this.isConst = isConst;
            return this;
        }

        public Builder input(boolean isInput) {
            this.isInput = isInput;
            return this;
        }

        public Builder output(boolean isOutput) {
            this.isOutput = isOutput;
            return this;
        }

        public GValue build() {
            if (type == null)
                throw new InferenceException(ErrorCode.RUNTIME_INVALID_STATE, "Missing type for " + id);
            if (shape == null)
                throw new InferenceException(ErrorCode.RUNTIME_INVALID_STATE, "Missing shape for " + id);
            return new GValue(this);
        }
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GValueId.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class GValueId {

    private final String id;

    public GValueId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof GValueId other) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/GValueRef.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.Objects;

public final class GValueRef {
    private final GValueId id;

    public GValueRef(GValueId id) {
        this.id = Objects.requireNonNull(id);
    }

    public GValueId id() {
        return id;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/IRException.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public class IRException extends RuntimeException {
    public IRException(String message) {
        super(message);
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/ModelRegistry.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.ModelWeightLoader;
import tech.kayys.aljabr.core.tensor.WeightAdapter;
import java.nio.file.Path;
import java.util.*;

public final class ModelRegistry {
    private final List<ModelWeightLoader> loaders = new ArrayList<>();

    public void register(ModelWeightLoader loader) {
        loaders.add(loader);
    }

    public WeightAdapter load(Path path) {
        for (ModelWeightLoader l : loaders) {
            if (l.supports(path)) {
                return l.load(path);
            }
        }
        throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Unsupported format: " + path);
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpDescriptor.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class OpDescriptor {
    private final OpId id;

    public OpDescriptor(OpId id) {
        this.id = id;
    }

    public OpId id() {
        return id;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpId.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public final class OpId {
    private final String namespace; // e.g. "core", "nn", "custom"
    private final String name; // e.g. "softmax"
    private final int version;

    public OpId(String name) {
        this("core", name, 1);
    }

    public OpId(String namespace, String name, int version) {
        this.namespace = namespace;
        this.name = name;
        this.version = version;
    }

    public String key() {
        return namespace + ":" + name + ":" + version;
    }

    public String name() {
        return name;
    }

    public String namespace() {
        return namespace;
    }

    public int version() {
        return version;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpKernel.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.List;

public interface OpKernel<In, Out, Attrs> {
    Out compute(List<In> inputs, Attrs attrs);
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpRegistry.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import java.util.*;

public final class OpRegistry {

    private final Map<String, OpDescriptor> ops = new HashMap<>();

    public void register(OpDescriptor op) {
        ops.put(op.id().key(), op);
    }

    public OpDescriptor get(OpId id) {
        OpDescriptor op = ops.get(id.key());
        if (op == null) {
            throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Unknown op: " + id.key());
        }
        return op;
    }
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpSchema.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;
import java.util.*;

public final class OpSchema {
    private final String opType;
    private final int minInputs;
    private final int maxInputs;
    private final int outputs;
    private final ShapeInfer shapeInfer;
    private final TypeInfer typeInfer;
    private final Validator validator;

    public OpSchema(
            String opType,
            int minInputs,
            int maxInputs,
            int outputs,
            ShapeInfer shapeInfer,
            TypeInfer typeInfer,
            Validator validator) {
        this.opType = opType;
        this.minInputs = minInputs;
        this.maxInputs = maxInputs;
        this.outputs = outputs;
        this.shapeInfer = shapeInfer;
        this.typeInfer = typeInfer;
        this.validator = validator;
    }

    public String opType() {
        return opType;
    }

    public int minInputs() {
        return minInputs;
    }

    public int maxInputs() {
        return maxInputs;
    }

    public int outputs() {
        return outputs;
    }

    public ShapeInfer shapeInfer() {
        return shapeInfer;
    }

    public TypeInfer typeInfer() {
        return typeInfer;
    }

    public Validator validator() {
        return validator;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/OpSchemaRegistry.java
================================================
package tech.kayys.aqli.ir;

import java.util.*;

public final class OpSchemaRegistry {
    private final Map<String, OpSchema> schemas = new HashMap<>();

    public void register(OpSchema schema) {
        schemas.put(schema.opType(), schema);
    }

    public OpSchema get(String opType) {
        return schemas.get(opType);
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/package-info.java
================================================
/**
 * Provides the core abstractions and interfaces for aqli ir.
 * <p>
 * This package is part of the Aqli framework layer and contains no
 * hardware-specific implementations or agent-level orchestration logic.
 * </p>
 */
package tech.kayys.aqli.ir;



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/Scheduler.java
================================================
package tech.kayys.aqli.ir;


import tech.kayys.aljabr.error.ErrorCode;
import tech.kayys.aqli.spi.exception.InferenceException;
import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

import tech.kayys.aqli.ir.util.GraphAnalyzer;
import java.util.*;

public final class Scheduler {
    public static List<GOp> schedule(GGraph graph) {
        List<GOp> ops = graph.ops();
        Map<GValueId, Integer> producer = GraphAnalyzer.buildProducerMap(graph);
        int n = ops.size();
        int[] indegree = new int[n];
        // compute dependencies
        for (int i = 0; i < n; i++) {
            for (GValueRef in : ops.get(i).inputs()) {
                Integer p = producer.get(in.id());
                if (p != null) {
                    indegree[i]++;
                }
            }
        }
        Queue<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0)
                q.add(i);
        }
        List<GOp> ordered = new ArrayList<>();
        while (!q.isEmpty()) {
            int i = q.poll();
            ordered.add(ops.get(i));
            for (int j = 0; j < n; j++) {
                for (GValueRef in : ops.get(j).inputs()) {
                    if (producer.get(in.id()) != null &&
                            producer.get(in.id()) == i) {
                        indegree[j]--;
                        if (indegree[j] == 0) {
                            q.add(j);
                        }
                    }
                }
            }
        }
        if (ordered.size() != n)
            throw new InferenceException(ErrorCode.INTERNAL_ERROR, "Graph has cycle");
        return ordered;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/WeightCodec.java
================================================
package tech.kayys.aqli.ir;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aljabr.core.tensor.Tensor;

import tech.kayys.aqli.ir.schema.*;
import tech.kayys.aqli.ir.validate.*;

import java.util.*;
import java.nio.file.Path;

public interface WeightCodec {
    Tensor decode(Tensor encoded);
}



================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/schema/ShapeInfer.java
================================================
package tech.kayys.aqli.ir.schema;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aqli.ir.*;
import java.util.*;

public interface ShapeInfer {
    Shape[] infer(
            List<GValue> inputs,
            Map<String, GAttrValue> attrs);
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/schema/TypeInfer.java
================================================
package tech.kayys.aqli.ir.schema;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import java.util.*;

public interface TypeInfer {
    GType[] infer(
            List<GValue> inputs,
            Map<String, GAttrValue> attrs);
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/util/GraphAnalyzer.java
================================================
package tech.kayys.aqli.ir.util;
import tech.kayys.aqli.ir.*;


import tech.kayys.aqli.ir.*;
import java.util.*;

public final class GraphAnalyzer {
    public static Map<GValueId, Integer> buildProducerMap(GGraph graph) {
        Map<GValueId, Integer> map = new HashMap<>();
        List<GOp> ops = graph.ops();
        for (int i = 0; i < ops.size(); i++) {
            for (GValueId out : ops.get(i).outputs()) {
                map.put(out, i);
            }
        }
        return map;
    }

    public static Map<GValueId, List<Integer>> buildConsumers(GGraph graph) {
        Map<GValueId, List<Integer>> map = new HashMap<>();
        List<GOp> ops = graph.ops();
        for (int i = 0; i < ops.size(); i++) {
            for (GValueRef in : ops.get(i).inputs()) {
                map.computeIfAbsent(in.id(), k -> new ArrayList<>())
                        .add(i);
            }
        }
        return map;
    }
}


================================================
FILE: framework/core/aqli-core/src/main/java/tech/kayys/aqli/ir/validate/Validator.java
================================================
package tech.kayys.aqli.ir.validate;

import tech.kayys.aljabr.core.tensor.*;
import tech.kayys.aqli.ir.*;

import tech.kayys.aljabr.core.tensor.*;

import tech.kayys.aqli.ir.*;
import java.util.*;

public interface Validator {
    void validate(List<GValue> inputs, Map<String, GAttrValue> attrs);
}


================================================
FILE: framework/core/aqli-tokenizer-core/README.md
================================================
# Aqli Tokenizer Core

This module provides the core abstractions and interfaces for aqli tokenizer core in the Aqli Inference Engine.

## Responsibilities
- Define pure interfaces without hardware dependencies.
- Establish the data structures for aqli tokenizer core operations.
- Ensure strict Separation of Concerns (SoC) from orchestrator-level logic.

## Integration
This module is part of the `framework/core` tier and is consumed by `runtime` implementations.



================================================
FILE: framework/core/aqli-tokenizer-core/build.gradle.kts
================================================
plugins {
    java
}

dependencies {
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}



================================================
FILE: framework/core/aqli-tokenizer-core/cp.txt
================================================
/Users/bhangun/.m2/repository/tech/kayys/aqli/aqli-spi/0.1.0-SNAPSHOT/aqli-spi-0.1.0-SNAPSHOT.jar:/Users/bhangun/.m2/repository/tech/kayys/aqli/aqli-error-code/0.1.0-SNAPSHOT/aqli-error-code-0.1.0-SNAPSHOT.jar:/Users/bhangun/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar:/Users/bhangun/.m2/repository/jakarta/validation/jakarta.validation-api/3.0.2/jakarta.validation-api-3.0.2.jar:/Users/bhangun/.m2/repository/commons-codec/commons-codec/1.15/commons-codec-1.15.jar:/Users/bhangun/.m2/repository/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar:/Users/bhangun/.m2/repository/io/smallrye/reactive/mutiny/3.1.1/mutiny-3.1.1.jar:/Users/bhangun/.m2/repository/io/smallrye/common/smallrye-common-annotation/2.16.0/smallrye-common-annotation-2.16.0.jar:/Users/bhangun/.m2/repository/org/jctools/jctools-core/4.0.5/jctools-core-4.0.5.jar:/Users/bhangun/.m2/repository/jakarta/enterprise/jakarta.enterprise.cdi-api/4.0.1/jakarta.enterprise.cdi-api-4.0.1.jar:/Users/bhangun/.m2/repository/jakarta/enterprise/jakarta.enterprise.lang-model/4.0.1/jakarta.enterprise.lang-model-4.0.1.jar:/Users/bhangun/.m2/repository/jakarta/annotation/jakarta.annotation-api/3.0.0/jakarta.annotation-api-3.0.0.jar:/Users/bhangun/.m2/repository/jakarta/el/jakarta.el-api/6.0.1/jakarta.el-api-6.0.1.jar:/Users/bhangun/.m2/repository/jakarta/interceptor/jakarta.interceptor-api/2.2.0/jakarta.interceptor-api-2.2.0.jar:/Users/bhangun/.m2/repository/jakarta/inject/jakarta.inject-api/2.0.1/jakarta.inject-api-2.0.1.jar:/Users/bhangun/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.15.2/jackson-databind-2.15.2.jar:/Users/bhangun/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.21.1/jackson-core-2.21.1.jar:/Users/bhangun/.m2/repository/org/jboss/logging/jboss-logging/3.6.2.Final/jboss-logging-3.6.2.Final.jar


================================================
FILE: framework/core/aqli-tokenizer-core/pom.xml
================================================
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>tech.kayys.aqli</groupId>
        <artifactId>aqli-core-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>aqli-tokenizer-core</artifactId>
    <name>Aqli :: Tokenizer Core</name>
    <description>
        Backend-agnostic tokenizer abstractions and HuggingFace BPE tokenizer implementation.
        Shared by all inference backends (SafeTensor, GGUF, ONNX).
    </description>

    <dependencies>
        <dependency>
            <groupId>tech.kayys.aqli</groupId>
            <artifactId>aqli-spi</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>tech.kayys.aqli</groupId>
            <artifactId>aqli-spi-model</artifactId>
            <version>${project.version}</version>
        </dependency>
        <!-- CDI for injectable tokenizer beans -->
        <dependency>
            <groupId>jakarta.enterprise</groupId>
            <artifactId>jakarta.enterprise.cdi-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>jakarta.inject</groupId>
            <artifactId>jakarta.inject-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <!-- Jackson for tokenizer.json parsing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jboss.logging</groupId>
            <artifactId>jboss-logging</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-core</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>25</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <version>3.2.7</version>
                <executions>
                    <execution>
                        <id>make-index</id>
                        <goals>
                            <goal>jandex</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>



================================================
FILE: framework/core/aqli-tokenizer-core/src/main/java/tech/kayys/aqli/tokenizer/package-info.java
================================================
/**
 * Provides the core abstractions and interfaces for aqli tokenizer core.
 * <p>
 * This package is part of the Aqli framework layer and contains no
 * hardware-specific implementations or agent-level orchestration logic.
 * </p>
 */
package tech.kayys.aqli.tokenizer;



================================================
FILE: framework/core/aqli-tokenizer-core/src/main/java/tech/kayys/aqli/tokenizer/SentencePieceTokenizerPool.java
================================================
package tech.kayys.aqli.tokenizer;

import java.nio.file.Path;
import tech.kayys.aqli.tokenizer.impl.SentencePieceTokenizer;

public class SentencePieceTokenizerPool {

    private final ThreadLocal<SentencePieceTokenizer> local;

    public SentencePieceTokenizerPool(Path lib, Path model) {
        this.local = ThreadLocal.withInitial(() -> new SentencePieceTokenizer(lib, model));
    }

    public SentencePieceTokenizer current() {
        return local.get();
    }
}


================================================
FILE: framework/core/aqli-tokenizer-core/src/main/java/tech/kayys/aqli/tokenizer/TokenizerFactory.java
================================================
package tech.kayys.aqli.tokenizer;

import tech.kayys.aqli.tokenizer.loader.HuggingFaceBpeLoader;
import tech.kayys.aqli.tokenizer.spi.*;

public class TokenizerFactory {

    public static Tokenizer create(ModelConfig config) {

        return switch (config.getTokenizerType()) {

            case BPE -> new HuggingFaceBpeLoader()
                    .load(config.getTokenizerPath());

            case SENTENCE_PIECE -> {
                throw new UnsupportedOperationException("SentencePiece not yet implemented");
            }

            case WORD_PIECE -> {
                throw new UnsupportedOperationException("WordPiece not yet implemented");
            }

            case TIKTOKEN -> {
                throw new UnsupportedOperationException("Tiktoken not yet implemented");
            }
        };
    }
}


================================================
FILE: framework/core/aqli-tokenizer-core/src/main/java/tech/kayys/aqli/tokenizer/impl/BpeTokenizer.java
================================================
package tech.kayys.aqli.tokenizer.impl;

import tech.kayys.aqli.tokenizer.spi.DecodeOptions;
import tech.kayys.aqli.tokenizer.spi.EncodeOptions;
import tech.kayys.aqli.tokenizer.spi.Tokenizer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.PriorityQueue;

/**
 * Byte-Pair Encoding (BPE) Tokenizer implementation.
 * Supports GPT-2 (byte-level) and Tiktoken (raw UTF-8) styles.
 */
public final class BpeTokenizer implements Tokenizer {

    private final Map<String, Integer> tokenToId;
    private final Map<Integer, String> idToToken;
    private final Map<String, Integer> mergeRanks;
    private final Map<Character, String> byteEncoder;
    private final Map<String, Byte> byteDecoder;
    private final boolean rawUtf8Vocab;
    private final boolean sentencePieceLikeWhitespace;
    private final Map<String, Integer> specialTokens;
    private final int unkTokenId;
    private final int bosTokenId;
    private final int eosTokenId;
    private final int padTokenId;
    private final ExecutorService encoderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Standard GPT-2 pre-tokenization regex
    private static final Pattern GPT2_REGEX = Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    public BpeTokenizer(
            Map<String, Integer> tokenToId,
            Map<Integer, String> idToToken,
            Map<String, Integer> mergeRanks,
            boolean rawUtf8Vocab,
            int unkTokenId,
            int bosTokenId,
            int eosTokenId,
            int padTokenId,
            Map<String, Integer> specialTokens
    ) {
        this.tokenToId = tokenToId;
        this.idToToken = idToToken;
        this.mergeRanks = mergeRanks;
        this.rawUtf8Vocab = rawUtf8Vocab;
        this.unkTokenId = unkTokenId;
        this.bosTokenId = bosTokenId;
        this.eosTokenId = eosTokenId;
        this.padTokenId = padTokenId;
        this.specialTokens = specialTokens != null ? specialTokens : new HashMap<>();
        this.sentencePieceLikeWhitespace = detectSentencePieceWhitespace(tokenToId, rawUtf8Vocab);
        this.byteEncoder = buildByteEncoder();
        this.byteDecoder = new HashMap<>();
        for (Map.Entry<Character, String> e : byteEncoder.entrySet()) {
            this.byteDecoder.put(e.getValue(), (byte) (int) e.getKey());
        }
    }

    /**
     * Legacy constructor for existing loaders.
     */
    public BpeTokenizer(
            Map<String, Integer> tokenToId,
            Map<Integer, String> idToToken,
            Map<String, Integer> mergeRanks,
            Map<Character, String> byteEncoder,
            int bosId,
            int eosId,
            int padId,
            int unkId,
            Object preTokenizer // Ignored for now, using default GPT-2 regex
    ) {
        this.tokenToId = tokenToId;
        this.idToToken = idToToken;
        this.mergeRanks = mergeRanks;
        this.rawUtf8Vocab = false;
        this.unkTokenId = unkId;
        this.bosTokenId = bosId;
        this.eosTokenId = eosId;
        this.padTokenId = padId;
        this.specialTokens = new HashMap<>();
        this.sentencePieceLikeWhitespace = false;
        this.byteEncoder = byteEncoder;
        this.byteDecoder = new HashMap<>();
        for (Map.Entry<Character, String> e : byteEncoder.entrySet()) {
            this.byteDecoder.put(e.getValue(), (byte) (int) e.getKey());
        }
    }

    private Map<Character, String> buildByteEncoder() {
        Map<Character, String> map = new HashMap<>();
        List<Integer> bs = new ArrayList<>();
        for (int i = '!'; i <= '~'; i++) bs.add(i);
        for (int i = '¡'; i <= '¬'; i++) bs.add(i);
        for (int i = '®'; i <= 'ÿ'; i++) bs.add(i);

        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!bs.contains(b)) {
                bs.add(b);
                map.put((char) b, String.valueOf((char) (256 + n++)));
            } else {
                map.put((char) b, String.valueOf((char) b));
            }
        }
        return map;
    }

    @Override
    public long[] encode(String text, EncodeOptions options) {
        if (text == null) return new long[0];
        
        List<Long> allTokens = new ArrayList<>();
        
        // 1. Find special tokens
        if (specialTokens.isEmpty()) {
            allTokens.addAll(encodeParallel(text));
        } else {
            // Build pattern for special tokens
            StringBuilder sb = new StringBuilder();
            List<String> sortedSpecials = new ArrayList<>(specialTokens.keySet());
            sortedSpecials.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (int i = 0; i < sortedSpecials.size(); i++) {
                if (i > 0) sb.append("|");
                sb.append(Pattern.quote(sortedSpecials.get(i)));
            }
            Pattern specialPattern = Pattern.compile(sb.toString());
            Matcher m = specialPattern.matcher(text);

            int lastEnd = 0;
            while (m.find()) {
                if (m.start() > lastEnd) {
                    allTokens.addAll(encodeParallel(text.substring(lastEnd, m.start())));
                }
                allTokens.add((long) specialTokens.get(m.group()));
                lastEnd = m.end();
            }
            if (lastEnd < text.length()) {
                allTokens.addAll(encodeParallel(text.substring(lastEnd)));
            }
        }

        long[] result = new long[allTokens.size()];
        for (int i = 0; i < allTokens.size(); i++) result[i] = allTokens.get(i);
        return result;
    }

    private List<Long> encodeParallel(String text) {
        List<String> segments = new ArrayList<>();
        Matcher matcher = GPT2_REGEX.matcher(text);
        while (matcher.find()) {
            segments.add(matcher.group());
        }

        if (segments.size() <= 1) {
            return segments.isEmpty() ? List.of() : bpeEncode(segments.get(0));
        }

        try {
            List<Future<List<Long>>> futures = encoderExecutor.invokeAll(
                segments.stream().map(s -> (java.util.concurrent.Callable<List<Long>>) () -> bpeEncode(s)).toList()
            );
            List<Long> result = new ArrayList<>();
            for (Future<List<Long>> f : futures) {
                result.addAll(f.get());
            }
            return result;
        } catch (Exception e) {
            // Fallback
            List<Long> result = new ArrayList<>();
            for (String s : segments) result.addAll(bpeEncode(s));
            return result;
        }
    }

    private List<Long> bpeEncode(String word) {
        if (word.isEmpty()) return List.of();

        if (rawUtf8Vocab && sentencePieceLikeWhitespace && word.indexOf(' ') >= 0) {
            word = word.replace(' ', '\u2581');
        }

        // Initial tokens from raw bytes
        List<Symbol> symbols = new ArrayList<>();
        if (rawUtf8Vocab) {
            for (int i = 0; i < word.length();) {
                int codePoint = word.codePointAt(i);
                symbols.add(new Symbol(new String(Character.toChars(codePoint))));
                i += Character.charCount(codePoint);
            }
        } else {
            byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
            for (byte b : bytes) {
                char c = (char) (b & 0xFF);
                symbols.add(new Symbol(byteEncoder.getOrDefault(c, String.valueOf(c))));
            }
        }

        if (symbols.size() <= 1) {
            return getFinalIds(symbols);
        }

        // Link segments
        for (int i = 0; i < symbols.size(); i++) {
            if (i > 0) symbols.get(i).prev = symbols.get(i - 1);
            if (i < symbols.size() - 1) symbols.get(i).next = symbols.get(i + 1);
        }

        // Priority Queue for merges
        PriorityQueue<Merge> pq = new PriorityQueue<>();
        for (int i = 0; i < symbols.size() - 1; i++) {
            addMerge(pq, symbols.get(i), symbols.get(i + 1));
        }

        while (!pq.isEmpty()) {
            Merge top = pq.poll();
            if (top.s1.deleted || top.s2.deleted) continue;
            if (top.s1.next != top.s2) continue;

            // Perform merge
            String mergedText = top.s1.text + top.s2.text;
            top.s1.text = mergedText;
            top.s2.deleted = true;

            // Update links
            Symbol s3 = top.s2.next;
            top.s1.next = s3;
            if (s3 != null) s3.prev = top.s1;

            // Add new possible merges
            if (top.s1.prev != null) addMerge(pq, top.s1.prev, top.s1);
            if (top.s1.next != null) addMerge(pq, top.s1, top.s1.next);
        }

        return getFinalIds(symbols);
    }

    private void addMerge(PriorityQueue<Merge> pq, Symbol s1, Symbol s2) {
        String pair = s1.text + " " + s2.text;
        Integer rank;
        if (!mergeRanks.isEmpty()) {
            rank = mergeRanks.get(pair);
        } else {
            rank = tokenToId.get(s1.text + s2.text);
        }
        if (rank != null) {
            pq.add(new Merge(s1, s2, rank));
        }
    }

    private List<Long> getFinalIds(List<Symbol> symbols) {
        List<Long> ids = new ArrayList<>();
        for (Symbol s : symbols) {
            if (s.deleted) continue;
            Integer id = tokenToId.get(s.text);
            if (id != null) {
                ids.add((long) id);
            } else if (unkTokenId >= 0) {
                ids.add((long) unkTokenId);
            }
        }
        return ids;
    }

    private static class Symbol {
        String text;
        Symbol prev, next;
        boolean deleted = false;
        Symbol(String text) { this.text = text; }
    }

    private static class Merge implements Comparable<Merge> {
        final Symbol s1, s2;
        final int rank;
        Merge(Symbol s1, Symbol s2, int rank) {
            this.s1 = s1; this.s2 = s2; this.rank = rank;
        }
        @Override public int compareTo(Merge o) { ret