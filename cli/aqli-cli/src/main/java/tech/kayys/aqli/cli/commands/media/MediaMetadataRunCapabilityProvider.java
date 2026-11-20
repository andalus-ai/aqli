package tech.kayys.aqli.cli.commands.media;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for media metadata CLI options. */
public final class MediaMetadataRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "media-metadata";
    }

    @Override
    public RunCapability create() {
        return new MediaMetadataRunCapability();
    }
}
