package tech.kayys.aqli.onnx.runner.tts.moss.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MossAudioTokenizerModelLocatorTest {
    @TempDir
    Path tempDir;

    @Test
    void prefersIndexedCodecLocation() throws Exception {
        Path indexed = Files.createDirectories(tempDir.resolve("indexed"));
        Files.writeString(indexed.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");
        Files.writeString(tempDir.resolve("other")
                .resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");

        assertEquals(indexed.toAbsolutePath().normalize(),
                MossAudioTokenizerModelLocator.findInstalled(tempDir, List.of(indexed), "org/codec").orElseThrow());
    }

    @Test
    void prefersOfficialRepositoryDuringCacheScan() throws Exception {
        Path other = Files.createDirectories(tempDir.resolve("a-other-codec"));
        Files.writeString(other.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");
        Path official = Files.createDirectories(tempDir.resolve("OpenMOSS-Team")
                .resolve("MOSS-Audio-Tokenizer-Nano-ONNX"));
        Files.writeString(official.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");

        assertEquals(official.toAbsolutePath().normalize(),
                MossAudioTokenizerModelLocator.findInstalled(
                        tempDir, List.of(), "OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX").orElseThrow());
    }

    @Test
    void returnsFirstSortedCodecWhenNoOfficialRepositoryMatches() throws Exception {
        Path second = Files.createDirectories(tempDir.resolve("z-codec"));
        Files.writeString(second.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");
        Path first = Files.createDirectories(tempDir.resolve("a-codec"));
        Files.writeString(first.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME), "{}");

        assertEquals(first.toAbsolutePath().normalize(),
                MossAudioTokenizerModelLocator.findInstalled(tempDir, List.of(), "missing/repository").orElseThrow());
    }

    @Test
    void returnsEmptyWhenNoCodecIsInstalled() {
        assertTrue(MossAudioTokenizerModelLocator.findInstalled(
                tempDir.resolve("missing"), List.of(), "org/codec").isEmpty());
        assertTrue(MossAudioTokenizerModelLocator.findInstalled(
                null, null, "org/codec").isEmpty());
    }
}
