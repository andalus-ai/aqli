package tech.kayys.aqli.cli.commands.audio;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for audio-generation CLI options. */
public final class AudioRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "audio";
    }

    @Override
    public RunCapability create() {
        return new AudioRunCapability();
    }
}
