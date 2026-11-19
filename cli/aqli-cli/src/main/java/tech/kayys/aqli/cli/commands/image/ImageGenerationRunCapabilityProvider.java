package tech.kayys.aqli.cli.commands.image;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for image-generation CLI options. */
public final class ImageGenerationRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "image-generation";
    }

    @Override
    public RunCapability create() {
        return new ImageGenerationRunCapability();
    }
}
