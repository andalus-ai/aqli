package tech.kayys.aqli.cli.commands.plugin;

import java.util.List;
import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;
import tech.kayys.aqli.cli.util.ExternalPluginClasspath;

/** Registers and snapshots external plugin classpath and directory options. */
public final class ExternalPluginLoadingRunCapability
        implements RunCapability, RunCapabilityOptions<ExternalPluginLoadingRunOptions> {

    @Override
    public String id() {
        return "external-plugin-loading";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder(
                        ExternalPluginClasspath.OPTION_PLUGIN_CLASSPATH,
                        ExternalPluginClasspath.OPTION_EXTERNAL_PLUGIN_CLASSPATH)
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description(ExternalPluginClasspath.MODEL_FAMILY_OPTION_DESCRIPTION)
                .build());
        commandSpec.addOption(OptionSpec.builder(
                        ExternalPluginClasspath.OPTION_PLUGIN_DIR,
                        ExternalPluginClasspath.OPTION_EXTERNAL_PLUGIN_DIR)
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description(ExternalPluginClasspath.PLUGIN_DIRECTORY_OPTION_DESCRIPTION)
                .build());
    }

    @Override
    public Class<ExternalPluginLoadingRunOptions> optionsType() {
        return ExternalPluginLoadingRunOptions.class;
    }

    @Override
    public ExternalPluginLoadingRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        List<String> classpath = parseResult.matchedOptionValue(
                ExternalPluginClasspath.OPTION_PLUGIN_CLASSPATH, List.of());
        List<String> directories = parseResult.matchedOptionValue(
                ExternalPluginClasspath.OPTION_PLUGIN_DIR, List.of());
        return new ExternalPluginLoadingRunOptions(classpath, directories);
    }
}
