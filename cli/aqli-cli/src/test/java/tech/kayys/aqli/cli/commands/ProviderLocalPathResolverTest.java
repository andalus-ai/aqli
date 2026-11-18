package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderLocalPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void leavesNonLibTorchProviderPathUnchanged() {
        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ProviderLocalPathResolver.Result result = ProviderLocalPathResolver.resolve(
                null, "cpu", "model", "/models/model", value -> false, value -> false,
                output::add, errors::add);

        assertTrue(result.ok());
        assertEquals("/models/model", result.localPath());
        assertTrue(output.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void resolvesDirectTorchScriptArtifactForLibTorch() throws Exception {
        Path artifact = Files.createFile(tempDir.resolve("model.pt"));
        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ProviderLocalPathResolver.Result result = ProviderLocalPathResolver.resolve(
                null, "libtorch", "model", artifact.toString(), value -> false, value -> false,
                output::add, errors::add);

        assertTrue(result.ok());
        assertEquals(artifact.toAbsolutePath().normalize().toString(), result.localPath());
        assertTrue(output.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void allowsSafetensorBridgeWhenNoTorchScriptArtifactExists() throws Exception {
        Path checkpoint = Files.createDirectory(tempDir.resolve("checkpoint"));
        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ProviderLocalPathResolver.Result result = ProviderLocalPathResolver.resolve(
                null, "libtorch", "model", checkpoint.toString(),
                value -> value.equals(checkpoint.toString()), value -> false,
                output::add, errors::add);

        assertTrue(result.ok());
        assertEquals(checkpoint.toString(), result.localPath());
        assertEquals(1, output.size());
        assertTrue(output.getFirst().contains("safetensor bridge mode"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void rejectsUnsupportedLibTorchArtifact() {
        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        ProviderLocalPathResolver.Result result = ProviderLocalPathResolver.resolve(
                null, "libtorch", "model", "/models/unsupported",
                value -> false, value -> false, output::add, errors::add);

        assertFalse(result.ok());
        assertEquals("/models/unsupported", result.localPath());
        assertEquals(2, errors.size());
    }
}
