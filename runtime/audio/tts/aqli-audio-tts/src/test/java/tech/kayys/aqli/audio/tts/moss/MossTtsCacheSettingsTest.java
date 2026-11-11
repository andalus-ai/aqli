package tech.kayys.aqli.audio.tts.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MossTtsCacheSettingsTest {
    @Test
    void defaultsMatchExistingCacheConfiguration() {
        var settings = MossTtsCacheSettings.from(Map.of(), Map.of(), "/home/test");
        assertTrue(settings.optimizedGraphCacheEnabled());
        assertEquals(Path.of("/home/test/.aqli/cache/onnx/optimized/moss-tts"), settings.optimizedGraphCacheRoot());
        assertEquals("error", settings.onnxRuntimeLogLevel());
        assertTrue(settings.sessionCacheEnabled());
        assertEquals(2, settings.sessionCacheMaxEntries());
        assertTrue(settings.assetCacheEnabled());
        assertEquals(4, settings.assetCacheMaxEntries());
    }

    @Test
    void systemPropertiesTakePrecedenceOverEnvironment() {
        var settings = MossTtsCacheSettings.from(
                Map.of(
                        "aqli.onnx.optimized-model-cache.enabled", "false",
                        "aqli.onnx.optimized-model-cache.dir", "/tmp/aqli-cache",
                        "aqli.onnx.moss-tts.session-cache.max-entries", "7",
                        "aqli.onnx.log-level", "WARNING"),
                Map.of(
                        "AQLI_ONNX_OPTIMIZED_MODEL_CACHE", "true",
                        "AQLI_ONNX_OPTIMIZED_MODEL_CACHE_DIR", "/var/cache",
                        "AQLI_ONNX_MOSS_TTS_SESSION_CACHE_MAX_ENTRIES", "3",
                        "AQLI_ONNX_LOG_LEVEL", "fatal"),
                "/home/test");
        assertFalse(settings.optimizedGraphCacheEnabled());
        assertEquals(Path.of("/tmp/aqli-cache/moss-tts"), settings.optimizedGraphCacheRoot());
        assertEquals(7, settings.sessionCacheMaxEntries());
        assertEquals("warning", settings.onnxRuntimeLogLevel());
    }

    @Test
    void disabledTokensAndInvalidValuesPreserveFallbackBehavior() {
        var settings = MossTtsCacheSettings.from(
                Map.of(
                        "aqli.onnx.moss-tts.asset-cache.enabled", "OFF",
                        "aqli.onnx.moss-tts.asset-cache.max-entries", "invalid",
                        "aqli.onnx.moss-tts.session-cache.max-entries", "-9",
                        "aqli.onnx.log-level", "trace"),
                Map.of(), "/home/test");
        assertFalse(settings.assetCacheEnabled());
        assertEquals(4, settings.assetCacheMaxEntries());
        assertEquals(0, settings.sessionCacheMaxEntries());
        assertEquals("error", settings.onnxRuntimeLogLevel());
    }

    @Test
    void environmentValuesAreUsedWhenPropertiesAreAbsent() {
        var settings = MossTtsCacheSettings.from(
                Map.of(),
                Map.of(
                        "AQLI_ONNX_OPTIMIZED_MODEL_CACHE", "no",
                        "AQLI_ONNX_MOSS_TTS_ASSET_CACHE", "0",
                        "AQLI_ONNX_MOSS_TTS_ASSET_CACHE_MAX_ENTRIES", "8"),
                "/home/test");
        assertFalse(settings.optimizedGraphCacheEnabled());
        assertFalse(settings.assetCacheEnabled());
        assertEquals(8, settings.assetCacheMaxEntries());
    }
}
