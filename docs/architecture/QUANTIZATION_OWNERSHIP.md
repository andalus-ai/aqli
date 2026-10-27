# Quantization ownership and integration boundary

## Product responsibilities

| Concern | Owner | Boundary |
|---|---|---|
| Tensor math, quantized tensor representations, kernels, device memory, backend dispatch | Aljabr | Reusable compute primitives; no dependency on Aqli or Tafkir |
| Inference execution, model loading, request scheduling, generation, KV-cache allocation and reclamation | Aqli | Consumes supported model formats and compute primitives; default runtime must not require training dependencies |
| Training, fine-tuning, QAT, calibration, training-aware adaptation | Tafkir | Produces or adapts model artifacts; does not become a runtime dependency of Aqli |
| Offline weight quantization | Optional quantization/conversion workflow | May use Tafkir algorithms and Aljabr primitives; should be invoked explicitly and independently from serving |
| KV-cache quantization during inference | Aqli policy + Aljabr primitives | Aqli controls cache lifecycle and policy; low-level math/storage primitives should be reusable where appropriate |

## Build contract

- Aqli's default Gradle build and runtime classpath must not require Tafkir.
- The optional `:runner:safetensor:aqli-safetensor-quantization` integration may depend on Aljabr's quantization artifact, but must remain opt-in.
- Composite-build substitution must not force an optional Tafkir-backed Aljabr module into every Aqli build.
- A model that was quantized by Tafkir must be loadable by Aqli only when its format and operators are supported; the producer framework is not a runtime dependency.

## CLI quantization status

The current `ui/aqli-cli` `quantize` implementation copies weight files and writes metadata; this does not transform the weights. Until the command invokes a real quantization engine and verifies its output, it must be considered a placeholder, not a supported quantization workflow.

Before enabling a real command implementation:

1. Define a training-independent SPI for offline model conversion, or invoke a dedicated optional converter.
2. Resolve strategy names against actual registered implementations; reject unavailable strategies.
3. Write to a temporary output and publish atomically only after conversion succeeds.
4. Validate output tensors, dtype/quantization metadata, and tokenizer/config preservation.
5. Record source and output checksums, engine/version, strategy, bit width, and conversion status in the audit trail.
6. Add tests proving output weights changed and that unsupported strategies fail without leaving a seemingly valid output directory.

## Acceptance criteria

- Aqli default runtime resolves and builds without Tafkir-enabled Aljabr artifacts.
- Optional quantization is discoverable and explicit; absence produces a clear unsupported-capability error.
- No CLI path reports success merely because files were copied or metadata was written.
- Quantized model loading and inference are tested independently from offline conversion.
