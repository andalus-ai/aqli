package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persists the local GGUF daemon's port, process id, and runtime identity key. */
final class GgufDaemonInfoStore {
    record DaemonInfo(int port, long pid, String key) {
    }

    private final Path file;

    GgufDaemonInfoStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    Optional<DaemonInfo> read() {
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < 3) {
                return Optional.empty();
            }
            int port = Integer.parseInt(lines.get(0).trim());
            long pid = Long.parseLong(lines.get(1).trim());
            String key = lines.get(2);
            return port > 0 && port <= 65535
                    ? Optional.of(new DaemonInfo(port, pid, key))
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    void write(int port, long pid, String key) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file,
                port + System.lineSeparator()
                        + pid + System.lineSeparator()
                        + (key == null ? "" : key) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // Preserve the daemon's best-effort cleanup behavior.
        }
    }
}
