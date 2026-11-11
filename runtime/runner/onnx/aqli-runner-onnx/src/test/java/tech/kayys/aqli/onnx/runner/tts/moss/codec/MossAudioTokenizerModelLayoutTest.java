package tech.kayys.aqli.onnx.runner.tts.moss.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossAudioTokenizerModelLayoutTest {
    @TempDir
    Path tempDir;

    @Test
    void recognizesCodecDirectoryAndMetadataFile() throws Exception {
        Path codec = Files.createDirectory(tempDir.resolve("codec"));
        Path metadata = Files.writeString(codec.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");

        assertTrue(MossAudioTokenizerModelLayout.isModelDirectory(codec));
        assertEquals(codec, MossAudioTokenizerModelLayout.directoryFrom(codec).orElseThrow());
        assertEquals(codec, MossAudioTokenizerModelLayout.directoryFrom(metadata).orElseThrow());
    }

    @Test
    void resolvesDirectoryFromGraphFileAndNestedRepositoryPath() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("repo").resolve("assets").resolve("codec"));
        Files.writeString(nested.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");
        Path graph = Files.writeString(nested.resolve("decode.onnx"), "onnx");

        assertEquals(nested, MossAudioTokenizerModelLayout.directoryFrom(graph).orElseThrow());
        assertEquals(nested, MossAudioTokenizerModelLayout.directoryFrom(tempDir.resolve("repo")).orElseThrow());
    }

    @Test
    void matchesOfficialRepositoryPathAfterNormalization() throws Exception {
        Path official = Files.createDirectories(
                tempDir.resolve("OpenMOSS-Team").resolve("MOSS-Audio-Tokenizer-Nano-ONNX"));
        assertTrue(MossAudioTokenizerModelLayout.looksLikeRepository(
                official, "OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX"));
        assertFalse(MossAudioTokenizerModelLayout.looksLikeRepository(
                tempDir.resolve("other-codec"), "OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX"));
    }

    @Test
    void rejectsMissingMetadataAndNullPaths() throws Exception {
        Path incomplete = Files.createDirectory(tempDir.resolve("incomplete"));
        assertFalse(MossAudioTokenizerModelLayout.isModelDirectory(incomplete));
        assertTrue(MossAudioTokenizerModelLayout.directoryFrom(incomplete).isEmpty());
        assertTrue(MossAudioTokenizerModelLayout.directoryFrom(null).isEmpty());
    }
}
