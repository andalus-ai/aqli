package tech.kayys.aqli.onnx.runner.tts.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsCacheSettings;

class MossTtsOnnxRunnerCacheIdentityTest {
    private static MossTtsCacheSettings settings(boolean enabled, Path root, String logLevel) {
        return new MossTtsCacheSettings(enabled, root, logLevel, true, 2, true, 4);
    }

    @Test
    void sessionCacheIdentityIncludesOptimizedGraphCacheEnablement() {
        Path root = Path.of("cache", "onnx");
        assertNotEquals(
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, root, "error")),
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(false, root, "error")));
    }

    @Test
    void sessionCacheIdentityIncludesNormalizedCacheRoot() {
        assertEquals(
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, Path.of("cache", "onnx"), "error")),
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(
                        settings(true, Path.of("cache", ".", "onnx"), "error")));
        assertNotEquals(
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, Path.of("cache-a"), "error")),
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, Path.of("cache-b"), "error")));
    }

    @Test
    void sessionCacheIdentityIncludesOnnxRuntimeLogLevel() {
        assertNotEquals(
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, Path.of("cache"), "error")),
                MossTtsOnnxRunner.sessionCacheConfigurationIdentity(settings(true, Path.of("cache"), "verbose")));
    }
}
