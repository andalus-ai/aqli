package tech.kayys.aqli.onnx.runner.tts.moss.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptimizedModelCacheFilesTest {
    @TempDir
    Path tempDir;

    @Test
    void removesOnlyAbandonedSidecarStageFilesWithinCacheScope() throws Exception {
        Path cacheRoot = Files.createDirectory(tempDir.resolve("cache"));
        Path nested = Files.createDirectory(cacheRoot.resolve("source-model"));
        Path rootStage = Files.writeString(cacheRoot.resolve(".moss-sidecar-abandoned.tmp"), "partial");
        Path nestedStage = Files.writeString(nested.resolve(".moss-sidecar-abandoned.tmp"), "partial");
        Path unrelated = Files.writeString(nested.resolve("ordinary.tmp"), "keep");
        Path similarlyNamed = Files.writeString(nested.resolve(".moss-sidecar-abandoned.tmp.bak"), "keep");

        int removed = OptimizedModelCacheFiles.cleanupAbandonedStages(cacheRoot);

        assertEquals(2, removed);
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(rootStage));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(nestedStage));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(unrelated));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(similarlyNamed));
    }

    @Test
    void replacesSameSizeButDifferentSidecarContents() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.data"), "fresh");
        Path target = Files.writeString(tempDir.resolve("target.data"), "stale");

        OptimizedModelCacheFiles.linkOrCopySidecar(source, target);

        assertEquals(-1L, Files.mismatch(source, target));
    }

    @Test
    void leavesIdenticalSidecarUntouched() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.data"), "same");
        Path target = Files.writeString(tempDir.resolve("target.data"), "same");
        var before = Files.getLastModifiedTime(target);

        OptimizedModelCacheFiles.linkOrCopySidecar(source, target);

        assertEquals(before, Files.getLastModifiedTime(target));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(target));
    }
}
