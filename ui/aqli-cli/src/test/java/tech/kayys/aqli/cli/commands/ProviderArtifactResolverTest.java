package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProviderArtifactResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesBestDirectTorchScriptArtifactFromDirectory() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("checkpoint"));
        Path weights = Files.createFile(root.resolve("weights.pth"));
        Path preferred = Files.createFile(root.resolve("model.pt"));

        assertEquals(Optional.of(preferred),
                ProviderArtifactResolver.resolveLibtorchArtifact(root));
        assertEquals(Optional.of(weights),
                ProviderArtifactResolver.resolveLibtorchArtifact(weights));
    }

    @Test
    void selectsRegisteredTorchScriptArtifactForSameModel() throws Exception {
        Path current = Files.createDirectory(tempDir.resolve("safetensors"));
        Path alternative = Files.createDirectory(tempDir.resolve("libtorchscript"));
        Path torchScript = Files.createFile(alternative.resolve("model.pt"));

        Optional<Path> result = ProviderArtifactResolver.findLibtorchArtifact(
                List.of(
                        new ProviderArtifactResolver.Artifact(
                                "org/model", "abc123", current.toString(), "safetensors"),
                        new ProviderArtifactResolver.Artifact(
                                "org/model", "abc123", alternative.toString(), "torchscript")),
                "abc123",
                current.toString());

        assertEquals(Optional.of(torchScript), result);
    }

    @Test
    void prefersTorchScriptFormatAndLibTorchScriptDirectory() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("registered"));
        Path generic = Files.createDirectory(root.resolve("weights"));
        Path specialized = Files.createDirectory(root.resolve("libtorchscript"));
        Files.createFile(generic.resolve("model.pt"));
        Path preferred = Files.createFile(specialized.resolve("model.bin"));

        Optional<Path> result = ProviderArtifactResolver.findLibtorchArtifact(
                List.of(
                        new ProviderArtifactResolver.Artifact(
                                "org/model", null, generic.toString(), "pytorch"),
                        new ProviderArtifactResolver.Artifact(
                                "org/model", null, specialized.toString(), "torchscript")),
                "org/model",
                tempDir.toString());

        assertEquals(Optional.of(preferred), result);
    }

    @Test
    void returnsEmptyWhenNoCompatibleAlternativeExists() throws Exception {
        Path current = Files.createDirectory(tempDir.resolve("current"));
        Path unrelated = Files.createDirectory(tempDir.resolve("unrelated"));
        Files.createFile(unrelated.resolve("model.safetensors"));

        assertTrue(ProviderArtifactResolver.findLibtorchArtifact(
                List.of(new ProviderArtifactResolver.Artifact(
                        "org/model", null, unrelated.toString(), "safetensors")),
                "org/model",
                current.toString()).isEmpty());
    }

    @Test
    void rejectsMissingAndInvalidArtifactPaths() {
        assertTrue(ProviderArtifactResolver.resolveLibtorchArtifact(
                tempDir.resolve("missing.pt")).isEmpty());
        assertTrue(ProviderArtifactResolver.findLibtorchArtifact(
                List.of(new ProviderArtifactResolver.Artifact(
                        "org/model", null, "\u0000invalid", "torchscript")),
                "org/model",
                null).isEmpty());
        assertTrue(ProviderArtifactResolver.findLibtorchArtifact(
                null, "org/model", null).isEmpty());
    }
}
