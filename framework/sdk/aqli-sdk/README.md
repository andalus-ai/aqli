# AQLI SDK implementation module

This module provides the implementation-side SDK provider and transport adapters for the public contract in `:sdk:aqli-sdk-api`.

## Responsibilities

- Register an `AqliClientProvider` implementation through Java `ServiceLoader`.
- Compose the selected client adapter for in-process, REST, gRPC or CLI transport where supported.
- Keep transport and runtime construction out of the API contract module.
- Maintain legacy SDK façades only as compatibility bridges while their consumers migrate.

## Canonical API

New public client code should import:

```java
import tech.kayys.aqli.sdk.api.AqliClient;
```

Do not use the older `tech.kayys.aqli.sdk.AqliClient` root-package interface for new integrations. The canonical API is defined in `framework/sdk/aqli-sdk-api`.

The separate `AqliSdkFactory` returns the aggregate `AqliRuntime` compatibility/runtime surface; it does not return the canonical `AqliClient`. See the [SDK module guide](../README.md) and [SDK contract consolidation](../../../docs/architecture/SDK_CONTRACT_CONSOLIDATION.md).
