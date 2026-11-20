package tech.kayys.aqli.cli.commands.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies TTS voice-list aliases, search filtering, and defaults. */
class TtsVoiceDiscoveryRunCapabilityTest {

    @Test
    void snapshotsVoiceListingAliasesAndSearchFilter() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--voices", "--tts-voices-json", "--tts-voice-search", "jun");

        TtsVoiceDiscoveryRunOptions options =
                new TtsVoiceDiscoveryRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.listVoices());
        assertTrue(options.listVoicesJson());
        assertEquals("jun", options.voiceSearch());
    }

    @Test
    void keepsVoiceDiscoveryDisabledByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        TtsVoiceDiscoveryRunOptions options =
                new TtsVoiceDiscoveryRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.listVoices());
        assertFalse(options.listVoicesJson());
        assertNull(options.voiceSearch());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new TtsVoiceDiscoveryRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
