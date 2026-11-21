# AQLI Java SDK

The SDK provides Java application-facing contracts and adapters for using AQLI inference capabilities. Keep public client contracts independent of local runner implementations and transport frameworks.

## Module ownership

| Gradle project | Responsibility |
|---|---|
| `:sdk:aqli-sdk-api` | Public transport-neutral `AqliClient` contract, request/result types and provider boundary |
| `:sdk:aqli-sdk` | SDK provider implementation and client adapters |
| `:sdk:aqli-sdk-core` | Runtime capability ports, text-generation mapping and compatibility aggregate |
| `:sdk:aqli-sdk-local` | Local runtime provider composed inside the AQLI CDI runtime |
| `:sdk:aqli-sdk-remote` | Optional/legacy remote SDK module; not part of the default settings graph |

Use `./gradlew projects` to confirm which optional projects are included in a particular checkout.

## Local runtime usage

The current local runtime factory returns `AqliRuntime`. New text-generation calls should use the SDK-owned `TextGenerationRequest` and `TextGenerationResponse` contracts:

```java
import tech.kayys.aqli.factory.AqliSdkFactory;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;

AqliRuntime runtime = AqliSdkFactory.createLocalSdk(
    SdkConfig.builder().apiKey("community").build());

var response = runtime.generateText(
    TextGenerationRequest.of("your-model-id", "Hello from AQLI"));

System.out.println(response.content());
```

This path requires the local provider and relevant runner modules on the classpath and must be created inside the running AQLI Quarkus/CDI environment.

## Public client API and compatibility

The canonical public client contract is `tech.kayys.aqli.sdk.api.AqliClient` in `:sdk:aqli-sdk-api`. The older root-package `tech.kayys.aqli.sdk.AqliClient` and broad `AqliSdk` aggregates are compatibility surfaces under staged consolidation.

The broad `AqliSdk` API still exposes some AQLI SPI DTOs. Prefer SDK-owned typed capability contracts for new application code. Do not add another parallel client interface or duplicate request/response model.

See [SDK Contract Consolidation](../../docs/architecture/SDK_CONTRACT_CONSOLIDATION.md) for canonical naming, migration stages and compatibility gates.
