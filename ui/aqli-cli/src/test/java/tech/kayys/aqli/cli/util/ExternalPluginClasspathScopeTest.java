package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPluginClasspathScopeTest {
    @TempDir
    Path tempDir;

    @Test
    void emptyClasspathUsesGlobalScope() throws Exception {
        try (ExternalPluginClasspathScope scope =
                ExternalPluginClasspathScope.open(List.of(), ExternalPluginClasspathScopeTest.class)) {
            assertFalse(scope.active());
            assertEquals("global", scope.registryScope());
            assertEquals(List.of(), scope.displayClasspath());
            assertEquals(List.of(), scope.classpath());
            assertNull(scope.discoveryClassLoader());
        }
    }

    @Test
    void presentClasspathUsesScopedClassLoader() throws Exception {
        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                List.of(tempDir.toString()),
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals("scoped", scope.registryScope());
            assertNotNull(scope.discoveryClassLoader());
            assertEquals(List.of(tempDir), scope.classpath());
            assertTrue(scope.displayClasspath().getFirst().contains(tempDir.getFileName().toString()));
        }
    }

    @Test
    void argsOffsetSkipsNonClasspathArguments() throws Exception {
        Path output = tempDir.resolve("report.json");

        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                new String[] { output.toString(), tempDir.toString() },
                1,
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals(List.of(tempDir), scope.classpath());
            assertEquals("scoped", scope.registryScope());
        }
    }

    @Test
    void argsParserSupportsPluginDirOptionForStandaloneUtilities() throws Exception {
        Path pluginDirectory = tempDir.resolve("option-plugins");
        Path jar = pluginDirectory.resolve("alpha.jar");
        Files.createDirectories(pluginDirectory);
        Files.createFile(jar);

        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                new String[] {
                        ExternalPluginClasspath.OPTION_PLUGIN_DIR,
                        pluginDirectory.toString()
                },
                0,
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals(List.of(jar.toAbsolutePath().normalize()), scope.classpath());
        }
    }

    @Test
    void argsParserSupportsCommaSeparatedPluginDirOption() throws Exception {
        Path firstPluginDirectory = tempDir.resolve("first-plugins");
        Path secondPluginDirectory = tempDir.resolve("second-plugins");
        Path firstJar = firstPluginDirectory.resolve("alpha.jar");
        Path secondJar = secondPluginDirectory.resolve("beta.jar");
        Files.createDirectories(firstPluginDirectory);
        Files.createDirectories(secondPluginDirectory);
        Files.createFile(firstJar);
        Files.createFile(secondJar);

        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                new String[] {
                        ExternalPluginClasspath.OPTION_PLUGIN_DIR,
                        firstPluginDirectory + "," + secondPluginDirectory
                },
                0,
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals(List.of(
                    firstJar.toAbsolutePath().normalize(),
                    secondJar.toAbsolutePath().normalize()), scope.classpath());
        }
    }

    @Test
    void argsParserSupportsInlineClasspathOptionAfterOffset() throws Exception {
        Path output = tempDir.resolve("report.json");
        Path classpathEntry = tempDir.resolve("standalone-classes");
        Files.createDirectories(classpathEntry);

        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                new String[] {
                        output.toString(),
                        ExternalPluginClasspath.OPTION_PLUGIN_CLASSPATH + "=" + classpathEntry
                },
                1,
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals(List.of(classpathEntry.toAbsolutePath().normalize()), scope.classpath());
        }
    }

    @Test
    void argsParserRejectsMissingPluginDirOptionValue() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ExternalPluginClasspathScope.open(
                        new String[] { ExternalPluginClasspath.OPTION_PLUGIN_DIR },
                        0,
                        ExternalPluginClasspathScopeTest.class));
        assertTrue(error.getMessage().contains("Missing value"));
    }

    @Test
    void pluginDirectoryAddsJarsAndServiceProviderClassDirectories() throws Exception {
        Path pluginDirectory = tempDir.resolve("plugins");
        Path jar = pluginDirectory.resolve("alpha.jar");
        Path classesDirectory = pluginDirectory.resolve("beta");
        Files.createDirectories(classesDirectory.resolve("META-INF/services"));
        Files.createFile(jar);

        try (ExternalPluginClasspathScope scope = ExternalPluginClasspathScope.open(
                List.of(),
                List.of(pluginDirectory.toString()),
                ExternalPluginClasspathScopeTest.class)) {
            assertTrue(scope.active());
            assertEquals("scoped", scope.registryScope());
            assertEquals(List.of(
                    jar.toAbsolutePath().normalize(),
                    classesDirectory.toAbsolutePath().normalize()), scope.classpath());
        }
    }

    @Test
    void pluginDirectoryInspectionReportsUnifiedRuntimeProviders() throws Exception {
        Path pluginDirectory = tempDir.resolve("runtime-plugins");
        Path jarPath = pluginDirectory.resolve("gemma4-runtime.jar");
        writeJarEntries(
                jarPath,
                Map.of(
                        ExternalPluginClasspath.UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY,
                        """
                        # fixture comment
                        external.fixture.ExternalGemma4UnifiedRuntime

                        external.fixture.ExternalGemma4UnifiedRuntime
                        """,
                        "external/fixture/ExternalGemma4UnifiedRuntime.class",
                        "fixture-bytecode-placeholder"));

        ExternalPluginClasspath.PluginDirectoryInspection inspection =
                ExternalPluginClasspath.inspectPluginDirectories(List.of(pluginDirectory.toString()));

        assertEquals(1, inspection.jarCount());
        assertEquals(0, inspection.modelFamilyPluginCandidates());
        assertEquals(1, inspection.unifiedRuntimePluginCandidates());
        assertEquals(1, inspection.unifiedRuntimeReady());
        assertEquals(0, inspection.unifiedRuntimeNotReady());
        assertEquals(List.of(jarPath.toAbsolutePath().normalize()), inspection.unifiedRuntimePluginJars());
        assertEquals(List.of(jarPath.toAbsolutePath().normalize()), inspection.unifiedRuntimeReadyJars());
        ExternalPluginClasspath.PluginDirectoryJarReport jar = inspection.jars().getFirst();
        assertFalse(jar.pluginInstallCandidate());
        assertTrue(jar.hasUnifiedMultimodalRuntimeServiceEntry());
        assertTrue(jar.unifiedRuntimeReady());
        assertEquals(List.of(), jar.unifiedRuntimeErrors());
        assertEquals(List.of(), jar.unifiedMultimodalRuntimeMissingProviderClasses());
        assertEquals(
                List.of("external.fixture.ExternalGemma4UnifiedRuntime"),
                jar.unifiedMultimodalRuntimeProviders());
    }

    @Test
    void pluginDirectoryInspectionReportsMissingUnifiedRuntimeProviderClass() throws Exception {
        Path pluginDirectory = tempDir.resolve("runtime-plugins");
        Path jarPath = pluginDirectory.resolve("missing-runtime-class.jar");
        writeJarEntry(
                jarPath,
                ExternalPluginClasspath.UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY,
                "external.fixture.MissingGemma4UnifiedRuntime" + System.lineSeparator());

        ExternalPluginClasspath.PluginDirectoryInspection inspection =
                ExternalPluginClasspath.inspectPluginDirectories(List.of(pluginDirectory.toString()));

        assertEquals(1, inspection.unifiedRuntimePluginCandidates());
        assertEquals(0, inspection.unifiedRuntimeReady());
        assertEquals(1, inspection.unifiedRuntimeNotReady());
        ExternalPluginClasspath.PluginDirectoryJarReport jar = inspection.jars().getFirst();
        assertEquals(
                List.of("external.fixture.MissingGemma4UnifiedRuntime"),
                jar.unifiedMultimodalRuntimeMissingProviderClasses());
        assertTrue(jar.unifiedRuntimeErrors().stream()
                .anyMatch(error -> error.contains("provider classes are missing")));
    }

    @Test
    void pluginDirectoryInspectionReportsUnifiedRuntimeServiceDescriptorErrors() throws Exception {
        Path pluginDirectory = tempDir.resolve("runtime-plugins");
        Path jarPath = pluginDirectory.resolve("empty-runtime.jar");
        writeJarEntry(
                jarPath,
                ExternalPluginClasspath.UNIFIED_MULTIMODAL_RUNTIME_SERVICE_ENTRY,
                """
                # providers intentionally omitted
                """);

        ExternalPluginClasspath.PluginDirectoryInspection inspection =
                ExternalPluginClasspath.inspectPluginDirectories(List.of(pluginDirectory.toString()));

        assertEquals(1, inspection.unifiedRuntimePluginCandidates());
        assertEquals(0, inspection.unifiedRuntimeReady());
        assertEquals(1, inspection.unifiedRuntimeNotReady());
        assertEquals(List.of(jarPath.toAbsolutePath().normalize()), inspection.unifiedRuntimeNotReadyJars());
        ExternalPluginClasspath.PluginDirectoryJarReport jar = inspection.jars().getFirst();
        assertFalse(jar.unifiedRuntimeReady());
        assertTrue(jar.unifiedRuntimeErrors().stream()
                .anyMatch(error -> error.contains("has no providers")));
    }

    @Test
    void persistentPluginDirectoryPropertyAddsClasspathEntries() throws Exception {
        Path pluginDirectory = tempDir.resolve("persistent-plugins");
        Path classesDirectory = pluginDirectory.resolve("external-plugin/classes");
        Files.createDirectories(classesDirectory.resolve("META-INF/services"));

        withSystemProperty(ExternalPluginClasspath.PROPERTY_PLUGIN_DIRS, pluginDirectory.toString(), () -> {
            try (ExternalPluginClasspathScope scope =
                    ExternalPluginClasspathScope.open(List.of(), ExternalPluginClasspathScopeTest.class)) {
                assertTrue(scope.active());
                assertEquals("scoped", scope.registryScope());
                assertTrue(scope.classpath().contains(classesDirectory.toAbsolutePath().normalize()));
            }
        });
    }

    @Test
    void pluginDirectoryReportSeparatesCommandConfiguredAndDefaultDirectories() throws Exception {
        Path fakeHome = tempDir.resolve("home");
        Path defaultDirectory = fakeHome.resolve(".aqli/plugins");
        Path commandDirectory = tempDir.resolve("command-plugins");
        Path configuredDirectory = tempDir.resolve("configured-plugins");
        Files.createDirectories(defaultDirectory);
        Files.createDirectories(commandDirectory);
        Files.createDirectories(configuredDirectory);

        withSystemProperty("user.home", fakeHome.toString(), () ->
                withSystemProperty(ExternalPluginClasspath.PROPERTY_PLUGIN_DIRS, configuredDirectory.toString(), () ->
                        withSystemProperty(
                                ExternalPluginClasspath.PROPERTY_PLUGIN_AUTOLOAD_DEFAULT_DIR,
                                "true",
                                () -> {
                                    ExternalPluginClasspath.PluginDirectoryReport report =
                                            ExternalPluginClasspath.pluginDirectoryReport(
                                                    List.of(commandDirectory.toString()));

                                    assertEquals(List.of(commandDirectory.toAbsolutePath().normalize()),
                                            report.commandDirectories());
                                    assertTrue(report.configuredDirectories()
                                            .contains(configuredDirectory.toAbsolutePath().normalize()));
                                    assertEquals(defaultDirectory.toAbsolutePath().normalize(),
                                            report.defaultDirectory());
                                    assertTrue(report.defaultDirectoryExists());
                                    assertTrue(report.defaultDirectoryAutoloadEnabled());
                                    assertTrue(report.defaultDirectoryActive());
                                    assertTrue(report.activeDirectories()
                                            .contains(commandDirectory.toAbsolutePath().normalize()));
                                    assertTrue(report.activeDirectories()
                                            .contains(configuredDirectory.toAbsolutePath().normalize()));
                                    assertTrue(report.activeDirectories()
                                            .contains(defaultDirectory.toAbsolutePath().normalize()));
                                })));
    }

    private static void withSystemProperty(String key, String value, ThrowingRunnable action) throws Exception {
        String previousValue = System.getProperty(key);
        try {
            System.setProperty(key, value);
            action.run();
        } finally {
            if (previousValue == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previousValue);
            }
        }
    }

    private static void writeJarEntry(Path jarPath, String entryName, String value) throws Exception {
        writeJarEntries(jarPath, Map.of(entryName, value));
    }

    private static void writeJarEntries(Path jarPath, Map<String, String> entries) throws Exception {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
