package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplicitModelInputResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesExplicitModelFileAndInfersFormatAndProvider() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("model.gguf"));

        ExplicitModelInputResolver.Result result = ExplicitModelInputResolver.resolve(
                new ExplicitModelInputResolver.Options(
                        model.toString(), null, null, false, false, false, null, null),
                null);

        assertTrue(result.successful());
        assertTrue(result.resolved());
        assertEquals(model.toAbsolutePath().toString(), result.modelId());
        assertEquals(model.toAbsolutePath().toString(), result.localPath());
        assertEquals("gguf", result.format());
        assertEquals("gguf", result.providerId());
        assertEquals(1, result.messages().size());
    }

    @Test
    void resolvesExplicitModelDirectoryAndUsesSafetensorFallback() throws Exception {
        Path modelDirectory = Files.createDirectory(temporaryDirectory.resolve("model"));

        ExplicitModelInputResolver.Result result = ExplicitModelInputResolver.resolve(
                new ExplicitModelInputResolver.Options(
                        null, modelDirectory.toString(), null, false, false, false, null, null),
                null);

        assertTrue(result.successful());
        assertTrue(result.resolved());
        assertEquals(modelDirectory.toAbsolutePath().toString(), result.modelId());
        assertEquals("safetensor", result.providerId());
    }

    @Test
    void reportsMissingExplicitModelPathWithoutResolving() {
        ExplicitModelInputResolver.Result result = ExplicitModelInputResolver.resolve(
                new ExplicitModelInputResolver.Options(
                        null, null, temporaryDirectory.resolve("missing.gguf").toString(),
                        false, false, false, null, null),
                null);

        assertFalse(result.successful());
        assertFalse(result.resolved());
        assertTrue(result.error().contains("Model file not found"));
    }

    @Test
    void preservesExplicitProviderAndFormat() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("model.gguf"));

        ExplicitModelInputResolver.Result result = ExplicitModelInputResolver.resolve(
                new ExplicitModelInputResolver.Options(
                        model.toString(), null, null, false, false, true, "custom", "custom-format"),
                null);

        assertEquals("custom", result.providerId());
        assertEquals("custom-format", result.format());
    }
}
