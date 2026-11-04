# Quantized Model Consumer Contract

**Status:** Consumer-side requirements; not a declaration of implemented format support.  
**Scope:** AQLI model loading, weight storage, and inference execution.  
**Related owners:** Aljabr (tensor representation and codecs), Tafkir (offline quantization workflows), AQLI (artifact consumption and runtime execution).

## 1. Purpose

AQLI must load quantized model artifacts only when it can establish the exact tensor representation and execute or decode that representation correctly. A dtype label such as `I8`, `Q4_K`, or `Q8_0` is not a complete serialization contract.

This document defines the consumer behavior required before a quantized format is advertised as supported. It does not introduce format identifiers or claim that a decoder exists.

## 2. Ownership boundary

| Responsibility | Owner | AQLI expectation |
|---|---|---|
| Stable tensor representation, format identifiers and versions, packing/layout semantics, reusable codecs and kernels | Aljabr | Consume the published contract; do not invent private interpretations |
| Calibration, algorithm selection, optimization, offline model conversion and artifact production | Tafkir | Produce artifacts conforming to a declared Aljabr representation |
| Artifact discovery, compatibility checks, model loading, runtime memory and execution dispatch | AQLI | Validate before admission; select only a compatible execution path |

AQLI must not depend on Tafkir at runtime. An artifact's producer identity is provenance, not a decoder selection mechanism.

## 3. Required representation identity

Before a quantized tensor can be decoded or passed to a native quantized operator, the consumer needs a versioned descriptor that unambiguously identifies at least:

- Format identifier and format version.
- Logical tensor shape and logical element type.
- Storage element type and bit width.
- Packing order, bit/nibble order, alignment and payload length.
- Quantization scheme and quantization axis.
- Group/block size and grouping semantics.
- Scale and zero-point representation, including their shapes and association with groups/channels.
- Codebook identity/content and auxiliary state when required by the format.
- Any format-specific invariants needed to interpret the payload.

The final descriptor schema and identifiers are owned by Aljabr. AQLI must not create local aliases or assume that a dtype enum uniquely determines these fields.

Producer name, quantizer algorithm, calibration settings, source-model identity and checksums are useful provenance, but must not substitute for decoding-critical representation metadata.

## 4. Consumer admission rules

AQLI must reject a quantized tensor or model before execution when any of these conditions applies:

1. The descriptor is absent where the format requires one.
2. The format identifier is unknown or its version is unsupported.
3. Required layout, packing, scale, zero-point, codebook or auxiliary metadata is missing.
4. The payload length or metadata dimensions contradict the descriptor.
5. The tensor shape, grouping or quantization axis is inconsistent.
6. The selected decoder/backend does not explicitly support the exact format and version.
7. The backend cannot satisfy the required operation, device, alignment or numerical contract.

Do not silently reinterpret unknown data as a familiar format. Do not fall back to a different nibble order, group size, scale convention or codebook. Do not treat a successful internal round trip as proof of compatibility with an external producer's format.

Errors should identify the tensor/model, format identifier and version when available, and the unsupported or invalid field. Avoid logging tensor payloads or sensitive model data.

## 5. Execution selection

For an admitted representation, AQLI should select an execution path in this order:

1. **Native quantized execution:** use a backend operator that explicitly declares support for the exact format/version and required operation.
2. **Validated decoder fallback:** decode through a validated Aljabr codec and execute using a supported higher-precision path.
3. **Reject:** if neither path is available, return a typed unsupported-format/capability error.

AQLI should not automatically expand every quantized tensor to FP32. That can erase memory and bandwidth benefits and may be prohibitively expensive. Dequantization is a deliberate fallback, not the definition of quantized inference.

The runtime must make the chosen path observable through structured diagnostics (format/version, backend, native-versus-decoded path, and fallback reason) without exposing payload contents.

## 6. Current implementation assessment

The current `QuantizedWeightStore` is a wrapper over `WeightStore`. It:

- Identifies quantized values using a small set of `DType` enum values.
- Returns the stored tensor unchanged unless `autoDequantize` is enabled.
- Throws `CONFIG_UNSUPPORTED` when automatic dequantization is requested.

This is not a format-aware loader or a working dequantization implementation. The dtype check does not validate packing, metadata, payload length, scales, zero points, codebooks or format version. Returning a tensor unchanged also does not establish that a downstream kernel can consume its representation.

The current `SafeTensorAdapter` is a map-backed adapter and does not itself establish a quantized format contract. Model-family dimensions in that adapter are placeholders and must not be confused with quantization validation.

Accordingly, AQLI must not advertise generic Q4/Q8/NF4/GPTQ/AWQ/AutoRound/TurboQuant support solely because a dtype or algorithm name is present.

## 7. Implementation sequence and gates

### Gate A — Aljabr descriptor contract

- Finalize the public, versioned tensor descriptor and validation semantics in Aljabr.
- Define stable format identifiers and exact payload layout for each supported format.
- Provide codecs/reference vectors and malformed-input tests.
- Keep algorithm/workflow settings out of the tensor representation contract.

### Gate B — AQLI admission seam

- Add a consumer-facing validation boundary once the Aljabr descriptor API is available.
- Validate descriptors and payload dimensions before model weights become executable.
- Reject unknown versions and incomplete metadata.
- Keep the default AQLI runtime independent of Tafkir.

### Gate C — execution capability negotiation

- Require each backend/provider to declare exact format/version and operation support.
- Prefer native quantized operators where supported.
- Permit decoder fallback only through a validated Aljabr codec.
- Return explicit unsupported-capability errors otherwise.

### Gate D — compatibility and numerical tests

For every enabled format/version, test:

- Valid producer-generated fixtures and independently specified reference vectors.
- Exact packing order, alignment, payload length and edge dimensions.
- Missing, malformed, contradictory and oversized metadata.
- Unsupported format identifiers and future versions.
- Scale/zero-point/codebook shape and value validation.
- Native execution parity against a trusted reference.
- Decoder fallback parity and error behavior.
- Model-load failure without partially publishing a usable model.
- Absence of mandatory Tafkir runtime dependencies.

A format is supported only after all applicable gates pass. Unit tests of a codec against itself are necessary but insufficient; include independently produced fixtures or a trusted external reference.

## 8. Explicit non-goals

This document does not:

- Define Aljabr format IDs or duplicate its descriptor schema.
- Implement GPTQ, AWQ, AutoRound, TurboQuant or BitsAndBytes algorithms in AQLI.
- Claim that Q4_K, Q8_0, NF4, INT8 or any other format is currently supported end-to-end.
- Mandate FP32 dequantization as the default inference path.
- Add Tafkir to AQLI's runtime dependency graph.

## 9. Acceptance criteria

AQLI may advertise a quantized format only when it can name the exact format identifier/version, validate its descriptor and payload, and demonstrate a supported native or validated-decoder execution path with reference-based tests. All unknown or incomplete representations must fail closed.
