package tech.kayys.aqli.sdk.core;

import tech.kayys.aqli.sdk.config.SdkConfig;
import tech.kayys.aqli.sdk.exception.SdkException;

/**
 * Service Provider Interface for Aqli SDK implementations.
 * 
 * <p>
 * Each implementation module (local, remote) registers a provider via
 * {@code META-INF/services/tech.kayys.aqli.sdk.core.AqliSdkProvider}.
 * The facade module discovers providers at runtime using
 * {@link java.util.ServiceLoader}.
 *
 * <p>
 * Implementations can also be discovered via CDI when running inside a
 * CDI-enabled container (e.g., Quarkus).
 */
public interface AqliSdkProvider {

    /**
     * The deployment mode this provider supports.
     */
    enum Mode {
        /** Local / standalone — engine runs in the same JVM. */
        LOCAL,
        /** Remote / distributed — communicates with engine via HTTP. */
        REMOTE
    }

    /**
     * Returns the deployment mode this provider supports.
     *
     * @return the mode (LOCAL or REMOTE)
     */
    Mode mode();

    /**
     * Creates a new {@link AqliSdk} instance with the given configuration.
     *
     * @param config SDK configuration (may be {@code null} for defaults)
     * @return a configured SDK instance
     * @throws SdkException if creation fails
     */
    AqliSdk create(SdkConfig config) throws SdkException;

    /**
     * Provider priority. Lower values are preferred when multiple providers
     * are available for the same mode.
     *
     * @return priority value (default 100)
     */
    default int priority() {
        return 100;
    }
}
