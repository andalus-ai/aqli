package tech.kayys.aqli.cli.commands.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Verifies route-report aliases and defaults. */
class RouteReportRunCapabilityTest {

    @Test
    void snapshotsRouteReportOptionsAndAliases() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs(
                "--dry-run-route",
                "--dry-run-route-allow-pull",
                "--dry-run-route-require-local");

        RouteReportRunOptions options =
                new RouteReportRunCapability().snapshot(commandLine.getParseResult());

        assertTrue(options.enabled());
        assertTrue(options.allowPull());
        assertTrue(options.requireLocal());
    }

    @Test
    void defaultsRouteReportOptionsToDisabled() {
        CommandLine commandLine = commandLine();
        commandLine.parseArgs();

        RouteReportRunOptions options =
                new RouteReportRunCapability().snapshot(commandLine.getParseResult());

        assertFalse(options.enabled());
        assertFalse(options.allowPull());
        assertFalse(options.requireLocal());
    }

    private static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new TestCommand());
        new RouteReportRunCapability().registerOptions(commandLine.getCommandSpec());
        return commandLine;
    }

    @Command
    private static final class TestCommand {
    }
}
