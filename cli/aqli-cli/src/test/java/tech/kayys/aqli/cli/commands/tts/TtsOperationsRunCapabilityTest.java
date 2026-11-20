package tech.kayys.aqli.cli.commands.tts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies aliases and typed snapshots for operational TTS options. */
class TtsOperationsRunCapabilityTest {

    @Test
    void snapshotsOperationalOptionsAndAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--tts-preflight",
                "--onnx-warmup",
                "--onnx-cache-prune",
                "--onnx-cache-prune-apply",
                "--voices",
                "--tts-voices-json");

        TtsOperationsRunOptions options =
                new TtsOperationsRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.diagnostics());
        assertTrue(options.warmup());
        assertTrue(options.cachePrune());
        assertTrue(options.cachePruneApply());
        assertTrue(options.listVoices());
        assertTrue(options.listVoicesJson());
    }

    @Test
    void keepsOperationalOptionsDisabledByDefault() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        TtsOperationsRunOptions options =
                new TtsOperationsRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.diagnostics());
        assertFalse(options.warmup());
        assertFalse(options.cachePrune());
        assertFalse(options.cachePruneApply());
        assertFalse(options.listVoices());
        assertFalse(options.listVoicesJson());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new TtsOperationsRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
