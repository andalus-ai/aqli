package tech.kayys.aqli.cli.commands.media;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for shared media output options. */
public final class MediaOutputRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "media-output";
    }

    @Override
    public RunCapability create() {
        return new MediaOutputRunCapability();
    }
}
