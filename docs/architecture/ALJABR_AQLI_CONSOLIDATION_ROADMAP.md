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

### Phase 2B — Generic execution-engine consolidation

- Audit both AQLI runtime-core `ExecutionEngine` classes and all call sites before changing code.
- Keep Aljabr as the canonical owner of reusable tensor/operator/kernel execution contracts; keep AQLI responsible for serving-session controls, cancellation, admission/scheduling and observability.
- Resolve the legacy `tech.kayys.alkhawarizm.core.tensor.Tensor` import in the older execution path by proving whether the class is still used, then migrate or remove it only with compile and behavior evidence.
- Establish an Aljabr public execution SPI if the existing public APIs do not cover the required execution semantics. Do not make Aljabr depend on AQLI IR or runtime classes.
- Add contract tests for missing inputs/kernels, output cardinality, device routing, cancellation/interruption, tensor ownership and cleanup.
- Add a dependency/architecture guard to prevent Aljabr → AQLI dependencies and flag new duplicate AQLI tensor/compute implementations.

**Done when:** AQLI has one clearly justified serving-plan integration path, generic computation delegates to Aljabr, no stale engine class remains referenced, and required CI plus numerical/lifecycle tests pass. A class-name overlap alone is not sufficient evidence to delete code.

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


## Architecture reference set

Use these documents together; each answers a different question:

- [System architecture and boundaries](SYSTEM_ARCHITECTURE.md) — product roles, dependency direction, model-format boundaries, resource ownership and compatibility.
- [Sequence flows](SEQUENCE_FLOWS.md) — request handling, model loading, generation, streaming/cancellation, shutdown, failure and cross-repository validation.
- [Module ownership matrix](MODULE_OWNERSHIP_MATRIX.md) — canonical owner by capability, known audit candidates, allowed dependency rules and per-migration checklist.
- [Ownership audit](ALJABR_AQLI_OWNERSHIP_AUDIT.md) — initial tree inventory and reverse-boundary leaks.

The diagrams are target-state contracts. If code currently behaves differently, record the gap and its status; do not present target behavior as implemented behavior.

## Recommended implementation sequence and gates

This sequence is intentionally dependency-aware. A later phase should not hide an unresolved failure from an earlier phase.

1. **Stabilize the baseline.** Capture the current main commit, Gradle project graph, JDK/toolchain, workflow results and module compile/test commands for both repositories. Confirm clean-checkout builds and document known failures.
2. **Make Aljabr public dependencies consumable.** Verify coordinates, publication, module metadata and Java 25 compatibility from a clean AQLI consumer. Test both composite and published-artifact resolution.
3. **Finish generated GGUF FFM ownership.** Confirm AQLI has no duplicate generated package, adapt remaining call sites to Aljabr public API, and fix compile/native-smoke CI before proceeding.
4. **Consolidate GGUF execution.** Inventory loader/context/sampler/batch/token/state paths; define the stable Aljabr API; migrate one path at a time; compare output, state lifecycle, cancellation and performance.
5. **Consolidate Safetensors format loading.** Use Aljabr parser/loader/bridge APIs; preserve AQLI execution until parity is demonstrated; keep optional quantization behind an AQLI-owned port and prove the default NONE path does not require its provider.
6. **Consolidate tokenizer and prompt contracts.** Establish Aljabr tokenizer APIs and golden fixtures; assign template formatting and special-token ownership explicitly; remove duplicate algorithms only after behavior parity.
7. **Consolidate tensor, IR, model execution and KV state.** Separate reusable storage/layout/ops from AQLI cache policy and generation scheduling. Validate ownership, lifetime and concurrency.
8. **Remove reverse-boundary leaks.** Audit Aljabr HTTP resources, server configuration and RAG orchestration. Move serving concerns only after consumer inventory and compatibility planning.
9. **Enforce architecture in CI.** Add checks for forbidden dependency direction, generated binding duplication, mandatory Tafkir leakage, accidental Aljabr-to-AQLI dependencies and optional modules on default classpaths.
10. **Stabilize runtime operations.** Test deadlines, cancellation, backpressure, concurrent requests, model reload, multi-model isolation, failure recovery and graceful shutdown.
11. **Publish versioned APIs and examples.** Validate public API docs, package coordinates, install scripts, clean-checkout quick starts and migration notes.
12. **Re-baseline performance.** Measure load time, time-to-first-token, prefill throughput, decode tokens/sec, peak memory, queue latency and end-to-end latency on the same models/hardware/configuration.

### Required gate before each merge

- [ ] Ownership and dependency direction are correct.
- [ ] No unreviewed public API break or package relocation.
- [ ] Focused tests include a positive case and relevant negative/lifecycle case.
- [ ] Required repository CI passes; failed jobs are understood and fixed.
- [ ] Published-artifact dependency resolution is checked when a public module boundary changes.
- [ ] Performance-sensitive paths have comparable before/after evidence.
- [ ] Docs describe what exists today and distinguish it from planned capability.
- [ ] The change is bounded and has a clear rollback path.

### Current evidence policy

The roadmap and ownership audit are planning artifacts, not build evidence. Track CI and PR status from the current run/commit rather than carrying forward an old green or red result. In particular, a generated-binding migration is not verified until both AQLI compile paths and the relevant native checks pass; a Safetensors API migration is not verified until the engine compiles and its tests pass.
