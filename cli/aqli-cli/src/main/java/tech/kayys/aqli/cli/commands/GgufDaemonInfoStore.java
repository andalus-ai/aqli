package tech.kayys.aqli.cli.commands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        Path target = file.toAbsolutePath();
        Path parent = target.getParent();
        Files.createDirectories(parent);

        byte[] contents = (port + System.lineSeparator()
                + pid + System.lineSeparator()
                + (key == null ? "" : key) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = (prefix + "___").substring(0, 3);
        }

        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(contents);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // Preserve the daemon's best-effort cleanup behavior.
        }
    }
}
