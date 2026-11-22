package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for response-cache run options. */
public final class ResponseCacheRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "response-cache";
    }

    @Override
    public RunCapability create() {
        return new ResponseCacheRunCapability();
    }
}
