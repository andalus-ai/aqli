# AQLI SDK API

This module defines the transport-neutral public client contract.

## Public API

- `tech.kayys.aqli.sdk.api.AqliClient` — canonical application-facing client interface.
- `AqliClient.GenerationRequest` and `GenerationResult` — request and response values.
- `AqliClientProvider` — provider boundary discovered by Java `ServiceLoader`.
- `ClientTransport` — transport selection for an installed implementation.

The API module must not depend on local runner implementations, Quarkus CDI, HTTP clients, or native inference backends. Those belong in provider/implementation modules.

## Provider discovery

`AqliClient.builder()` resolves an `AqliClientProvider` from the runtime classpath. Add a supported implementation module (currently `:sdk:aqli-sdk`) to provide that service. If no provider is present, the builder reports the missing implementation dependency explicitly.

## Compatibility

The separate `tech.kayys.aqli.sdk.AqliSdk` aggregate and root-package `tech.kayys.aqli.sdk.AqliClient` are legacy surfaces being consolidated. New code should use the canonical `tech.kayys.aqli.sdk.api.AqliClient` contract and SDK-owned capability DTOs.

See [SDK Contract Consolidation](../../../docs/architecture/SDK_CONTRACT_CONSOLIDATION.md) for the migration plan.
