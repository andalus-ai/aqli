package tech.kayys.aqli.cli.spi.run;

/**
 * ServiceLoader entry point for an independently packaged run capability.
 */
public interface RunCapabilityProvider {

    /**
     * Stable identifier of the capability supplied by this provider.
     */
    String capabilityId();

    /**
     * Creates the CLI contribution.
     */
    RunCapability create();
}
