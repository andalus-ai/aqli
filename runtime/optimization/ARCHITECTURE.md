# Optimization module placement

This document defines ownership for Aqli's optimization-related code so that dormant,
duplicated, and misplaced modules can be identified without deleting code based only on
directory names.

## Responsibility matrix

| Capability | Canonical home | Keep in Aqli | Move to Aljabr |
|---|---|---|---|
| KV-cache allocation, block tables, request ownership and cleanup | Aqli | Yes | No |
| Prefix/prompt cache policy, admission, TTL, eviction and tenant isolation | Aqli | Yes | No |
| Continuous batching, waiting queues, cancellation and scheduling | Aqli | Yes | No |
| Prefill/decode routing and disaggregated-runtime orchestration | Aqli | Yes | No |
| Weight-offload decisions and inference-time adapter selection | Aqli | Yes | No |
| Attention implementation reusable across inference engines | Aljabr | Dispatch/configuration only | Kernel and tensor implementation |
| Paged-attention math and tensor layout operations | Aljabr | Request/block-table mapping and runtime dispatch | Reusable compute |
| Low-level device memory, transfers and backend dispatch | Aljabr | Runtime policy only | Reusable primitives |
| Quantized tensor/LoRA math | Aljabr | Adapter lifecycle and selection | Compute implementation |
| Expert-parallel coordination and scheduling | Aqli | Yes | Only reusable collectives/compute primitives |

## Lifecycle states

Every optimization module must have an explicit state:

- **Active:** included in the default build, tested, and called from a supported runtime path.
- **Optional:** intentionally excluded from the default build; has a documented enablement
  path and independent build/test coverage.
- **Experimental:** exploratory code with no production or performance claim.
- **Remove candidate:** no supported caller or owner has been identified after reference,
  build, service-loader, configuration, and downstream-consumer checks.

Do not infer state from the presence of a directory or unit test. Record the state in the
module README and keep Gradle inclusion consistent with it.

## Cleanup sequence

1. **Inventory:** list each module's public classes, dependencies, service registrations,
   configuration keys, tests, and callers in the generation path.
2. **Deduplicate:** consolidate copied plugin contracts, exceptions, config models, and
   native bindings into one owner only where their semantics are actually identical.
3. **Separate policy from compute:** runtime policy stays in Aqli; reusable tensor/kernel
   operations are proposed for Aljabr through an explicit API.
4. **Prove integration:** add a runtime-level test that exercises the feature through model
   generation; a unit test of a helper is not enough.
5. **Measure:** compare baseline and candidate with fixed model, prompt/output lengths,
   hardware, warm-up, concurrency, and memory metrics.
6. **Delete last:** remove unused modules only after a repository-wide dependency and
   consumer audit. Keep experimental modules out of the default build meanwhile.

## Acceptance criteria for enabling a module

- Gradle project is intentionally included and resolves dependencies from supported
  sources.
- No accidental dependency from Aljabr back into Aqli.
- Public API and package names do not duplicate another module's responsibility.
- Unit tests pass, including resource cleanup and failure/cancellation paths where relevant.
- At least one integration test proves that the runtime invokes the implementation.
- Native/GPU paths have a clear capability check and safe, tested fallback.
- Performance claims are backed by a reproducible benchmark rather than estimates.
