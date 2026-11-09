# AQLI GGUF integration

This module contains format-level AQLI integration that is independent of any
specific GGUF execution provider.

## Responsibilities

- Generic GGUF daemon framing, request decoding, dispatch, and server-loop
  contracts.
- AQLI runner integration for GGUF-based non-text workflows where implemented.

## Execution providers

This module intentionally does **not** depend on llama.cpp and does not bundle
native llama.cpp build scripts or FFM bindings.

- Java-native GGUF execution is implemented by Aljabr's
  `aljabr-gguf-java` provider and is the target default after its model-family
  correctness and performance gates are met.
- llama.cpp is optional and is isolated in
  `aqli-runner-gguf-llamacpp`. Its native build helper and ABI shim live there.
- Select an execution provider independently from the model format. The format
  remains `gguf`; provider IDs identify the executor (for example `java` or
  `llamacpp`).

The generic runner must remain usable when llama.cpp headers, libraries,
artifacts, and optional modules are absent.
