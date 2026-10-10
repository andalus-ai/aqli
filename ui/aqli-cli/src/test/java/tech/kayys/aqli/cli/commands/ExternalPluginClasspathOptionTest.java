package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import tech.kayys.aqli.cli.util.ExternalPluginClasspath;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPluginClasspathOptionTest {
    @Test
    void runShowAndModulesShareExternalPluginClasspathAliases() {
        assertExternalPluginClasspathOption(
                new RunCommand(),
                ExternalPluginClasspath.MODEL_FAMILY_OPTION_DESCRIPTION);
        assertExternalPluginClasspathOption(
                new ShowCommand(),
                ExternalPluginClasspath.MODEL_FAMILY_OPTION_DESCRIPTION);
        assertExternalPluginClasspathOption(
                new ExtensionsCommand(),
                ExternalPluginClasspath.MODEL_FAMILY_AND_EXTENSION_OPTION_DESCRIPTION);
    }

    @Test
    void runShowAndModulesShareExternalPluginDirectoryAliases() {
        assertExternalPluginDirectoryOption(new RunCommand());
        assertExternalPluginDirectoryOption(new ShowCommand());
        assertExternalPluginDirectoryOption(new ExtensionsCommand());
    }

    @Test
    void modulesExposePluginDoctorAliases() {
        OptionSpec option = new CommandLine(new ExtensionsCommand())
                .getCommandSpec()
                .findOption("--doctor");
        assertNotNull(option);
        assertTrue(Arrays.asList(option.names()).contains("--doctor"));
        assertTrue(Arrays.asList(option.names()).contains("--plugin-doctor"));
        assertArrayEquals(new String[] { "Print a focused detachable plugin validation report" },
                option.description());
    }

    private static void assertExternalPluginClasspathOption(Object command, String expectedDescription) {
        OptionSpec option = new CommandLine(command)
                .getCommandSpec()
                .findOption(ExternalPluginClasspath.OPTION_PLUGIN_CLASSPATH);
        assertNotNull(option);
        assertTrue(Arrays.asList(option.names()).contains(ExternalPluginClasspath.OPTION_PLUGIN_CLASSPATH));
        assertTrue(Arrays.asList(option.names()).contains(ExternalPluginClasspath.OPTION_EXTERNAL_PLUGIN_CLASSPATH));
        assertArrayEquals(new String[] { expectedDescription }, option.description());
    }

    private static void assertExternalPluginDirectoryOption(Object command) {
        OptionSpec option = new CommandLine(command)
                .getCommandSpec()
                .findOption(ExternalPluginClasspath.OPTION_PLUGIN_DIR);
        assertNotNull(option);
        assertTrue(Arrays.asList(option.names()).contains(ExternalPluginClasspath.OPTION_PLUGIN_DIR));
        assertTrue(Arrays.asList(option.names()).contains(ExternalPluginClasspath.OPTION_EXTERNAL_PLUGIN_DIR));
        assertArrayEquals(new String[] { ExternalPluginClasspath.PLUGIN_DIRECTORY_OPTION_DESCRIPTION },
                option.description());
        assertTrue(option.description()[0].contains("comma"));
        assertTrue(option.description()[0].contains("path-separated"));
    }
}
