package tech.kayys.aqli.sdk.local;

import jakarta.enterprise.inject.spi.CDI;
import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.core.AqliSdkProvider;
import tech.kayys.aqli.sdk.exception.SdkException;

/**
 * ServiceLoader entry point for the local SDK.
 *
 * <p>Provider discovery itself is container-independent. Creating the SDK
 * requires the CDI container because inference engines and their native
 * dependencies are container-managed.</p>
 */
public final class LocalAqliSdkProvider implements AqliSdkProvider {

    @Override
    public Mode mode() {
        return Mode.LOCAL;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public AqliSdk create(SdkConfig config) throws SdkException {
        try {
            return CDI.current().select(LocalAqliSdkFactory.class).get().create(config);
        } catch (RuntimeException e) {
            throw new SdkException("SDK_ERR_INIT",
                    "Unable to create the local SDK. Start it inside the AQLI CDI runtime with the local runner modules installed.",
                    e);
        }
    }
}
