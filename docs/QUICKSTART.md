# AQLI Quickstart

AQLI is the Java inference runtime and serving product in the Aljabr + AQLI + Tafkir ecosystem. Aljabr owns reusable ML compute and model execution foundations; AQLI owns inference orchestration, model lifecycle, serving, SDKs and CLI operations.

## Requirements

- JDK 25
- Git
- macOS, Linux, or another supported Java 25 environment
- Gradle is included through the repository wrapper

## 1. Get the source

```bash
git clone https://github.com/andalus-ai/aqli.git
cd aqli
```

Inspect the actual project graph before choosing modules:

```bash
./gradlew projects
```

## 2. Build the CLI

```bash
./gradlew :cli:aqli-cli:quarkusBuild
```

The CLI module is `cli/aqli-cli` and its Gradle project path is `:cli:aqli-cli`.

Show the available commands:

```bash
java --enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector \
  -jar cli/aqli-cli/build/aqli.jar --help
```

Use the installed `aqli` executable instead when you have installed the local runtime.

## 3. Download and run a model

Download a model from a supported repository:

```bash
aqli pull hf:Qwen/Qwen2.5-7B-Instruct-GGUF
```

Run a single prompt:

```bash
aqli run \
  --model hf:unsloth/gemma-4-12b-it-GGUF \
  --no-banner \
  --prompt "Who are you?" \
  --max-tokens 50
```

Model availability, quantization, backend support and memory requirements depend on the selected model and host. Use `aqli run --help` and `aqli pull --help` for the options supported by the installed build.

## 4. Start the API server

Start the OpenAI-compatible serving API:

```bash
aqli serve --model <model-id> --port 8080
```

Check health:

```bash
curl -H 'Accept: application/json' http://localhost:8080/health
```

List available models:

```bash
curl -H 'X-API-Key: community' \
  -H 'Accept: application/json' \
  http://localhost:8080/v1/models
```

The API accepts either `X-API-Key` or a Bearer token. See the [API quick reference](API_QUICK_REFERENCE.md) and [complete API examples](API_EXAMPLES.md).

## 5. Use the Java SDK

The local SDK provider is intended to run inside the AQLI Quarkus/CDI runtime with the local SDK provider and required runner modules on the classpath. New text-generation code should use the SDK-owned text capability types rather than constructing internal inference SPI DTOs.

```java
import tech.kayys.aqli.factory.AqliSdkFactory;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;

AqliRuntime runtime = AqliSdkFactory.createLocalSdk(
    SdkConfig.builder().apiKey("community").build());

TextGenerationResponse response = runtime.generateText(
    TextGenerationRequest.of("your-model-id", "Hello from AQLI"));

System.out.println(response.content());
```

The implementation is composed from `:sdk:aqli-sdk`, `:sdk:aqli-sdk-core`, `:sdk:aqli-sdk-local`, and the required runner modules. See the [SDK module guide](../framework/sdk/README.md) and [SDK contract consolidation](architecture/SDK_CONTRACT_CONSOLIDATION.md) before choosing a public API or provider.

## 6. Where to go next

- [CLI guide](../cli/aqli-cli/README.md)
- [API quick reference](API_QUICK_REFERENCE.md)
- [API examples](API_EXAMPLES.md)
- [Observability guide](OBSERVABILITY.md)
- [System architecture](architecture/SYSTEM_ARCHITECTURE.md)
- [Module ownership matrix](architecture/MODULE_OWNERSHIP_MATRIX.md)

Use the Gradle project graph and module README files as the source of truth for current modules. Architecture documents distinguish implemented capabilities from proposed migrations.
