package tech.kayys.aqli.cli.commands.features.install;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureInstallLockStoreTest {
    @TempDir Path tempDir;

    @Test
    void writesCanonicalAndCompatibilityFields() throws Exception {
        FeatureInstallLockStore.put(tempDir, tempDir.resolve("demo.jar"),
                Map.of("sha256", "abc", "size_bytes", 3), Map.of("version", "test"));
        Map<?, ?> json = new ObjectMapper().readValue(FeatureInstallLockStore.path(tempDir).toFile(), Map.class);
        assertEquals(json.get("extensions"), json.get("features"));
        assertEquals("abc", FeatureInstallLockStore.read(tempDir).entries().get("demo.jar").get("sha256"));
        assertEquals("test", ((Map<?, ?>) json.get("runtime")).get("version"));
    }

    @Test
    void prefersCanonicalLockAndReadsLegacyFilename() throws Exception {
        Path legacy = tempDir.resolve(FeatureInstallLockStore.LEGACY_LOCKFILE);
        Files.writeString(legacy, "{\"features\":{\"legacy.jar\":{\"sha256\":\"legacy\"}}}");
        assertEquals(legacy, FeatureInstallLockStore.readPath(tempDir));
        assertEquals("legacy", FeatureInstallLockStore.read(tempDir).entries().get("legacy.jar").get("sha256"));
        Files.writeString(FeatureInstallLockStore.path(tempDir),
                "{\"extensions\":{\"current.jar\":{\"sha256\":\"current\"}}}");
        assertEquals(FeatureInstallLockStore.path(tempDir), FeatureInstallLockStore.readPath(tempDir));
        assertTrue(FeatureInstallLockStore.read(tempDir).entries().containsKey("current.jar"));
    }

    @Test
    void removePreservesOtherEntries() throws Exception {
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        entries.put("keep.jar", Map.of("sha256", "keep"));
        entries.put("remove.jar", Map.of("sha256", "remove"));
        FeatureInstallLockStore.write(tempDir, entries, Map.of());
        FeatureInstallLockStore.remove(tempDir, tempDir.resolve("remove.jar"), Map.of());
        assertTrue(FeatureInstallLockStore.read(tempDir).entries().containsKey("keep.jar"));
        assertFalse(FeatureInstallLockStore.read(tempDir).entries().containsKey("remove.jar"));
    }

    @Test
    void malformedLockProducesDiagnosticState() throws Exception {
        Files.writeString(FeatureInstallLockStore.path(tempDir), "{not-json");
        FeatureInstallLockStore.State state = FeatureInstallLockStore.read(tempDir);
        assertTrue(state.entries().isEmpty());
        assertTrue(state.error().contains("not readable"));
    }
}
