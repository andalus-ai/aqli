# AQLI Safetensors Runner

This directory contains the Gradle-based Safetensors inference runner. The maintained implementation is `aqli-safetensor-engine`; the directory also contains optional or experimental integrations that are not all part of the default build.

## Ownership

- **Aljabr** owns the reusable Safetensors API, tensor types, loader, weight bridge, and compute/backend foundations.
- **AQLI** owns direct inference execution, generation, model lifecycle, request-facing integration, and runner wiring.
- **Quantization** is an optional integration. The default Safetensors engine depends on AQLI's `InferenceQuantizer` port and must not require Tafkir-backed quantization classes for ordinary unquantized inference.
- **Tafkir** remains a training/adaptation project; it is not a mandatory dependency of the standard inference path.

## Gradle modules

| Module | Purpose | Default status |
| --- | --- | --- |
| `runner:safetensor:aqli-safetensor-engine` | Direct Safetensors model loading and inference | Included when the module directory exists |
| `runner:safetensor:aqli-safetensor-quantization` | Optional adapter integration for quantization | Opt-in with `-PwithQuantization=true`; requires the compatible Aljabr quantization integration |
| `runner:safetensor:aqli-runner-flux` | Flux-related runner integration | Optional |

The project settings use optional module inclusion. A directory or Gradle project name is not, by itself, evidence that its complete runtime path is tested or production-ready.

## Requirements

- JDK 25 for the documented baseline.
- The repository's Gradle Wrapper.
- Compatible Aljabr artifacts or a sibling Aljabr checkout included by the build.
- Platform-specific native/backend prerequisites only for the selected execution path.

## Build and test

From the AQLI repository root:

```bash
./gradlew projects
./gradlew :runner:safetensor:aqli-safetensor-engine:compileJava
./gradlew :runner:safetensor:aqli-safetensor-engine:test
```

Run the commands against the current checkout and inspect the task graph first if the optional module is not included. These commands are suggested validation steps, not a claim that they have been run successfully for every platform or model.

The quantization integration is deliberately separate from the default engine. Enable it only in a development environment that also has the compatible Aljabr/Tafkir integration configured; do not add it as a mandatory dependency just to make unquantized inference work.

## Public boundary

The engine should consume Aljabr's public Safetensors and tensor APIs, including `aljabr-safetensor-bridge`, rather than duplicate the file-format parser or tensor bridge. The default engine owns the `InferenceQuantizer` port; optional integrations can implement that port without leaking their dependencies into the core inference classpath.

## Current limitations

- Supported model architectures, tensor dtypes, operators, and hardware backends vary by runner and release.
- The presence of a format module does not guarantee that every checkpoint is executable.
- Optional quantization support is unavailable unless its adapter and required backend dependencies are installed and wired.
- The legacy Maven layout and module examples in older documentation are not authoritative for this Gradle repository.

See the [repository README](../../../README.md) and [architecture documentation](../../../docs/architecture/) for repository-wide guidance.
