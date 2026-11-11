package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Opens completed media with the platform's default viewer or audio player. */
final class MediaOutputOpener {
    @FunctionalInterface
    interface ProcessLauncher {
        void start(String... command) throws IOException;
    }

    private MediaOutputOpener() {
    }

    static void openImage(Path imagePath) {
        openImage(imagePath, System.getProperty("os.name"), MediaOutputOpener::startProcess);
    }

    static void openAudio(Path audioPath) {
        openAudio(audioPath, System.getProperty("os.name"), MediaOutputOpener::startProcess);
    }

    static void openImage(Path imagePath, String osName, ProcessLauncher launcher) {
        try {
            String os = normalizedOsName(osName);
            if (os.contains("mac")) {
                launcher.start("open", imagePath.toString());
            } else if (os.contains("linux")) {
                launcher.start("xdg-open", imagePath.toString());
            }
        } catch (Exception ignored) {
            // Opening media is best-effort and must not fail the completed inference.
        }
    }

    static void openAudio(Path audioPath, String osName, ProcessLauncher launcher) {
        try {
            if (normalizedOsName(osName).contains("mac")) {
                launcher.start("afplay", audioPath.toString());
            }
        } catch (Exception ignored) {
            // Opening media is best-effort and must not fail the completed inference.
        }
    }

    private static String normalizedOsName(String osName) {
        return osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    }

    private static void startProcess(String... command) throws IOException {
        new ProcessBuilder(command).start();
    }
}
