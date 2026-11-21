package tech.kayys.aqli.cli.commands.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies shared media output destination and extension snapshots. */
class MediaOutputRunCapabilityTest {

    @Test
    void snapshotsOutputPathAndExtensionAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("-o", "generated.wav", "--ext", "flac");

        MediaOutputRunOptions options =
                new MediaOutputRunCapability().snapshot(commandLine.getParseResult());

        assertEquals("generated.wav", options.outputPath());
        assertEquals("flac", options.extension());
    }

    @Test
    void defaultsOutputPathAndExtensionToUnset() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        MediaOutputRunOptions options =
                new MediaOutputRunCapability().snapshot(commandLine.getParseResult());

        assertNull(options.outputPath());
        assertNull(options.extension());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new MediaOutputRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
