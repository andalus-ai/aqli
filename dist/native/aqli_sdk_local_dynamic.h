#ifndef __AQLI_SDK_LOCAL_H
#define __AQLI_SDK_LOCAL_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef char* (*golek_version_fn_t)(graal_isolatethread_t*);

typedef long long int (*golek_client_create_fn_t)(graal_isolatethread_t*);

typedef int (*golek_client_destroy_fn_t)(graal_isolatethread_t*, long long int);

typedef void (*golek_client_shutdown_runtime_fn_t)(graal_isolatethread_t*);

typedef char* (*golek_last_error_fn_t)(graal_isolatethread_t*);

typedef void (*golek_clear_last_error_fn_t)(graal_isolatethread_t*);

typedef void (*golek_string_free_fn_t)(graal_isolatethread_t*, char*);

typedef char* (*golek_create_completion_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_create_completion_async_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_stream_completion_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_submit_async_job_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_get_job_status_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_wait_for_job_json_fn_t)(graal_isolatethread_t*, long long int, char*, long long int, long long int);

typedef char* (*golek_batch_inference_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_list_available_providers_json_fn_t)(graal_isolatethread_t*, long long int);

typedef char* (*golek_get_provider_info_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef int (*golek_set_preferred_provider_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_get_preferred_provider_json_fn_t)(graal_isolatethread_t*, long long int);

typedef char* (*golek_list_models_json_fn_t)(graal_isolatethread_t*, long long int);

typedef char* (*golek_list_models_paginated_json_fn_t)(graal_isolatethread_t*, long long int, int, int);

typedef char* (*golek_get_model_info_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef char* (*golek_pull_model_json_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef int (*golek_delete_model_fn_t)(graal_isolatethread_t*, long long int, char*);

typedef int (*run_main_fn_t)(int argc, char** argv);

#if defined(__cplusplus)
}
#endif
#endif
