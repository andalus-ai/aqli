package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GgufDaemonInfoStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsDaemonMetadata() throws Exception {
        Path file = tempDir.resolve("run").resolve("gguf-fast-daemon.port");
        GgufDaemonInfoStore store = new GgufDaemonInfoStore(file);

        store.write(43210, 1234L, "runtime-key");

        GgufDaemonInfoStore.DaemonInfo info = store.read().orElseThrow();
        assertEquals(43210, info.port());
        assertEquals(1234L, info.pid());
        assertEquals("runtime-key", info.key());
    }

    @Test
    void returnsEmptyForMalformedOrOutOfRangeMetadata() throws Exception {
        Path file = tempDir.resolve("gguf-fast-daemon.port");
        GgufDaemonInfoStore store = new GgufDaemonInfoStore(file);

        Files.writeString(file, "not-a-port\n1234\nkey\n");
        assertTrue(store.read().isEmpty());

        Files.writeString(file, "65536\n1234\nkey\n");
        assertTrue(store.read().isEmpty());

        Files.writeString(file, "43210\n");
        assertTrue(store.read().isEmpty());
    }

    @Test
    void deletesDaemonMetadata() throws Exception {
        Path file = tempDir.resolve("gguf-fast-daemon.port");
        GgufDaemonInfoStore store = new GgufDaemonInfoStore(file);
        store.write(43210, 1234L, "runtime-key");

        store.delete();

        assertTrue(store.read().isEmpty());
        assertTrue(Files.notExists(file));
    }
}
