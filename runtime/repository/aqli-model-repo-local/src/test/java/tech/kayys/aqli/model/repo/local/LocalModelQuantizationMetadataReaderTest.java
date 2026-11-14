package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModelQuantizationMetadataReaderTest {

    @TempDir
    Path tempDir;

    private final LocalModelQuantizationMetadataReader reader =
            new LocalModelQuantizationMetadataReader();

    @Test
    void readsAqliQuantizationSidecar() throws Exception {
        Files.writeString(tempDir.resolve("aqli_quant.json"), """
                {
                  "aqli_quant": {
                    "strategy": "gptq",
                    "bits": 4,
                    "group_size": 128,
                    "source_model": "org/model"
                  }
                }
                """);

        var metadata = reader.read(tempDir).orElseThrow();

        assertEquals("gptq", metadata.strategy());
        assertEquals(4, metadata.bits());
        assertEquals(128, metadata.groupSize());
        assertEquals("org/model", metadata.sourceModel());
    }

    @Test
    void returnsEmptyWhenSidecarIsMissingOrMalformed() throws Exception {
        assertTrue(reader.read(tempDir).isEmpty());
        Files.writeString(tempDir.resolve("aqli_quant.json"), "{not-json");
        assertTrue(reader.read(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyWhenAqliSectionIsNotAnObject() throws Exception {
        Files.writeString(tempDir.resolve("aqli_quant.json"), """
                {"aqli_quant": "not-an-object"}
                """);

        assertTrue(reader.read(tempDir).isEmpty());
    }

    @Test
    void ignoresFieldsWithUnexpectedTypes() throws Exception {
        Files.writeString(tempDir.resolve("aqli_quant.json"), """
                {
                  "aqli_quant": {
                    "strategy": 4,
                    "bits": "four",
                    "group_size": 128.5,
                    "source_model": false
                  }
                }
                """);

        var metadata = reader.read(tempDir).orElseThrow();

        assertNull(metadata.strategy());
        assertEquals(0, metadata.bits());
        assertEquals(0, metadata.groupSize());
        assertNull(metadata.sourceModel());
    }
}
