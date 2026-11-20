package tech.kayys.aqli.cli.commands.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies external plugin loading option aliases and defaults. */
class ExternalPluginLoadingRunCapabilityTest {

    @Test
    void snapshotsClasspathAndDirectoryAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--external-plugin-classpath", "plugins/a.jar,plugins/b.jar",
                "--external-plugin-dir", "plugins/local,plugins/shared");

        ExternalPluginLoadingRunOptions options =
                new ExternalPluginLoadingRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(List.of("plugins/a.jar", "plugins/b.jar"), options.classpathEntries());
        assertEquals(List.of("plugins/local", "plugins/shared"), options.pluginDirectoryEntries());
    }

    @Test
    void defaultsToEmptyPluginLocations() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        ExternalPluginLoadingRunOptions options =
                new ExternalPluginLoadingRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(List.of(), options.classpathEntries());
        assertEquals(List.of(), options.pluginDirectoryEntries());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ExternalPluginLoadingRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
