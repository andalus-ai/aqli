package tech.kayys.aqli.cli.commands.tts;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for TTS voice discovery options. */
public final class TtsVoiceDiscoveryRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "tts-voice-discovery";
    }

    @Override
    public RunCapability create() {
        return new TtsVoiceDiscoveryRunCapability();
    }
}
