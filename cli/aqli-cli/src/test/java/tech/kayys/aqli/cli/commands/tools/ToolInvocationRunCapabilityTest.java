package tech.kayys.aqli.cli.commands.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies tool invocation flags, aliases, splitting, and defaults. */
class ToolInvocationRunCapabilityTest {

    @Test
    void snapshotsToolFilesChoiceAndMcpTools() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--tool-file", "tools-a.json,tools-b.json",
                "--tool-choice", "{\"type\":\"function\",\"function\":{\"name\":\"lookup\"}}",
                "--mcp-tools", "server-a/search,server-b/fetch");

        ToolInvocationRunOptions options =
                new ToolInvocationRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(java.util.List.of("tools-a.json", "tools-b.json"), options.toolFiles());
        assertEquals("{\"type\":\"function\",\"function\":{\"name\":\"lookup\"}}", options.toolChoice());
        assertEquals(java.util.List.of("server-a/search", "server-b/fetch"), options.mcpTools());
    }

    @Test
    void snapshotsLegacyToolFileAliasAndEmptyDefaults() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs("--tools-file", "tools.json", "--mcp-tool", "server/search");

        ToolInvocationRunOptions options =
                new ToolInvocationRunCapability().snapshot(commandLine.getParseResult());

        assertEquals(java.util.List.of("tools.json"), options.toolFiles());
        assertEquals(java.util.List.of("server/search"), options.mcpTools());
        assertNull(options.toolChoice());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new ToolInvocationRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
