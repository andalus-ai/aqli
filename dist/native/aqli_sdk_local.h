#ifndef __AQLI_SDK_LOCAL_H
#define __AQLI_SDK_LOCAL_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

char* golek_version(graal_isolatethread_t*);

long long int golek_client_create(graal_isolatethread_t*);

int golek_client_destroy(graal_isolatethread_t*, long long int);

void golek_client_shutdown_runtime(graal_isolatethread_t*);

char* golek_last_error(graal_isolatethread_t*);

void golek_clear_last_error(graal_isolatethread_t*);

void golek_string_free(graal_isolatethread_t*, char*);

char* golek_create_completion_json(graal_isolatethread_t*, long long int, char*);

char* golek_create_completion_async_json(graal_isolatethread_t*, long long int, char*);

char* golek_stream_completion_json(graal_isolatethread_t*, long long int, char*);

char* golek_submit_async_job_json(graal_isolatethread_t*, long long int, char*);

char* golek_get_job_status_json(graal_isolatethread_t*, long long int, char*);

char* golek_wait_for_job_json(graal_isolatethread_t*, long long int, char*, long long int, long long int);

char* golek_batch_inference_json(graal_isolatethread_t*, long long int, char*);

char* golek_list_available_providers_json(graal_isolatethread_t*, long long int);

char* golek_get_provider_info_json(graal_isolatethread_t*, long long int, char*);

int golek_set_preferred_provider(graal_isolatethread_t*, long long int, char*);

char* golek_get_preferred_provider_json(graal_isolatethread_t*, long long int);

char* golek_list_models_json(graal_isolatethread_t*, long long int);

char* golek_list_models_paginated_json(graal_isolatethread_t*, long long int, int, int);

char* golek_get_model_info_json(graal_isolatethread_t*, long long int, char*);

char* golek_pull_model_json(graal_isolatethread_t*, long long int, char*);

int golek_delete_model(graal_isolatethread_t*, long long int, char*);

int run_main(int argc, char** argv);

#if defined(__cplusplus)
}
#endif
#endif
