# Multimodal embedding integration contract

**Status: SPI exists; concrete EmbeddingGemma 2 provider and served image-embedding API are not yet established.**

This document defines the integration boundary between Aljabr's model execution, AQLI's inference runtime and consumers such as Pinisi.

## Responsibility boundaries

- **Aljabr** owns model-family implementations, tensor/weight operations, tokenizer and processor primitives, backend execution, and the registered model runner.
- **AQLI** owns model loading orchestration, inference request lifecycle, scheduling, cancellation, batching policy, resource lifecycle, service APIs and deployment.
- **Pinisi** owns source ingestion, media segmentation, embedding-space-aware indexing, retrieval and search APIs. It must not become a second inference runtime.

AQLI must call Aljabr through public runner/model APIs. It must not duplicate model-specific tensor math. Aljabr must not depend on AQLI.

## Current contract and gap

The multimodal SPI currently exposes `EmbeddingService.embed(MultimodalContent, model)` and `embedBatch(...)`. `UnifiedMultimodalRuntime` is a ServiceLoader extension point. These are useful contracts, but an SPI declaration is not a provider implementation.

The compatibility REST `/v1/embeddings` request currently accepts text input. The text endpoint and vision-enabled chat endpoint do not establish a multimodal embedding endpoint. In particular, image understanding/generation responses are not substitutes for vectors from a shared retrieval space.

Do not advertise image embeddings as supported until AQLI can select a registered Aljabr runner whose manifest declares the validated input modality and compatible query/document modality pairs.

## Required runtime behavior

An implementation of `EmbeddingService` should:

1. Resolve a model ID to a loaded, registered embedding runner.
2. Validate each `MultimodalContent` part against the runner's declared input modalities before scheduling work.
3. Preserve batch item order and return one result per input, with stable item-level error semantics.
4. Propagate cancellation and deadlines to the underlying runner.
5. Bound image/audio/video decoding and preprocessing memory; do not buffer unbounded request bodies.
6. Enforce request-size, media-type, decode-time and resource limits before expensive inference.
7. Close streams and release temporary buffers on success, failure and cancellation.
8. Return model identity, immutable revision, vector dimension, normalization and an embedding-space identifier with each result or batch metadata.
9. Reject comparisons and indexing across incompatible embedding spaces; equal vector dimensions alone do not imply compatibility.
10. Expose readiness based on actual runner/backend initialization, not merely provider discovery.

## Embedding-space metadata

The service contract should make these values available to callers:

- Model ID and immutable revision/digest.
- Embedding-space ID including model/projection revision and task semantics.
- Supported input modalities.
- Supported query/document modality pairings (e.g. text-to-text and text-to-image).
- Output dimension and numeric type.
- Pooling, truncation and normalization behavior.
- Processor/tokenizer revision and relevant preprocessing configuration.

For asymmetric retrieval models, the query/document role and task prefix must be explicit. Do not infer compatibility from equal dimensions or a shared model family name.

## Suggested rollout

### Phase 1 — local service implementation
- Select one Aljabr runner that is actually registered and executable.
- Implement the AQLI `EmbeddingService` provider as a thin lifecycle/scheduling adapter.
- Add tests for unsupported modality rejection, batch ordering, cancellation, resource cleanup and readiness.

### Phase 2 — EmbeddingGemma 2
- Wait for Aljabr's text and image runner to be registered and validated against a real checkpoint.
- Add AQLI provider support only after Aljabr exposes the supported modality matrix.
- Add paired text/image fixtures and verify text-to-image nearest-neighbor behavior.
- Expose an explicit modality-aware API without breaking the existing text-only OpenAI-compatible endpoint.

### Phase 3 — external consumers
- Publish SDK request/response types and a versioned API contract.
- Provide a Pinisi adapter that consumes AQLI's public API and preserves embedding-space metadata.
- Keep Andalus optional; it may call Pinisi as a tool or MCP server but must not own retrieval.

## Acceptance criteria

- [ ] Concrete `EmbeddingService` provider is registered and discoverable.
- [ ] Provider resolves only registered, ready Aljabr runners.
- [ ] Text and image inputs are accepted only when the selected runner declares them.
- [ ] Text-to-image compatibility is proven with one checkpoint and paired fixture set.
- [ ] Response contains stable embedding-space metadata.
- [ ] Batch order, errors, cancellation, limits and cleanup are tested.
- [ ] API and SDK docs distinguish embeddings from vision chat.
- [ ] Existing text-only `/v1/embeddings` behavior remains compatible.
- [ ] Integration tests cover the published Aljabr artifact, not only a Gradle project dependency.

## Upstream dependency

Aljabr's EmbeddingGemma 2 module currently contains useful float32 text and vision reference components, but its model-family descriptor still declares text-only support and no registered `EmbeddingModelRunner`. AQLI should not implement a fake or parallel model path to work around that gap.

See [Aljabr's EmbeddingGemma 2 runner readiness note](https://github.com/andalus-ai/aljabr/blob/main/docs/architecture/EMBEDDING_GEMMA2_RUNNER_READINESS.md).
