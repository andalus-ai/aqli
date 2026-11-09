# AQLI GGUF — llama.cpp provider

This module is the opt-in AQLI integration for Aljabr's llama.cpp GGUF backend.
It owns the llama.cpp-specific AQLI engine adapter, FFM bindings, session
management, native-image metadata, native shim, and native build helper.

The module depends on Aljabr's `aljabr-gguf-llamacpp` provider. Do not add this
module as a dependency of the format-level `aqli-runner-gguf`.

## Build the optional native provider

Set `AQLI_LLAMA_SOURCE_DIR` to a llama.cpp source checkout (default:
`~/.aqli/source/vendor/llama.cpp`) and run:

```bash
./scripts/build-llama-cpp.sh
```

This helper builds the native libraries and the AQLI ABI shim into this
module's build output. Native hardware/runtime validation is still required
for each target platform.

## Provider identity

The execution provider ID is `llamacpp`; `gguf` remains the model format.
