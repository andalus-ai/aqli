package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GgufDaemonLauncherTest {

    @Test
    void normalizesSupportedModesAndFallsBackForUnknownValues() {
        assertEquals("auto", GgufDaemonLauncher.normalizeMode(" AUTO ", "nohup"));
        assertEquals("launchctl", GgufDaemonLauncher.normalizeMode("LaunchCtl", "nohup"));
        assertEquals("nohup", GgufDaemonLauncher.normalizeMode("invalid", "nohup"));
        assertEquals("nohup", GgufDaemonLauncher.normalizeMode(null, "nohup"));
    }

    @Test
    void quotesArgumentsAndRedirectsDaemonOutput() {
        String shell = GgufDaemonLauncher.shellCommand(
                List.of("/path/java", "argument with spaces", "it's-safe"),
                true,
                Path.of("/tmp/daemon.log"),
                "/opt/aqli/libs",
                true);

        assertTrue(shell.startsWith("export DYLD_LIBRARY_PATH='/opt/aqli/libs'"));
        assertTrue(shell.contains("'argument with spaces'"));
        assertTrue(shell.contains("'it'\\''s-safe'"));
        assertTrue(shell.contains(">> '/tmp/daemon.log' 2>&1 < /dev/null &"));
        assertTrue(shell.contains("AQLI_GGUF_FAST_PROMPT_CACHE"));
    }

    @Test
    void launchctlShellUsesExecWithoutNohupBackgrounding() {
        String shell = GgufDaemonLauncher.shellCommand(
                List.of("java", "__daemon"),
                false,
                Path.of("/tmp/daemon.log"),
                "/opt/aqli/libs",
                false);

        assertTrue(shell.contains("exec 'java' '__daemon'"));
        assertTrue(shell.endsWith(">> '/tmp/daemon.log' 2>&1 < /dev/null"));
        assertTrue(!shell.endsWith("&"));
    }
}
