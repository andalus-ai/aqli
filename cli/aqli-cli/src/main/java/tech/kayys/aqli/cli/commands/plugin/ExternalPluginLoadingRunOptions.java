package tech.kayys.aqli.cli.commands.plugin;

import java.util.List;

/** Immutable external plugin loading inputs for one run invocation. */
public record ExternalPluginLoadingRunOptions(
        List<String> classpathEntries,
        List<String> pluginDirectoryEntries) {

    public ExternalPluginLoadingRunOptions {
        classpathEntries = List.copyOf(classpathEntries == null ? List.of() : classpathEntries);
        pluginDirectoryEntries = List.copyOf(
                pluginDirectoryEntries == null ? List.of() : pluginDirectoryEntries);
    }
}
