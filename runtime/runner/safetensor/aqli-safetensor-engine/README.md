# Aqli SafeTensors Engine

The `aqli-safetensor-engine` module contains Aqli's SafeTensors-oriented inference execution path.

It is an Aqli runtime component, not a general-purpose tensor or model-loading framework. It delegates SafeTensors file access and tensor integration to Aljabr, while Aqli owns inference execution and runtime lifecycle.

## Responsibilities

The current `DirectInferenceEngine` implementation is an application-scoped Aljabr `SafetensorEngine` integration. Its dependencies show the following execution collaborators:

- Aljabr `SafetensorLoaderFacade` for loading SafeTensors data.
- Aljabr `AccelWeightBridge` for bridging loaded weights into `AccelTensor`.
- Aqli `DirectForwardPass` and `TokenSampler` for inference execution.
- Aqli `KVCacheManager` for generation cache management.
- Aqli `ModelWarmupService` for model warmup.
- Aljabr model SPI and model-family plugin APIs for model metadata and architecture integration.

The module also declares the Aljabr Metal backend as a dependency. Hardware-specific behavior must be validated separately from ordinary JVM tests.

## Architecture boundary

- **Aljabr:** SafeTensors APIs, loading, tensor representation, compute backends, and model-level primitives.
- **Aqli:** inference execution, generation, sampling, KV-cache lifecycle, warmup, and runtime integration.
- **Tafkir:** optional training, fine-tuning, calibration, and adaptation; it is not a mandatory dependency of this engine's default dependency declarations.

The engine must not provide a second application-wide `AqliSdk` implementation. SDK composition belongs to the canonical Aqli runtime. Format-specific code should integrate through the current inference and model SPIs.

## Build and test

From the Aqli repository root:

```bash
./gradlew :runner:safetensor:aqli-safetensor-engine:compileJava
./gradlew :runner:safetensor:aqli-safetensor-engine:test
```

The test source set currently excludes legacy tests that reference removed APIs or have stale policy expectations. Those exclusions are tracked as migration work; they must not be interpreted as passing coverage. Restore tests incrementally after migrating them to the current inference SPI and verifying their assertions against current runtime behavior.

## Current migration notes

- The legacy `safetensor/inference` source package remains excluded because it depends on removed provider APIs.
- The former `SafetensorAqliSdk` no-op implementation has been removed; it must not be documented or reintroduced as the engine entry point.
- The empty `InferenceRequestPlanner.plan(InferenceRequest)` stub and the excluded planning tests require a separate usage and migration audit before deciding whether to implement or remove them.
- Audio and quantization modules are not declared as direct dependencies of this engine. Optional quantization integration is kept separate.

This README describes the current Gradle/source configuration and should be updated alongside future integration changes.
