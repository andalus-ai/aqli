package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.feature.AqliFeatureKit;

/**
 * Access to optional high-level ML features exposed by the runtime.
 */
public interface FeatureOperations {

    /**
     * Return the feature kit for face recognition, natural language, and document scanning.
     *
     * @throws UnsupportedOperationException when the feature kit is unavailable
     */
    default AqliFeatureKit featureKit() throws SdkException {
        throw new UnsupportedOperationException(
                "Feature kit is not supported by this SDK implementation.");
    }
}
