package tech.kayys.aqli.cli.commands.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies runtime profile option parsing and aliases. */
class RuntimeProfileRunCapabilityTest {

    @Test
    void snapshotsEnabledProfileOption() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--profile");

        assertTrue(new RuntimeProfileRunCapability()
                .snapshot(commandLine.getParseResult()).enabled());
    }

    @Test
    void snapshotsOnnxAlias() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--onnx-profile");

        assertTrue(new RuntimeProfileRunCapability()
                .snapshot(commandLine.getParseResult()).enabled());
    }

    @Test
    void defaultsToDisabled() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        assertFalse(new RuntimeProfileRunCapability()
                .snapshot(commandLine.getParseResult()).enabled());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new RuntimeProfileRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
