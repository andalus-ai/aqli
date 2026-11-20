package tech.kayys.aqli.cli.commands.audio;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for audio metadata replay options. */
public final class AudioReplayRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "audio-replay";
    }

    @Override
    public RunCapability create() {
        return new AudioReplayRunCapability();
    }
}
