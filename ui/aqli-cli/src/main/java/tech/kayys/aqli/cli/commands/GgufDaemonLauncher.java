package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Starts the detached local GGUF daemon process using the platform launcher.
 *
 * <p>The runner owns daemon lifecycle and readiness checks. This class owns
 * only process command launching and platform-specific detached execution.</p>
 */
final class GgufDaemonLauncher {

    boolean launch(
            List<String> command,
            String mode,
            String label,
            Path logFile,
            boolean macOs,
            boolean debug,
            String nativePaths) throws IOException, InterruptedException {
        if (debug) {
            System.err.println("GGUF daemon launcher: " + mode);
        }
        if ("launchctl".equals(mode)) {
            return macOs && launchWithLaunchctl(command, label, logFile, debug, nativePaths);
        }
        if ("auto".equals(mode) && macOs && launchWithLaunchctl(command, label, logFile, debug)) {
            return true;
        }
        return launchWithNohup(command, logFile, nativePaths, macOs);
    }

    static String normalizeMode(String configured, String defaultMode) {
        String normalized = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "auto", "launchctl", "nohup" -> normalized;
            default -> defaultMode;
        };
    }

    static String shellCommand(List<String> command, boolean useNohup, Path logFile,
            String nativePaths, boolean macOs) {
        StringBuilder shellCommand = new StringBuilder();
        shellCommand.append("export DYLD_LIBRARY_PATH=")
                .append(shellQuote(nativePaths))
                .append("${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}; ");
        shellCommand.append("export DYLD_FALLBACK_LIBRARY_PATH=")
                .append(shellQuote(nativePaths))
                .append("${DYLD_FALLBACK_LIBRARY_PATH:+:$DYLD_FALLBACK_LIBRARY_PATH}; ");
        if (macOs) {
            shellCommand.append("export AQLI_GGUF_FAST_PROMPT_CACHE=\"${AQLI_GGUF_FAST_PROMPT_CACHE:-true}\"; ");
        }
        if (useNohup) {
            shellCommand.append("nohup ");
        } else {
            shellCommand.append("exec ");
        }
        for (int i = 0; i < command.size(); i++) {
            if (i > 0 || useNohup) {
                shellCommand.append(' ');
            }
            shellCommand.append(shellQuote(command.get(i)));
        }
        shellCommand.append(" >> ").append(shellQuote(logFile.toString()))
                .append(" 2>&1 < /dev/null");
        if (useNohup) {
            shellCommand.append(" &");
        }
        return shellCommand.toString();
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static boolean launchWithLaunchctl(
            List<String> command, String label, Path logFile, boolean debug, String nativePaths)
            throws IOException, InterruptedException {
        String launchctl = "/bin/launchctl";
        if (!Files.isExecutable(Path.of(launchctl))) {
            return false;
        }
        removeLaunchctl(label);
        Process launcher = new ProcessBuilder(
                launchctl,
                "submit",
                "-l",
                label,
                "--",
                "/bin/sh",
                "-c",
                shellCommand(command, false, logFile, nativePaths, true))
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start();
        if (!launcher.waitFor(3, TimeUnit.SECONDS)) {
            return true;
        }
        boolean launched = launcher.exitValue() == 0;
        if (!launched && debug) {
            System.err.println("GGUF launchctl daemon submit failed; falling back to nohup");
        }
        return launched;
    }

    private static boolean launchWithNohup(
            List<String> command, Path logFile, String nativePaths, boolean macOs)
            throws IOException, InterruptedException {
        Process launcher = new ProcessBuilder(
                "/bin/sh", "-c", shellCommand(command, true, logFile, nativePaths, macOs)).start();
        launcher.waitFor(2, TimeUnit.SECONDS);
        return true;
    }

    private static void removeLaunchctl(String label) {
        try {
            new ProcessBuilder("/bin/launchctl", "remove", label)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static String nativePaths() {
        String home = System.getProperty("user.home");
        return home + "/.aqli/libs" + java.io.File.pathSeparator + home + "/.aqli/libs/llama";
    }
}
