package tech.kayys.aqli.cli.commands.tts;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for operational TTS CLI options. */
public final class TtsOperationsRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "tts-operations";
    }

    @Override
    public RunCapability create() {
        return new TtsOperationsRunCapability();
    }
}
