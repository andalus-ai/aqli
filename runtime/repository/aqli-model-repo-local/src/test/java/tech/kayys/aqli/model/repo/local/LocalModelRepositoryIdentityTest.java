package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalModelRepositoryIdentityTest {

    @Test
    void derivesOwnerAndRepositoryForConventionalModelFiles() {
        Path relative = Path.of("OpenMOSS-Team", "MOSS-TTS-Nano-100M-ONNX", "model.onnx");
        Path file = Path.of("/models/onnx").resolve(relative);

        assertEquals("OpenMOSS-Team/MOSS-TTS-Nano-100M-ONNX",
                LocalModelRepositoryIdentity.modelId(relative, file));
        assertEquals("MOSS-TTS-Nano-100M-ONNX",
                LocalModelRepositoryIdentity.displayName(relative, file));
        assertTrue(LocalModelRepositoryIdentity.aggregateRepositoryFile(relative, file));
    }

    @Test
    void preservesLegacyOwnerAndFileIdentityForNonConventionalNestedFiles() {
        Path relative = Path.of("owner", "repository", "weights.gguf");
        Path file = Path.of("/models/gguf").resolve(relative);

        assertEquals("owner/weights.gguf", LocalModelRepositoryIdentity.modelId(relative, file));
        assertEquals("weights.gguf", LocalModelRepositoryIdentity.displayName(relative, file));
        assertFalse(LocalModelRepositoryIdentity.aggregateRepositoryFile(relative, file));
    }

    @Test
    void derivesDisplayNameFromConventionalModelFileInShallowLayout() {
        Path relative = Path.of("model.safetensors");
        Path file = Path.of("/models/safetensors/repository/model.safetensors");

        assertEquals("model.safetensors", LocalModelRepositoryIdentity.modelId(relative, file));
        assertEquals("repository", LocalModelRepositoryIdentity.displayName(relative, file));
        assertFalse(LocalModelRepositoryIdentity.aggregateRepositoryFile(relative, file));
    }

    @Test
    void keepsNonRepositoryFilesAtTheirOriginalPathDepth() {
        Path relative = Path.of("owner", "repository", "weights.bin");
        Path file = Path.of("/models/bin").resolve(relative);

        assertEquals("owner/weights.bin", LocalModelRepositoryIdentity.modelId(relative, file));
        assertFalse(LocalModelRepositoryIdentity.aggregateRepositoryFile(relative, file));
    }

    @Test
    void handlesNullRelativePath() {
        assertEquals("", LocalModelRepositoryIdentity.modelId(null, Path.of("model.gguf")));
    }
}
