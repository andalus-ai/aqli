package tech.kayys.aqli.cli.commands.image;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for image metadata options. */
public final class ImageMetadataRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "image-metadata";
    }

    @Override
    public RunCapability create() {
        return new ImageMetadataRunCapability();
    }
}
