package tech.kayys.aqli.cli.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.aqli.plugin.core.ExtensionAvailability;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractReport;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityRegistry;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionAvailabilityExternalPluginFixtureTest {
    private static final String CLEAN_PROVIDER_ID = "external-tokenizer-fixture";
    private static final String BAD_PROVIDER_ID = "Bad External Fixture";
    private static final String SERVICE_DESCRIPTOR =
            "META-INF/services/tech.kayys.aqli.plugin.core.ExtensionAvailabilityProvider";
    private static final String UNIFIED_RUNTIME_SERVICE_DESCRIPTOR =
            "META-INF/services/tech.kayys.aqli.spi.multimodal.UnifiedMultimodalRuntime";

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void checkerLoadsExternalExtensionProviderFromPluginClassLoader() throws Exception {
        Path classesDir = compileExternalProvider(
                "external.fixture.ExternalTokenizerAvailabilityProvider",
                cleanProviderSource());
        writeServiceDescriptor(classesDir, "external.fixture.ExternalTokenizerAvailabilityProvider");

        try (URLClassLoader pluginClassLoader = externalPluginClassLoader(classesDir)) {
            ExtensionAvailabilityRegistry.global().unregister(CLEAN_PROVIDER_ID);

            List<ExtensionAvailability> reports =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityReports(pluginClassLoader);

            ExtensionAvailability availability = reports.stream()
                    .filter(report -> CLEAN_PROVIDER_ID.equals(report.id()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("tokenizer", availability.kind());
            assertEquals("ready", availability.status());
            assertTrue(availability.productionReady());
            assertTrue(availability.capabilities().contains("tokenizer"));
            assertTrue(ExtensionAvailabilityRegistry.global().availability(CLEAN_PROVIDER_ID).isEmpty());

            ExtensionAvailabilityContractReport contract =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityContractReport(pluginClassLoader);
            assertTrue(contract.passed());
            ExtensionAvailabilityGate gate =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityGate(pluginClassLoader);
            assertTrue(gate.passed());
            assertEquals("passed", gate.status());

            Map<String, Object> report = ExtensionAvailabilityGateReportWriter.buildReport(pluginClassLoader);
            assertEquals("scoped", report.get("extensionRegistryScope"));
            assertEquals("passed", ((Map<String, Object>) report.get("gate")).get("status"));
            assertTrue(report.get("extensions").toString().contains(CLEAN_PROVIDER_ID));

            Path reportFile = tempDir.resolve("external-extension-gate.json");
            ExtensionAvailabilityGateReportWriter.main(new String[] {
                    reportFile.toString(),
                    classesDir.toString()
            });
            JsonNode root = new ObjectMapper().readTree(reportFile.toFile());
            assertTrue(root.path("externalPluginClasspath").get(0).asText()
                    .contains(classesDir.getFileName().toString()));
            assertEquals("scoped", root.path("extensionRegistryScope").asText());
            assertTrue(root.path("extensions").toString().contains(CLEAN_PROVIDER_ID));

            ExtensionAvailabilityContractCheck.main(new String[] {
                    classesDir.toString()
            });
            assertTrue(ExtensionAvailabilityRegistry.global().availability(CLEAN_PROVIDER_ID).isEmpty());
        } finally {
            ExtensionAvailabilityRegistry.global().unregister(CLEAN_PROVIDER_ID);
        }
    }

    @Test
    void checkerReportsExternalExtensionContractFailureThroughGate() throws Exception {
        Path classesDir = compileExternalProvider(
                "external.fixture.BadExternalAvailabilityProvider",
                badProviderSource());
        writeServiceDescriptor(classesDir, "external.fixture.BadExternalAvailabilityProvider");

        try (URLClassLoader pluginClassLoader = externalPluginClassLoader(classesDir)) {
            ExtensionAvailabilityRegistry.global().unregister(BAD_PROVIDER_ID);

            ExtensionAvailabilityContractReport contract =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityContractReport(pluginClassLoader);
            assertFalse(contract.passed());
            assertTrue(contract.summaries().stream()
                    .anyMatch(summary -> summary.contains("provider_id_invalid")));
            assertTrue(ExtensionAvailabilityRegistry.global().contractViolations().stream()
                    .noneMatch(violation -> BAD_PROVIDER_ID.equals(violation.extensionId())));

            ExtensionAvailabilityGate gate =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityGate(pluginClassLoader);
            assertFalse(gate.passed());
            assertEquals("contract_failed", gate.status());
            assertTrue(gate.violations().stream()
                    .anyMatch(violation -> violation.contains("provider_id_invalid")));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> ExtensionAvailabilityContractCheck.main(new String[] {
                            classesDir.toString()
                    }));
            assertTrue(error.getMessage().contains("provider_id_invalid"));
            assertTrue(ExtensionAvailabilityRegistry.global().contractViolations().stream()
                    .noneMatch(violation -> BAD_PROVIDER_ID.equals(violation.extensionId())));
        } finally {
            ExtensionAvailabilityRegistry.global().unregister(BAD_PROVIDER_ID);
        }
    }

    @Test
    void checkerReportsExternalGemma4UnifiedRuntimeThroughBuiltInAvailabilityProvider() throws Exception {
        Path classesDir = compileExternalProvider(
                "external.fixture.ExternalGemma4UnifiedRuntime",
                gemma4UnifiedRuntimeSource());
        writeServiceDescriptor(
                classesDir,
                UNIFIED_RUNTIME_SERVICE_DESCRIPTOR,
                "external.fixture.ExternalGemma4UnifiedRuntime");

        try (URLClassLoader pluginClassLoader = externalPluginClassLoader(classesDir)) {
            List<ExtensionAvailability> reports =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityReports(pluginClassLoader);

            ExtensionAvailability availability = reports.stream()
                    .filter(report -> Gemma4UnifiedRuntimeAvailabilityProvider.ID.equals(report.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(availability.attached());
            assertFalse(availability.detached());
            assertTrue(availability.healthy());
            assertTrue(availability.productionReady());
            assertEquals("ready", availability.status());
            assertEquals("external-gemma4-unified-fixture", availability.attributes().get("runtimeId"));
            assertEquals("gemma4_unified", availability.attributes().get("modelType"));
            assertEquals("text,image,audio,video", availability.attributes().get("inputModalities"));
            assertTrue(availability.capabilities().contains("runtime:external-gemma4-unified-fixture"));

            ExtensionAvailabilityContractReport contract =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityContractReport(pluginClassLoader);
            assertTrue(contract.passed());
        }
    }

    @Test
    void checkerReportsInvalidExternalGemma4UnifiedRuntimeManifest() throws Exception {
        Path classesDir = compileExternalProvider(
                "external.fixture.InvalidGemma4UnifiedRuntime",
                invalidGemma4UnifiedRuntimeSource());
        writeServiceDescriptor(
                classesDir,
                UNIFIED_RUNTIME_SERVICE_DESCRIPTOR,
                "external.fixture.InvalidGemma4UnifiedRuntime");

        try (URLClassLoader pluginClassLoader = externalPluginClassLoader(classesDir)) {
            List<ExtensionAvailability> reports =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityReports(pluginClassLoader);

            ExtensionAvailability availability = reports.stream()
                    .filter(report -> Gemma4UnifiedRuntimeAvailabilityProvider.ID.equals(report.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(availability.attached());
            assertFalse(availability.detached());
            assertFalse(availability.healthy());
            assertFalse(availability.productionReady());
            assertEquals("invalid", availability.status());
            assertEquals("invalid-gemma4-runtime", availability.attributes().get("runtimeId"));
            assertTrue(availability.diagnostics().contains("missing_input_modalities"));
            assertTrue(availability.diagnostics().contains("production_ready_without_text"));
            assertTrue(availability.attributes().get("manifestViolations").contains("missing_input_modalities"));

            ExtensionAvailabilityContractReport contract =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityContractReport(pluginClassLoader);
            assertTrue(contract.passed());
        }
    }

    @Test
    void checkerReportsConflictingExternalGemma4UnifiedRuntimeProviders() throws Exception {
        Path classesDir = compileExternalProviders(
                "conflicting-gemma4-unified-runtime",
                Map.of(
                        "external.fixture.FirstGemma4UnifiedRuntime",
                        gemma4UnifiedRuntimeSource(
                                "FirstGemma4UnifiedRuntime",
                                "first-gemma4-unified-fixture"),
                        "external.fixture.SecondGemma4UnifiedRuntime",
                        gemma4UnifiedRuntimeSource(
                                "SecondGemma4UnifiedRuntime",
                                "second-gemma4-unified-fixture")));
        writeServiceDescriptor(
                classesDir,
                UNIFIED_RUNTIME_SERVICE_DESCRIPTOR,
                "external.fixture.FirstGemma4UnifiedRuntime"
                        + System.lineSeparator()
                        + "external.fixture.SecondGemma4UnifiedRuntime");

        try (URLClassLoader pluginClassLoader = externalPluginClassLoader(classesDir)) {
            List<ExtensionAvailability> reports =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityReports(pluginClassLoader);

            ExtensionAvailability availability = reports.stream()
                    .filter(report -> Gemma4UnifiedRuntimeAvailabilityProvider.ID.equals(report.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(availability.attached());
            assertFalse(availability.detached());
            assertFalse(availability.healthy());
            assertFalse(availability.productionReady());
            assertEquals("conflict", availability.status());
            assertEquals("true", availability.attributes().get("runtimeConflict"));
            assertEquals("2", availability.attributes().get("runtimeConflictCount"));
            assertTrue(availability.attributes().get("runtimeIds").contains("first-gemma4-unified-fixture"));
            assertTrue(availability.attributes().get("runtimeIds").contains("second-gemma4-unified-fixture"));
            assertTrue(availability.diagnostics().contains("Multiple unified runtimes claim gemma4_unified"));
        }
    }

    @Test
    void contractCheckRejectsMissingExternalExtensionClasspath() {
        Path missingPlugin = tempDir.resolve("missing-extension.jar");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ExtensionAvailabilityContractCheck.main(new String[] {
                        missingPlugin.toString()
                }));
        assertTrue(error.getMessage().contains("does not exist"));
    }

    private Path compileExternalProvider(String className, String source) throws Exception {
        return compileExternalProviders(className, Map.of(className, source));
    }

    private Path compileExternalProviders(String fixtureId, Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "Java compiler must be available for external plugin fixture compilation");

        Path sourceRoot = tempDir.resolve("src-" + fixtureId.toLowerCase(Locale.ROOT).replace('.', '-'));
        Path classesDir = tempDir.resolve("classes-" + fixtureId.toLowerCase(Locale.ROOT).replace('.', '-'));
        Files.createDirectories(classesDir);
        List<Path> sourceFiles = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path sourceFile = sourceRoot.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, entry.getValue(), StandardCharsets.UTF_8);
            sourceFiles.add(sourceFile);
        }

        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(sourceFiles.stream()
                            .map(Path::toFile)
                            .toList());
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDir.toString());
            Boolean compiled = compiler.getTask(null, fileManager, null, options, null, units).call();
            assertTrue(Boolean.TRUE.equals(compiled), "external plugin fixture should compile: " + fixtureId);
        }
        return classesDir;
    }

    private void writeServiceDescriptor(Path classesDir, String implementationClass) throws Exception {
        writeServiceDescriptor(classesDir, SERVICE_DESCRIPTOR, implementationClass);
    }

    private void writeServiceDescriptor(Path classesDir, String serviceDescriptor, String implementationClass)
            throws Exception {
        Path serviceFile = classesDir.resolve(serviceDescriptor);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, implementationClass + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private URLClassLoader externalPluginClassLoader(Path classesDir) throws Exception {
        URL[] urls = { classesDir.toUri().toURL() };
        return new URLClassLoader(urls, ExtensionAvailabilityExternalPluginFixtureTest.class.getClassLoader());
    }

    private static String cleanProviderSource() {
        return """
                package external.fixture;

                import java.util.List;
                import java.util.Map;
                import tech.kayys.aqli.plugin.core.ExtensionAvailability;
                import tech.kayys.aqli.plugin.core.ExtensionAvailabilityProvider;

                public final class ExternalTokenizerAvailabilityProvider implements ExtensionAvailabilityProvider {
                    @Override
                    public String extensionId() {
                        return "external-tokenizer-fixture";
                    }

                    @Override
                    public String extensionName() {
                        return "External Tokenizer Fixture";
                    }

                    @Override
                    public String extensionKind() {
                        return "tokenizer";
                    }

                    @Override
                    public ExtensionAvailability availability() {
                        return new ExtensionAvailability(
                                extensionId(),
                                extensionName(),
                                extensionKind(),
                                true,
                                false,
                                true,
                                true,
                                "ready",
                                List.of("tokenizer"),
                                List.of("sentencepiece"),
                                Map.of("source", "external-plugin-fixture"),
                                "external fixture ready",
                                List.of());
                    }
                }
                """;
    }

    private static String badProviderSource() {
        return """
                package external.fixture;

                import java.util.List;
                import java.util.Map;
                import tech.kayys.aqli.plugin.core.ExtensionAvailability;
                import tech.kayys.aqli.plugin.core.ExtensionAvailabilityProvider;

                public final class BadExternalAvailabilityProvider implements ExtensionAvailabilityProvider {
                    @Override
                    public String extensionId() {
                        return "Bad External Fixture";
                    }

                    @Override
                    public String extensionName() {
                        return "Bad External Fixture";
                    }

                    @Override
                    public String extensionKind() {
                        return "Tokenizer";
                    }

                    @Override
                    public ExtensionAvailability availability() {
                        return new ExtensionAvailability(
                                "different-external-fixture",
                                extensionName(),
                                "tokenizer",
                                true,
                                false,
                                true,
                                false,
                                "fallback",
                                List.of("tokenizer"),
                                List.of("wordpiece"),
                                Map.of("source", "bad-external-plugin-fixture"),
                                "external fixture has bad provider metadata",
                                List.of());
                    }
                }
                """;
    }

    private static String gemma4UnifiedRuntimeSource() {
        return gemma4UnifiedRuntimeSource("ExternalGemma4UnifiedRuntime", "external-gemma4-unified-fixture");
    }

    private static String gemma4UnifiedRuntimeSource(String className, String runtimeId) {
        return """
                package external.fixture;

                import java.util.List;
                import java.util.Map;
                import tech.kayys.aqli.spi.multimodal.UnifiedInputModality;
                import tech.kayys.aqli.spi.multimodal.UnifiedMultimodalRuntime;
                import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifest;
                import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeReadiness;

                public final class %s implements UnifiedMultimodalRuntime {
                    @Override
                    public UnifiedRuntimeManifest manifest() {
                        return new UnifiedRuntimeManifest(
                                "%s",
                                "External Gemma 4 Unified Fixture",
                                List.of("gemma4"),
                                List.of("gemma4_unified"),
                                List.of(
                                        UnifiedInputModality.TEXT,
                                        UnifiedInputModality.IMAGE,
                                        UnifiedInputModality.AUDIO,
                                        UnifiedInputModality.VIDEO),
                                UnifiedRuntimeReadiness.READY,
                                "fixture runtime ready",
                                List.of("processor_config.json", "image_processor_config.json"),
                                List.of("tokenizer.json", "tokenizer.model"),
                                Map.of("source", "external-runtime-fixture"));
                    }
                }
                """.formatted(className, runtimeId);
    }

    private static String invalidGemma4UnifiedRuntimeSource() {
        return """
                package external.fixture;

                import java.util.List;
                import java.util.Map;
                import tech.kayys.aqli.spi.multimodal.UnifiedMultimodalRuntime;
                import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeManifest;
                import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeReadiness;

                public final class InvalidGemma4UnifiedRuntime implements UnifiedMultimodalRuntime {
                    @Override
                    public UnifiedRuntimeManifest manifest() {
                        return new UnifiedRuntimeManifest(
                                "invalid-gemma4-runtime",
                                "Invalid Gemma 4 Runtime",
                                List.of("gemma4"),
                                List.of("gemma4_unified"),
                                List.of(),
                                UnifiedRuntimeReadiness.READY,
                                "claims ready without modalities",
                                List.of(),
                                List.of("tokenizer.json"),
                                Map.of("source", "invalid-runtime-fixture"));
                    }
                }
                """;
    }
}
