package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class GgufStaleDaemonLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void missingMetadataDoesNotInvokeProcessOrLaunchctlPolicy() {
        FakeProcesses processes = new FakeProcesses(true);
        int[] launchctlCalls = {0};
        GgufStaleDaemonLifecycle lifecycle = lifecycle(processes, launchctlCalls);

        assertTrue(lifecycle.resolvePort(Optional.empty(), null, false, true, 2000).isEmpty());
        assertEquals(0, processes.terminateCalls);
        assertEquals(0, launchctlCalls[0]);
    }

    @Test
    void liveDaemonIsReusedWithoutCleanupEvenWhenKeyDiffersInRelaxedMode() throws Exception {
        Path metadata = metadataFile();
        FakeProcesses processes = new FakeProcesses(true);
        int[] launchctlCalls = {0};
        GgufStaleDaemonLifecycle lifecycle = lifecycle(metadata, processes, launchctlCalls);

        OptionalInt port = lifecycle.resolvePort(
                Optional.of(new GgufDaemonInfoStore.DaemonInfo(4567, 1234, "old-key")),
                "new-key",
                false,
                true,
                2000);

        assertTrue(port.isPresent());
        assertEquals(4567, port.getAsInt());
        assertTrue(Files.exists(metadata));
        assertEquals(0, processes.terminateCalls);
        assertEquals(0, launchctlCalls[0]);
    }

    @Test
    void deadDaemonMetadataIsDeletedAndLaunchctlCleanupRuns() throws Exception {
        Path metadata = metadataFile();
        FakeProcesses processes = new FakeProcesses(false);
        int[] launchctlCalls = {0};
        GgufStaleDaemonLifecycle lifecycle = lifecycle(metadata, processes, launchctlCalls);

        OptionalInt port = lifecycle.resolvePort(
                Optional.of(new GgufDaemonInfoStore.DaemonInfo(4567, 1234, "key")),
                "key",
                false,
                true,
                2000);

        assertTrue(port.isEmpty());
        assertFalse(Files.exists(metadata));
        assertEquals(1, processes.terminateCalls);
        assertEquals(1, launchctlCalls[0]);
    }

    @Test
    void strictRuntimeKeyMismatchDiscardsOtherwiseLiveDaemon() throws Exception {
        Path metadata = metadataFile();
        FakeProcesses processes = new FakeProcesses(true);
        int[] launchctlCalls = {0};
        GgufStaleDaemonLifecycle lifecycle = lifecycle(metadata, processes, launchctlCalls);

        OptionalInt port = lifecycle.resolvePort(
                Optional.of(new GgufDaemonInfoStore.DaemonInfo(4567, 1234, "old-key")),
                "new-key",
                true,
                false,
                3500);

        assertTrue(port.isEmpty());
        assertFalse(Files.exists(metadata));
        assertEquals(1, processes.terminateCalls);
        assertFalse(processes.force);
        assertEquals(3500, processes.waitMillis);
        assertEquals(1, launchctlCalls[0]);
    }

    private Path metadataFile() throws Exception {
        Path file = tempDir.resolve("gguf-fast-daemon.port");
        new GgufDaemonInfoStore(file).write(4567, 1234, "key");
        return file;
    }

    private GgufStaleDaemonLifecycle lifecycle(FakeProcesses processes, int[] launchctlCalls) {
        return lifecycle(tempDir.resolve("missing.port"), processes, launchctlCalls);
    }

    private GgufStaleDaemonLifecycle lifecycle(Path file, FakeProcesses processes, int[] launchctlCalls) {
        return new GgufStaleDaemonLifecycle(
                new GgufDaemonInfoStore(file),
                processes,
                () -> launchctlCalls[0]++);
    }

    private static final class FakeProcesses implements GgufStaleDaemonLifecycle.ProcessController {
        private final boolean alive;
        private int terminateCalls;
        private boolean force;
        private long waitMillis;

        private FakeProcesses(boolean alive) {
            this.alive = alive;
        }

        @Override
        public boolean isAlive(long pid) {
            return alive;
        }

        @Override
        public void terminate(long pid, boolean force, long waitMillis) {
            terminateCalls++;
            this.force = force;
            this.waitMillis = waitMillis;
        }
    }
}
