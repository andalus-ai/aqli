package tech.kayys.aqli.onnx.runner.tts.moss.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MossTtsModelDiagnosticsTest {
    @TempDir Path tempDir;

    @Test
    void inspectsVoiceInventoryAndDefaultVoice() throws Exception {
        Files.writeString(tempDir.resolve("browser_poc_manifest.json"), """
                {"builtin_voices":[
                  {"voice":"en_female_1","display_name":"English Female","group":"english female"},
                  {"voice":"jp_male_1","display_name":"Japanese Male","group":"japanese male"}
                ]}
                """);

        var snapshot = MossTtsModelDiagnostics.inspect(tempDir);

        assertTrue(snapshot.manifestAvailable());
        assertEquals(2, snapshot.voiceCount());
        assertEquals(1, snapshot.voiceGroups().get("en female"));
        assertEquals(1, snapshot.voiceGroups().get("jp male"));
        assertEquals("en_female_1", snapshot.defaultVoice().id());
        assertEquals("English Female", snapshot.defaultVoice().displayName());
        assertNull(snapshot.manifestError());
    }

    @Test
    void reportsMissingManifestWithoutThrowing() {
        var snapshot = MossTtsModelDiagnostics.inspect(tempDir);
        assertFalse(snapshot.manifestAvailable());
        assertEquals(0, snapshot.voiceCount());
        assertNull(snapshot.defaultVoice());
        assertTrue(snapshot.manifestError().contains("manifest is missing"));
    }

    @Test
    void reportsEmptyVoiceCatalog() throws Exception {
        Files.writeString(tempDir.resolve("browser_poc_manifest.json"), "{\"builtin_voices\":[]}");
        var snapshot = MossTtsModelDiagnostics.inspect(tempDir);
        assertTrue(snapshot.manifestAvailable());
        assertEquals(0, snapshot.voiceCount());
        assertNull(snapshot.defaultVoice());
        assertTrue(snapshot.manifestError().contains("builtin_voices"));
    }
}
