/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * Scoped classloader for commands that inspect detachable plugin classpaths.
 */
public final class ExternalPluginClasspathScope implements AutoCloseable {
    private final List<Path> classpath;
    private final URLClassLoader classLoader;

    private ExternalPluginClasspathScope(List<Path> classpath, URLClassLoader classLoader) {
        this.classpath = classpath == null ? List.of() : List.copyOf(classpath);
        this.classLoader = classLoader;
    }

    public static ExternalPluginClasspathScope empty() {
        return new ExternalPluginClasspathScope(List.of(), null);
    }

    public static ExternalPluginClasspathScope open(List<String> entries, Class<?> parentAnchor) {
        return open(entries, List.of(), parentAnchor);
    }

    public static ExternalPluginClasspathScope open(
            List<String> entries,
            List<String> pluginDirectoryEntries,
            Class<?> parentAnchor) {
        return openParsed(ExternalPluginClasspath.resolve(entries, pluginDirectoryEntries), parentAnchor);
    }

    public static ExternalPluginClasspathScope open(String[] args, int startIndex, Class<?> parentAnchor) {
        return openParsed(ExternalPluginClasspath.resolve(args, startIndex), parentAnchor);
    }

    public static ExternalPluginClasspathScope openParsed(List<Path> classpath, Class<?> parentAnchor) {
        return new ExternalPluginClasspathScope(
                classpath,
                ExternalPluginClasspath.classLoader(classpath, parentAnchor));
    }

    public ClassLoader discoveryClassLoader() {
        return classLoader;
    }

    public boolean active() {
        return classLoader != null;
    }

    public String registryScope() {
        return active() ? "scoped" : "global";
    }

    public List<Path> classpath() {
        return classpath;
    }

    public List<String> displayClasspath() {
        return ExternalPluginClasspath.display(classpath);
    }

    @Override
    public void close() throws Exception {
        if (classLoader != null) {
            classLoader.close();
        }
    }
}
