package tech.kayys.aqli.cli.commands.response;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for response delivery options. */
public final class ResponseDeliveryRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "response-delivery";
    }

    @Override
    public RunCapability create() {
        return new ResponseDeliveryRunCapability();
    }
}
