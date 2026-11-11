package tech.kayys.aqli.sdk.runtime;

import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.exception.SdkException;

/**
 * Service provider contract for local and remote AQLI runtime implementations.
 * Implementations are discovered through {@link java.util.ServiceLoader}.
 */
public interface AqliRuntimeProvider {

    enum Mode {
        LOCAL,
        REMOTE
    }

    Mode mode();

    AqliRuntime create(SdkConfig config) throws SdkException;

    default int priority() {
        return 100;
    }
}
