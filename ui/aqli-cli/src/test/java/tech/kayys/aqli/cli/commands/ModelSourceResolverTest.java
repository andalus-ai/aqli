package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelSourceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesExistingLocalFilesAndDirectories() throws Exception {
        Path model = Files.createFile(tempDir.resolve("model.onnx"));
        ModelSourceResolver.Source file = ModelSourceResolver.classify(model.toString());
        assertEquals(ModelSourceResolver.Kind.LOCAL_FILE, file.kind());
        assertEquals(model.toAbsolutePath().normalize(), file.localPath());

        ModelSourceResolver.Source directory = ModelSourceResolver.classify(tempDir.toString());
        assertEquals(ModelSourceResolver.Kind.LOCAL_DIRECTORY, directory.kind());
        assertEquals(tempDir.toAbsolutePath().normalize(), directory.localPath());
    }

    @Test
    void classifiesHuggingFaceReferencesWithoutResolvingThem() {
        assertEquals("org/model", ModelSourceResolver.huggingFaceRepositoryId("org/model"));
        assertEquals("org/model", ModelSourceResolver.huggingFaceRepositoryId("hf:org/model"));
        assertEquals("org/model", ModelSourceResolver.huggingFaceRepositoryId("huggingface:org/model"));
        assertEquals(ModelSourceResolver.Kind.HUGGING_FACE_REPOSITORY,
                ModelSourceResolver.classify("hf:org/model").kind());
    }

    @Test
    void rejectsValuesThatAreNotRepositoryIdentifiers() {
        assertNull(ModelSourceResolver.huggingFaceRepositoryId(null));
        assertNull(ModelSourceResolver.huggingFaceRepositoryId("model"));
        assertNull(ModelSourceResolver.huggingFaceRepositoryId("/models/model"));
        assertNull(ModelSourceResolver.huggingFaceRepositoryId("./models/model"));
        assertNull(ModelSourceResolver.huggingFaceRepositoryId("https://huggingface.co/org/model"));
        assertNull(ModelSourceResolver.huggingFaceRepositoryId("org/model/subfolder"));
    }

    @Test
    void leavesUnknownReferencesUnresolved() {
        ModelSourceResolver.Source source = ModelSourceResolver.classify("custom:model-reference");
        assertEquals(ModelSourceResolver.Kind.UNRESOLVED_REFERENCE, source.kind());
        assertEquals("custom:model-reference", source.reference());
        assertNull(source.localPath());
    }
}
