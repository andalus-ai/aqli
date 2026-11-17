package tech.kayys.aqli.cli.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;

@QuarkusTest
class RunCapabilityCommandLineTest {

    @Inject
    CommandLine commandLine;

    @Test
    void registersServiceLoadedCapabilityOptionsBeforeArgumentParsing() {
        CommandLine runCommand = commandLine.getSubcommands().get("run");
        assertNotNull(runCommand, "run command must remain part of the Quarkus command tree");

        OptionSpec option = runCommand.getCommandSpec().findOption("--registry-probe");
        assertNotNull(option, "ServiceLoader capability option must be present in the command model");

        CommandLine.ParseResult parsed = commandLine.parseArgs("run", "--registry-probe");
        assertNotNull(parsed.subcommand());
        assertTrue(parsed.subcommand().hasMatchedOption("--registry-probe"));
    }
}
