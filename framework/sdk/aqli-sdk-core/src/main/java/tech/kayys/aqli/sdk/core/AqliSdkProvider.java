package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.exception.SdkException;

/**
 * @deprecated Use {@link tech.kayys.aqli.sdk.runtime.AqliRuntimeProvider}.
 */
@Deprecated(forRemoval = false)
public interface AqliSdkProvider extends tech.kayys.aqli.sdk.runtime.AqliRuntimeProvider {

    @Override
    AqliSdk create(SdkConfig config) throws SdkException;
}
