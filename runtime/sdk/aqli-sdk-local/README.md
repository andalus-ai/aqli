# aqli-sdk-java-local

`aqli-sdk-java-local` is the single client-facing entrypoint for local Aqli SDK usage.

## Native FFI entrypoints

The module exposes GraalVM `@CEntryPoint` functions in:

- `tech.kayys.aqli.sdk.local.nativeinterop.AqliNativeEntrypoints`

Core lifecycle:

- `aqli_client_create`
- `aqli_client_destroy`
- `aqli_client_shutdown_runtime`
- `aqli_last_error`
- `aqli_clear_last_error`
- `aqli_string_free`

SDK feature entrypoints (JSON payloads):

- `aqli_create_completion_json`
- `aqli_create_completion_async_json`
- `aqli_stream_completion_json`
- `aqli_submit_async_job_json`
- `aqli_get_job_status_json`
- `aqli_wait_for_job_json`
- `aqli_batch_inference_json`
- `aqli_list_available_providers_json`
- `aqli_get_provider_info_json`
- `aqli_set_preferred_provider`
- `aqli_get_preferred_provider_json`
- `aqli_list_models_json`
- `aqli_list_models_paginated_json`
- `aqli_get_model_info_json`
- `aqli_pull_model_json`
- `aqli_delete_model`

## Native shared library build

Example:

```bash
mvn -f inference-aqli/sdk/aqli-sdk-java-local/pom.xml -DskipTests package

native-image \
  -cp inference-aqli/sdk/aqli-sdk-java-local/target/classes \
  --shared \
  -H:Name=libaqli_sdk_local \
  -H:Class=tech.kayys.aqli.sdk.local.nativeinterop.AqliNativeEntrypoints
```

All returned `char*` values must be released by calling `aqli_string_free`.
