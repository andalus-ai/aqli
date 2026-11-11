# Remote SDK adapter (prototype, currently disabled)

> **Status:** This module is not part of the active Gradle build and is not a
> supported or publishable remote SDK artifact. The project is intentionally
> excluded from `settings.gradle.kts` while its implementation is migrated to
> the canonical runtime API.

The sources under `src/main/java` currently include Java code stored with a
`.txt` extension. Gradle does not compile those files. In particular, the
remote provider is not an available `AqliRuntimeProvider`, and this module
must not be treated as providing working remote SDK support.

## Current supported contracts

The transport-neutral client contract is in
`framework/sdk/aqli-sdk-api`:

- `tech.kayys.aqli.sdk.api.AqliClient`
- `tech.kayys.aqli.sdk.api.AqliClientProvider`

The runtime-management contract and capability ports are in
`framework/sdk/aqli-sdk-core`:

- `tech.kayys.aqli.sdk.runtime.AqliRuntime`
- `tech.kayys.aqli.sdk.runtime.AqliRuntimeProvider`

The active local implementation is maintained separately in
`runtime/sdk/aqli-sdk-local`.

## Requirements before re-enabling this module

A future remote adapter must:

1. Provide compiled Java sources rather than `.txt` source artifacts.
2. Implement the canonical `AqliRuntime` / `AqliRuntimeProvider` contracts,
   not the deprecated `sdk.core.AqliSdk` compatibility alias.
3. Map the shared runtime request and response types to the remote HTTP
   protocol without introducing a second public client contract.
4. Register only compiled provider implementations in
   `META-INF/services`.
5. Include focused tests for provider discovery, HTTP error mapping,
   synchronous and asynchronous inference, streaming, and cancellation.
6. Be explicitly re-enabled in `settings.gradle.kts` only after its module
   compiles and its tests pass.

Until those requirements are met, do not add this module as a dependency or
register its provider through ServiceLoader.
