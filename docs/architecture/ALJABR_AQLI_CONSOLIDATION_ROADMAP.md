# Aljabr–AQLI Consolidation Roadmap

**Baseline:** 2026-10-28 | **JDK baseline:** Java 25

## Vision

Make Aljabr a reusable, PyTorch-like Java ML framework for research, development, education and application building. Keep AQLI focused on inference, deployment and serving. Keep Tafkir as an optional training/adaptation integration.

**Rule:** one canonical implementation per responsibility. Do not delete duplicate code until API compatibility, behavioral parity, dependency resolution and tests prove the replacement works.

## Ownership boundary

### Aljabr — ML framework/library
- Tensor, shape, dtype, memory and storage abstractions.
- Operators, kernels, autograd where supported, graph/IR and execution APIs.
- CPU/vector, Metal, CUDA and other supported compute backends.
- Reusable model execution and model-family components.
- GGUF, Safetensors, ONNX and LiteRT model-format/runtime integrations.
- Tokenizer SPI and reusable tokenizer implementations.
- Java 25 FFM native interfaces and stable public APIs.

### AQLI — inference/serving runtime
- CLI, serving SDK/facade, HTTP/OpenAI-compatible APIs and streaming.
- Model discovery/download/cache and deployment configuration.
- Request validation/admission, batching, scheduling and routing.
- Cancellation, session/tenant policy, runtime lifecycle and graceful shutdown.
- Authentication, observability, health/admin endpoints and operational controls.
- Thin adapters between AQLI serving contracts and Aljabr APIs.

### Tafkir — optional training/adaptation
- Training orchestration, fine-tuning and adaptation.
- Must not be a mandatory dependency for ordinary AQLI inference.

Dependency direction: applications → AQLI when serving is needed → Aljabr → backends/native libraries. Direct Aljabr use must not require AQLI. Aljabr must not depend on AQLI server/runtime modules.

## Phases and exit criteria

### Phase 0 — Inventory and guardrails
- Inventory modules, public APIs, Gradle dependencies, duplicate class/package candidates and CI status in both repositories.
- Classify overlap by behavior, not just class names.
- Document allowed dependencies and exceptions.
- Add CI checks for forbidden dependency directions and duplicate generated binding packages.
- Test both local composite builds and published-artifact resolution.

**Done when:** the baseline is reproducible and ownership checks are reliable.

### Phase 1 — GGUF generated FFM bindings
- Make Aljabr's `aljabr-gguf-llamacpp` the sole owner of generated llama.cpp/ggml FFM declarations.
- Remove AQLI's generated copies and redirect references to Aljabr's public API.
- Verify ABI layouts, symbols, native library loading and platform support.

**Current work:** [AQLI PR #25](https://github.com/andalus-ai/aqli/pull/25). This phase is not complete until required CI passes and the PR is reviewed/merged.

**Done when:** no duplicate generated binding package remains in AQLI and JVM/native smoke tests pass.

### Phase 2 — GGUF execution/backend consolidation
- Audit AQLI's handwritten FFM loader, ABI layouts, model/context lifecycle, batch/sampler wrappers, token handling and state save/load.
- Replace duplicated execution with Aljabr's public backend API.
- Keep an AQLI adapter for serving lifecycle, metrics, cancellation and request/session policy.
- Add parity tests for load, prefill, greedy/sampled decode, streaming, cancellation, state persistence, unload and native-unavailable errors.
- Preserve Metal performance work; benchmark the same model, prompt, token limit, warm-up and hardware.

**Done when:** no second native execution implementation remains without a documented reason; correctness and performance checks pass.

### Phase 3 — Safetensors format/loading
- Use Aljabr's parser, validation and loader as the canonical format implementation.
- Replace AQLI's duplicate header parsing and tensor-file loading.
- Keep serving policy and model-execution logic in their correct layers; audit the Safetensors engine separately rather than assuming the loader replaces it.
- Test malformed/truncated headers, offset bounds, dtype/shape consistency, shards, mmap/copy, tensor lifetime, large files and errors.
- Preserve a pure-Java/FFM path without mandatory LibTorch.

**Done when:** one canonical format parser/loader remains and validation/resource-lifetime tests pass.

### Phase 4 — Tokenizer consolidation
- Make Aljabr the canonical owner of generic tokenizer contracts and reusable implementations.
- Inventory BPE/GPT-2, WordPiece, SentencePiece, vocabulary loading, special tokens, chat templates, pooling and native discovery.
- Port missing reusable capabilities or retain narrow temporary adapters until parity is demonstrated.
- Add golden encode/decode tests for representative model tokenizers.

**Done when:** no duplicate generic contract remains and supported model tokenizer behavior is preserved.

### Phase 5 — Tensor, memory, operators and model execution
- Compare AQLI buffers/tensor wrappers with Aljabr tensor/memory APIs.
- Consolidate dtype/shape/storage, views, device placement, ownership and lifetime semantics.
- Move reusable operators, kernels and model-family execution to Aljabr; keep request scheduling/admission in AQLI.
- Provide documented direct-use APIs for research and education.
- Test numerical behavior and memory safety; document PyTorch feature coverage honestly.

**Done when:** stable framework APIs work without AQLI and no circular dependency exists.

### Phase 6 — ONNX, LiteRT and backend capabilities
- Define consistent model-format/backend SPIs in Aljabr.
- Distinguish format parsing from graph execution and native runtime dependencies.
- Keep ONNX Runtime and LiteRT integrations optional.
- Publish a capability matrix by format, operator coverage, backend, OS/architecture and quantization.
- Add load/inference conformance tests and clear unsupported-operator errors.

**Done when:** capabilities and limitations are tested and accurately documented.

### Phase 7 — Reverse-boundary cleanup
- Audit Aljabr modules containing REST resources, HTTP routing, server configuration or RAG orchestration.
- Move serving endpoints and deployment policy to AQLI where they are not reusable framework APIs.
- Keep framework/backend contracts in Aljabr; inspect consumers and compatibility before moving public classes.
- Review Aljabr/AQLI/Tafkir dependency edges.

**Done when:** Aljabr works as a library without starting a server, and AQLI serves through Aljabr's public APIs.

### Phase 8 — AQLI reliability and observability
- Standardize cancellation, backpressure, streaming, timeouts and graceful shutdown.
- Harden model download/cache integrity, concurrency, resource limits and cleanup.
- Measure queue time, prefill latency, decode tokens/sec, memory, model load time and failures.
- Test concurrent requests, multi-model isolation, reload, failure recovery and resource exhaustion.

**Done when:** runtime behavior and production limits are documented and tested.

### Phase 9 — SDK, packaging and releases
- Publish versioned Aljabr modules and AQLI SDK/runtime artifacts with compatibility rules.
- Ensure local installation scripts and composite builds match published module boundaries.
- Document Java 25, native prerequisites, supported platforms and optional dependencies.
- Use README badges only for real workflows/releases and keep links canonical.
- Maintain migration notes and a clean-checkout quick start using the HF GGUF model path.

**Done when:** clean builds and published dependencies work independently of local repository layout.

### Phase 10 — Performance and developer experience
- Maintain reproducible Metal, CPU/vector and CUDA benchmarks where available.
- Profile GPU logits argmax, GPU token embedding, prefill batching and CPU fallbacks.
- Measure end-to-end time-to-first-token and tokens/sec, not just kernel latency.
- Provide examples for tensors, model loading, inference, custom operators/backends and education/research.
- Record correctness, model, hardware, settings and known limitations for each benchmark.

**Done when:** benchmarks are reproducible and optimizations preserve output correctness.

## Cross-cutting gates for every PR

1. Correct ownership and one canonical implementation.
2. Public API and compatibility review.
3. Focused unit, integration, golden-output and negative tests.
4. FFM ABI, symbol, memory-scope and cleanup validation where applicable.
5. Java 25 Gradle tasks pass in composite and published-artifact modes.
6. Relevant performance baselines are recorded for hot paths.
7. Documentation matches actual supported capabilities.
8. Required CI passes before merge; failures are fixed, not bypassed.
9. Changes are bounded and independently revertible.

## Execution order

| Priority | Work | Prerequisite |
|---|---|---|
| P0 | Finish PR #25 and verify CI | Current |
| P0 | Consolidate GGUF execution/backend API | Phase 1 |
| P1 | Safetensors loading boundary | API and fixture parity |
| P1 | Tokenizer SPI and implementations | Golden tests |
| P1 | Reverse-boundary REST/RAG audit | Consumer inventory |
| P2 | Tensor/memory/operator consolidation | Lifetime/numerical tests |
| P2 | ONNX/LiteRT capability matrix | Stable backend SPI |
| P2 | Serving reliability and observability | Lifecycle tests |
| P3 | Packaging and SDK hardening | Stable module boundaries |
| P3 | Research/education examples and performance work | Stable framework APIs |

## Definition of done

- Aljabr is independently usable as a Java ML framework and owns reusable compute, model-format, tokenizer and FFM implementations.
- AQLI focuses on serving, deployment and operations rather than duplicating framework code.
- GGUF, Safetensors, ONNX and LiteRT capabilities are honestly documented and tested.
- Java 25 is the documented default baseline.
- The intended pure-Java/FFM inference path does not accidentally require LibTorch or Tafkir.
- Both repositories have passing required CI, reproducible builds and release compatibility.
- End-to-end correctness and performance are measured and preserved through each migration.
