package tech.kayys.aqli.cli.commands.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioReplayOutputPathResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsReplaySuffixAndUsesRequestedFormat() {
        Path result = AudioReplayOutputPathResolver.resolve(
                tempDir.resolve("speech.wav").toString(), "flac");
        assertEquals(tempDir.resolve("speech-replay.wav"), result);
    }

    @Test
    void usesDefaultNameWhenOriginalPathIsMissing() {
        Path result = AudioReplayOutputPathResolver.resolve(null, "mp3");
        assertEquals(Path.of("output-replay.mp3"), result);
    }

    @Test
    void choosesNextAvailableReplayName() throws Exception {
        Path existing = tempDir.resolve("speech-replay.wav");
        Files.writeString(existing, "first");
        Files.writeString(tempDir.resolve("speech-replay-2.wav"), "second");

        Path result = AudioReplayOutputPathResolver.resolve(tempDir.resolve("speech.wav").toString(), "wav");

        assertEquals(tempDir.resolve("speech-replay-3.wav"), result);
    }

    @Test
    void preservesExtensionWhenFormatIsNotProvided() {
        Path result = AudioReplayOutputPathResolver.resolve(
                tempDir.resolve("speech.WAV").toString(), null);
        assertEquals(tempDir.resolve("speech-replay.WAV"), result);
    }
}
