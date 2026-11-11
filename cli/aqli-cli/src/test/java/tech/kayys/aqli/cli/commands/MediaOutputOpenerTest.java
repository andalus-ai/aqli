package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MediaOutputOpenerTest {
    @Test
    void opensImagesWithPlatformViewer() {
        List<List<String>> commands = new ArrayList<>();

        MediaOutputOpener.openImage(Path.of("image.png"), "Mac OS X",
                command -> commands.add(List.of(command)));
        MediaOutputOpener.openImage(Path.of("image.png"), "Linux",
                command -> commands.add(List.of(command)));

        assertEquals(List.of(
                List.of("open", "image.png"),
                List.of("xdg-open", "image.png")), commands);
    }

    @Test
    void opensAudioOnlyOnMacOs() {
        List<List<String>> commands = new ArrayList<>();

        MediaOutputOpener.openAudio(Path.of("audio.flac"), "Mac OS X",
                command -> commands.add(List.of(command)));
        MediaOutputOpener.openAudio(Path.of("audio.flac"), "Linux",
                command -> commands.add(List.of(command)));

        assertEquals(List.of(List.of("afplay", "audio.flac")), commands);
    }

    @Test
    void ignoresUnsupportedPlatforms() {
        List<List<String>> commands = new ArrayList<>();

        MediaOutputOpener.openImage(Path.of("image.png"), "Windows 11",
                command -> commands.add(List.of(command)));
        MediaOutputOpener.openAudio(Path.of("audio.flac"), "Windows 11",
                command -> commands.add(List.of(command)));

        assertTrue(commands.isEmpty());
    }

    @Test
    void ignoresProcessLaunchFailures() {
        MediaOutputOpener.openImage(Path.of("image.png"), "Linux",
                command -> { throw new IOException("viewer unavailable"); });
        MediaOutputOpener.openAudio(Path.of("audio.flac"), "Mac OS X",
                command -> { throw new IOException("player unavailable"); });
    }
}
