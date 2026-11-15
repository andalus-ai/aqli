package tech.kayys.aqli.sdk.runtime;

import java.util.function.Consumer;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.model.HuggingFacePullResult;
import tech.kayys.aqli.sdk.model.PullProgress;

/**
 * Hugging Face-specific model lifecycle capability.
 *
 * <p>Provider-specific options stay in this contract instead of expanding
 * the provider-neutral {@link ModelLifecycleOperations} request.</p>
 */
public interface HuggingFaceModelOperations {

    /**
     * Pull a Hugging Face repository with repository-specific selection and
     * task classification options.
     */
    default HuggingFacePullResult pullHuggingFaceModel(
            HuggingFacePullRequest request,
            Consumer<PullProgress> progressCallback) throws SdkException {
        throw new SdkException("SDK_ERR_NOT_SUPPORTED",
                "Hugging Face model pulls are not supported by this SDK implementation.");
    }
}
