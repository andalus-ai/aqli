package tech.kayys.aqli.cli.commands.features.install;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists the installed-extension inventory without interpreting capability semantics. */
public final class FeatureInstallLockStore {
    public static final String LOCKFILE = "aqli-extensions.lock.json";
    public static final String LEGACY_LOCKFILE = "aqli-features.lock.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeatureInstallLockStore() {
    }

    public static Path path(Path featureDir) {
        Path root = featureDir == null ? Path.of(".") : featureDir;
        return root.toAbsolutePath().normalize().resolve(LOCKFILE);
    }

    public static Path legacyPath(Path featureDir) {
        Path root = featureDir == null ? Path.of(".") : featureDir;
        return root.toAbsolutePath().normalize().resolve(LEGACY_LOCKFILE);
    }

    public static Path readPath(Path featureDir) {
        Path preferred = path(featureDir);
        if (Files.isRegularFile(preferred)) return preferred;
        Path legacy = legacyPath(featureDir);
        return Files.isRegularFile(legacy) ? legacy : preferred;
    }

    public static State read(Path featureDir) {
        if (featureDir == null) return new State(Map.of(), "");
        Path lockfile = readPath(featureDir);
        if (!Files.isRegularFile(lockfile)) return new State(Map.of(), "");
        try (var input = Files.newInputStream(lockfile)) {
            Map<?, ?> root = MAPPER.readValue(input, Map.class);
            Object raw = root == null ? null
                    : root.get("extensions") != null ? root.get("extensions") : root.get("features");
            if (!(raw instanceof Map<?, ?> rawEntries)) {
                return new State(Map.of(),
                        "Extension install lock has no 'extensions' object: " + lockfile);
            }
            Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
            rawEntries.forEach((key, value) -> {
                if (key != null && value instanceof Map<?, ?> rawEntry) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    rawEntry.forEach((entryKey, entryValue) -> {
                        if (entryKey != null) entry.put(String.valueOf(entryKey), entryValue);
                    });
                    entries.put(String.valueOf(key), entry);
                }
            });
            return new State(entries, "");
        } catch (Exception e) {
            return new State(Map.of(), "Extension install lock is not readable: " + e.getMessage());
        }
    }

    public static void put(Path featureDir, Path artifact, Map<String, Object> entry,
            Map<String, Object> runtime) throws IOException {
        Path root = featureDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>(read(root).entries());
        String filename = artifact.getFileName() == null ? artifact.toString() : artifact.getFileName().toString();
        entries.put(filename, new LinkedHashMap<>(entry));
        write(root, entries, runtime);
    }

    public static void remove(Path featureDir, Path artifact, Map<String, Object> runtime) throws IOException {
        Path root = featureDir.toAbsolutePath().normalize();
        Map<String, Map<String, Object>> entries = new LinkedHashMap<>(read(root).entries());
        if (entries.isEmpty()) return;
        Path name = artifact.getFileName();
        if (name != null) entries.remove(name.toString());
        write(root, entries, runtime);
    }

    public static void write(Path featureDir, Map<String, Map<String, Object>> entries,
            Map<String, Object> runtime) throws IOException {
        Path root = featureDir.toAbsolutePath().normalize();
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("schema_version", 1);
        lock.put("updated_at", Instant.now().toString());
        lock.put("runtime", runtime == null ? Map.of() : runtime);
        lock.put("extensions", entries);
        lock.put("features", entries);
        Files.createDirectories(root);
        Files.writeString(path(root),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(lock) + System.lineSeparator(),
                java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public record State(Map<String, Map<String, Object>> entries, String error) {
        public State {
            Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
            if (entries != null) entries.forEach((key, value) ->
                    copy.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(value))));
            entries = Collections.unmodifiableMap(copy);
            error = error == null ? "" : error;
        }
    }
}
