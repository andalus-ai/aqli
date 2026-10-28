# Aljabr–AQLI Sequence Flows

**Baseline:** 2026-10-28  
**Purpose:** Specify intended runtime interactions and failure/cleanup behavior. These diagrams describe the target flow; concrete class names may vary while consolidation is in progress.

## 1. Request from API to response

```mermaid
sequenceDiagram
  autonumber
  actor Client
  participant Transport as AQLI Transport (CLI/SDK/HTTP/gRPC)
  participant Admission as AQLI Admission/Scheduler
  participant Registry as AQLI Model Registry
  participant Runner as AQLI Runner Adapter
  participant Engine as Aljabr Execution API
  participant Backend as Aljabr Backend
  participant Metrics as AQLI Observability

  Client->>Transport: submit prompt + generation options
  Transport->>Transport: authenticate, validate, normalize
  Transport->>Admission: InferenceRequest + deadline + cancellation
  Admission->>Admission: enforce quotas, queue limits, scheduling
  Admission->>Registry: resolve model/provider
  Registry-->>Admission: loaded model handle or load plan
  Admission->>Runner: execute request
  Runner->>Engine: execute/prefill input
  Engine->>Backend: dispatch tensor operations
  Backend-->>Engine: logits / execution result
  Engine-->>Runner: result + execution metadata
  Runner->>Runner: AQLI sampling, stop conditions, output policy
  loop while generation continues
    Runner->>Engine: execute next-token step
    Engine->>Backend: dispatch compute
    Backend-->>Engine: next logits
    Engine-->>Runner: logits
    Runner-->>Transport: token/chunk (when streaming)
  end
  Runner-->>Admission: final response + usage
  Admission->>Metrics: latency, token counts, outcome
  Admission-->>Transport: response
  Transport-->>Client: final result / stream completion
```

**Boundary:** Aljabr executes reusable model/tensor work. AQLI decides whether and when to accept a request, how to sample, when to stop, how to stream and how to account for it. If Aljabr exposes a complete decode API for a supported model, AQLI still owns serving policy around that API.

## 2. Model discovery and loading

```mermaid
sequenceDiagram
  autonumber
  participant API as AQLI API/CLI
  participant Registry as AQLI Model Registry
  participant Download as AQLI Repository/Download
  participant Store as AQLI Model Cache
  participant Format as Aljabr Format Loader
  participant Model as Aljabr Model/Weight API
  participant Provider as Aljabr Backend Provider

  API->>Registry: resolve model ID + requested options
  Registry->>Store: inspect cache + metadata
  alt model files absent
    Registry->>Download: fetch manifest and artifacts
    Download->>Download: verify revision, size, digest and safe paths
    Download-->>Store: verified artifact files
  else cached files present
    Store-->>Registry: cached artifact metadata
  end
  Registry->>Format: parse and validate model format
  Format->>Format: check headers, offsets, dtype, shapes, shards
  Format-->>Model: validated metadata and tensor/weight access
  Model->>Provider: select supported device/backend
  Provider-->>Model: provider capability + resource context
  Model-->>Registry: loaded model handle + capability report
  Registry-->>API: model ready or actionable load error
```

**Requirements:** downloading and format parsing are distinct stages. Never trust an unverified cache entry. On any partial failure, close mapped files and native/device allocations. Record model revision, format, provider, quantization and relevant runtime versions so a result is reproducible.

## 3. Token-by-token generation

```mermaid
sequenceDiagram
  autonumber
  participant Runner as AQLI Generation Loop
  participant Tokenizer as Aljabr Tokenizer API
  participant Model as Aljabr Model Execution
  participant Cache as KV/State Resources
  participant Backend as Aljabr Compute Backend
  participant Sampler as AQLI Sampling/Stopping

  Runner->>Tokenizer: encode normalized prompt
  Tokenizer-->>Runner: token IDs
  Runner->>Cache: acquire request/session cache context
  Runner->>Model: prefill token IDs + execution context
  Model->>Backend: embedding, attention, FFN and logits compute
  Backend-->>Model: tensor outputs/logits
  Model-->>Runner: initial logits + updated state
  loop until stop / deadline / cancellation / token limit
    Runner->>Sampler: sample from logits + request policy
    Sampler-->>Runner: next token ID or stop decision
    opt token accepted
      Runner->>Tokenizer: decode incremental token
      Tokenizer-->>Runner: text fragment
      Runner-->>Runner: emit fragment and update usage
      Runner->>Model: decode one token + cache state
      Model->>Cache: read/write reusable model state
      Model->>Backend: execute decode step
      Backend-->>Model: next logits
      Model-->>Runner: next logits + state
    end
  end
  Runner->>Cache: finalize or release request/session state
  Runner-->>Runner: return finish reason and usage
```

KV cache ownership must be explicit: Aljabr may own storage/layout primitives and backend-safe state handles; AQLI owns request/session policy, admission, reuse eligibility, tenant isolation and eviction policy. If current implementations combine these concerns, split them only after cache parity and lifecycle tests exist.

## 4. Streaming, cancellation and deadlines

```mermaid
sequenceDiagram
  autonumber
  actor Client
  participant Transport as AQLI Transport
  participant Scheduler as AQLI Scheduler
  participant Runner as AQLI Runner
  participant Engine as Aljabr Execution
  participant Resources as Model/Cache/Device Resources
  participant Metrics as AQLI Metrics

  Client->>Transport: request with deadline
  Transport->>Scheduler: request + cancellation token
  Scheduler->>Runner: admitted request
  loop each available output chunk
    Runner->>Engine: prefill/decode step
    Engine-->>Runner: result or interruption check
    Runner-->>Transport: output chunk
    Transport-->>Client: stream chunk
    alt client cancels or deadline expires
      Client--xTransport: disconnect/cancel
      Transport->>Scheduler: signal cancellation
      Scheduler->>Runner: cancel request
      Runner->>Engine: cooperative cancel / stop at safe point
      Engine-->>Runner: operation completed or cancelled
      Runner->>Resources: release request-scoped resources
      Runner->>Metrics: cancelled/timeout outcome
    end
  end
  Runner->>Resources: normal finalization
  Runner->>Metrics: completion latency and usage
  Transport-->>Client: final chunk / cancellation status
```

Cancellation must not free native/device memory while an in-flight operation can still access it. Cancellation should be cooperative at documented safe points; deadline expiry must be distinguished from client cancellation and model/backend failure.

## 5. Shutdown and model unload

```mermaid
sequenceDiagram
  autonumber
  participant Admin as AQLI Lifecycle/Admin
  participant Admission as AQLI Admission/Scheduler
  participant Registry as AQLI Model Registry
  participant Runner as AQLI Runner
  participant Engine as Aljabr Model Handle
  participant Backend as Aljabr Backend
  participant Metrics as AQLI Observability

  Admin->>Admission: stop accepting new requests
  Admission-->>Admin: admission closed
  Admin->>Admission: drain or cancel in-flight work
  Admission->>Runner: finish/cancel outstanding requests
  Runner->>Engine: complete outstanding operations
  Runner-->>Admission: request resources released
  Admin->>Registry: unload models
  Registry->>Engine: close model handle
  Engine->>Backend: synchronize and release device/native resources
  Backend-->>Engine: resources released
  Engine-->>Registry: closed
  Registry-->>Admin: all models unloaded
  Admin->>Metrics: flush final telemetry
  Admin-->>Admin: terminate service
```

Shutdown ordering is important: stop admission first, drain/cancel requests, then release model/backend resources. Never unload a model while requests still hold live references unless the model API provides safe reference-counted lifetime semantics.

## 6. Failure and fallback policy

```mermaid
flowchart TD
  Start["Load or execute request"] --> Validate["Validate request, model and capabilities"]
  Validate -->|invalid| ClientErr["Return typed client/model error"]
  Validate -->|valid| Resolve["Resolve provider and required runtime"]
  Resolve -->|dependency missing| CapabilityErr["Return actionable provider unavailable error"]
  Resolve -->|supported| Execute["Execute through selected backend"]
  Execute -->|success| Result["Return result"]
  Execute -->|recoverable backend failure| Classify["Classify whether retry/fallback is safe"]
  Classify -->|safe and policy permits| Fallback["Switch to permitted provider at safe boundary"]
  Classify -->|unsafe or policy forbids| RuntimeErr["Return typed execution error"]
  Fallback --> Execute
  Execute -->|timeout/cancel| Cleanup["Stop safely and release request resources"]
  RuntimeErr --> Cleanup
  ClientErr --> End["Record outcome and finish"]
  CapabilityErr --> End
  Result --> End
  Cleanup --> End
```

Fallback is not automatic for every error. It must be explicit in the request/provider policy and safe with respect to model format, quantization, state, numerical behavior and performance expectations. Do not hide ABI errors or incorrect outputs behind a fallback that makes the run appear successful.

## 7. Cross-repository integration and build sequence

```mermaid
sequenceDiagram
  autonumber
  participant Dev as Developer
  participant A as Aljabr Build/CI
  participant P as Published Aljabr Artifacts
  participant Q as AQLI Build/CI
  participant Test as Integration/Parity Tests

  Dev->>A: change public API or implementation
  A->>A: compile, unit tests, native/backend checks
  A-->>Dev: green required Aljabr CI + versioned artifact
  Dev->>P: publish or stage artifact
  Dev->>Q: resolve dependency via published coordinates
  Q->>Q: compile AQLI adapter and runtime
  Q->>Test: run request/load/decode/lifecycle parity
  Test-->>Q: correctness + resource + performance evidence
  Q-->>Dev: merge only when required checks pass
```

During development, composite builds are useful, but they are not sufficient: run at least one published-artifact resolution check to catch missing modules, incorrect coordinates, and hidden source-tree coupling.

## 8. Minimum sequence-level test matrix

| Flow | Positive case | Negative case | Lifecycle assertion |
|---|---|---|---|
| Request | valid prompt, streaming and non-streaming | invalid options / unsupported model | request state released |
| Model load | cached and fresh download | malformed metadata, digest mismatch, missing runtime | partial allocations closed |
| Decode | greedy and sampled generation | backend failure, invalid logits | cache state remains consistent |
| Cancellation | client cancel and deadline | cancellation during prefill/decode | no use-after-free; resources eventually released |
| Unload | idle model and drained model | unload requested while active | reference/lifecycle rules enforced |
| Fallback | allowed compatible provider | ABI mismatch / unsupported quantization | no silent unsafe fallback |
| Build integration | composite and published artifacts | missing optional dependency | default inference remains buildable |

## 9. Diagram maintenance

When implementation changes, update these flows alongside public API changes. If the implementation differs from the target, document the deviation and its migration step rather than drawing an idealized diagram as if it were already implemented.
