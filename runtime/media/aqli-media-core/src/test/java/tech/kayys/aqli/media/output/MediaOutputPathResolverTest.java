package tech.kayys.aqli.media.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MediaOutputPathResolverTest {
    @Test
    void defaultsOutputNameFromProviderExtension() {
        var result = MediaOutputPathResolver.resolve(new MediaOutputPathResolver.Request(null, null, ".FLAC", true));
        assertEquals(Path.of("output.flac"), result.path());
        assertEquals("flac", result.extension());
        assertFalse(result.requestedExtensionOverridden());
    }

    @Test
    void audioProviderFormatOverridesConflictingRequestedExtension() {
        var result = MediaOutputPathResolver.resolve(
                new MediaOutputPathResolver.Request("recording.mp3", "wav", "flac", true));
        assertEquals(Path.of("recording.flac"), result.path());
        assertEquals("flac", result.extension());
        assertTrue(result.requestedExtensionOverridden());
        assertEquals("wav", result.requestedExtension());
    }

    @Test
    void replacesKnownAudioSuffixWhenProviderFormatChanges() {
        var result = MediaOutputPathResolver.resolve(
                new MediaOutputPathResolver.Request("recording.mp3", null, "wav", true));
        assertEquals(Path.of("recording.wav"), result.path());
    }

    @Test
    void explicitExtensionReplacesImageSuffix() {
        var result = MediaOutputPathResolver.resolve(
                new MediaOutputPathResolver.Request("art.png", ".webp", "png", false));
        assertEquals(Path.of("art.webp"), result.path());
        assertEquals("webp", result.extension());
        assertFalse(result.requestedExtensionOverridden());
    }

    @Test
    void preservesUnknownUserPathSuffixWhenNoExtensionWasRequested() {
        var result = MediaOutputPathResolver.resolve(
                new MediaOutputPathResolver.Request("custom.data", null, "flac", true));
        assertEquals(Path.of("custom.data"), result.path());
    }
}
