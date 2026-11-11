package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.spi.inference.AsyncJobStatus;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.time.Duration;

/**
 * Capability port for submitting and observing asynchronous inference jobs.
 *
 * <p>This contract is independent of model format and execution backend.
 * Implementations own job submission and status observation semantics.</p>
 */
public interface AsyncJobOperations {

    String submitAsyncJob(InferenceRequest request) throws SdkException;

    AsyncJobStatus getJobStatus(String jobId) throws SdkException;

    AsyncJobStatus waitForJob(String jobId, Duration maxWaitTime, Duration pollInterval)
            throws SdkException;
}
