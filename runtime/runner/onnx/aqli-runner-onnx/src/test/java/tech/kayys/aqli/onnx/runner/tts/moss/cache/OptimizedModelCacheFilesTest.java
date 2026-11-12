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
