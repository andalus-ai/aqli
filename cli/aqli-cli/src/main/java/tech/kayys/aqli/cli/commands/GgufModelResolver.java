package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.inference.gguf.GgufRunArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Resolves local and indexed model references for the standalone GGUF path. */
final class GgufModelResolver {
    private GgufModelResolver() {
    }

    static boolean hasKnownNonGgufTarget(GgufRunArguments args, boolean equivalentFastPathEnabled) {
        if (args.modelFile() != null && hasNonGgufExtension(args.modelFile())) {
            return true;
        }
        if (args.model() != null && hasNonGgufExtension(args.model())) {
            return true;
        }
        if (args.model() != null) {
            Optional<Path> direct = resolvePath(Path.of(args.model()));
            if (direct.isPresent()) {
                return false;
            }
            try {
                Optional<LocalModelIndex.Entry> indexed = LocalModelIndex.find(args.model());
                if (indexed.isPresent()) {
                    String format = indexed.get().format;
                    if (format != null && !format.isBlank() && !"gguf".equals(format.trim().toLowerCase(Locale.ROOT))) {
                        return !equivalentFastPathEnabled
                                || findEquivalentGgufModelPath(indexed.get()).isEmpty();
                    }
                }
            } catch (Throwable ignored) {
                // The full CLI can still resolve unknown references after this lightweight
                // fallback.
            }
        }
        return false;
    }

    private static boolean hasNonGgufExtension(String value) {
        String normalized = value.replace('\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith(".litertlm")
                || normalized.endsWith(".task")
                || normalized.endsWith(".tflite")
                || normalized.endsWith(".safetensors")
                || normalized.endsWith(".safetensor")
                || normalized.endsWith(".onnx")
                || normalized.endsWith(".pt")
                || normalized.endsWith(".pth")
                || normalized.endsWith(".bin");
    }

    static Optional<Path> resolve(GgufRunArguments args, boolean equivalentFastPathEnabled) {
        if (args.modelFile() != null) {
            Optional<Path> explicit = resolvePath(Path.of(args.modelFile()));
            if (explicit.isPresent()) {
                return findGguf(explicit.get());
            }
        }
        if (args.model() != null) {
            Optional<Path> direct = resolvePath(Path.of(args.model()));
            if (direct.isPresent()) {
                Optional<Path> found = findGguf(direct.get());
                if (found.isPresent()) {
                    return found;
                }
            }
            try {
                Optional<LocalModelIndex.Entry> indexed = LocalModelIndex.find(args.model());
                if (indexed.isPresent() && indexed.get().path != null && !indexed.get().path.isBlank()) {
                    Optional<Path> indexedGguf = findGguf(Path.of(indexed.get().path));
                    if (indexedGguf.isPresent()) {
                        return indexedGguf;
                    }
                    if (equivalentFastPathEnabled) {
                        return findEquivalentGgufModelPath(indexed.get());
                    }
                }
            } catch (Throwable ignored) {
                // Full CLI has the complete resolver if the lightweight index read fails.
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findEquivalentGgufModelPath(LocalModelIndex.Entry requested) {
        if (!canUseGgufEquivalentFor(requested)) {
            return Optional.empty();
        }
        var requestedKeys = canonicalModelKeys(requested);
        if (requestedKeys.isEmpty()) {
            return Optional.empty();
        }
        try {
            return LocalModelIndex.entries().stream()
                    .filter(candidate -> candidate != null && candidate.path != null && !candidate.path.isBlank())
                    .filter(candidate -> !sameIndexPath(candidate, requested))
                    .filter(GgufModelResolver::isGgufIndexEntry)
                    .filter(candidate -> compatibleArchitecture(requested, candidate))
                    .filter(candidate -> intersects(requestedKeys, canonicalModelKeys(candidate)))
                    .sorted((left, right) -> Integer.compare(
                            equivalentGgufPreference(left),
                            equivalentGgufPreference(right)))
                    .map(candidate -> Path.of(candidate.path))
                    .map(GgufModelResolver::findGguf)
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean canUseGgufEquivalentFor(LocalModelIndex.Entry requested) {
        if (requested == null || isGgufIndexEntry(requested)) {
            return false;
        }
        String format = requested.format == null ? "" : requested.format.trim().toLowerCase(Locale.ROOT);
        if (!format.isBlank()
                && !format.equals("safetensor")
                && !format.equals("safetensors")) {
            return false;
        }
        String architecture = requested.architecture == null
                ? ""
                : requested.architecture.trim().toLowerCase(Locale.ROOT);
        String identity = ((requested.id == null ? "" : requested.id) + " "
                + (requested.name == null ? "" : requested.name) + " "
                + (requested.path == null ? "" : requested.path)).toLowerCase(Locale.ROOT);
        return architecture.contains("gemma") || identity.contains("gemma");
    }

    private static boolean isGgufIndexEntry(LocalModelIndex.Entry entry) {
        if (entry == null) {
            return false;
        }
        String format = entry.format == null ? "" : entry.format.trim().toLowerCase(Locale.ROOT);
        if (format.equals("gguf")) {
            return true;
        }
        return entry.path != null && entry.path.toLowerCase(Locale.ROOT).endsWith(".gguf");
    }

    private static boolean compatibleArchitecture(LocalModelIndex.Entry requested, LocalModelIndex.Entry candidate) {
        String requestedArchitecture = normalizedArchitecture(requested.architecture);
        String candidateArchitecture = normalizedArchitecture(candidate.architecture);
        return requestedArchitecture.isBlank()
                || candidateArchitecture.isBlank()
                || requestedArchitecture.equals(candidateArchitecture);
    }

    private static String normalizedArchitecture(String architecture) {
        if (architecture == null || architecture.isBlank()) {
            return "";
        }
        String normalized = architecture.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("unknown")) {
            return "";
        }
        if (normalized.contains("gemma")) {
            return "gemma";
        }
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static List<String> canonicalModelKeys(LocalModelIndex.Entry entry) {
        List<String> keys = new ArrayList<>();
        if (entry == null) {
            return keys;
        }
        addCanonicalModelKey(keys, entry.id);
        addCanonicalModelKey(keys, entry.name);
        addCanonicalModelKey(keys, entry.path);
        return keys;
    }

    private static void addCanonicalModelKey(List<String> keys, String value) {
        String key = LiteRtLmFastRun.canonicalModelKey(value);
        if (!key.isBlank() && !keys.contains(key)) {
            keys.add(key);
        }
    }

    private static boolean sameIndexPath(LocalModelIndex.Entry left, LocalModelIndex.Entry right) {
        if (left == null || right == null || left.path == null || right.path == null) {
            return false;
        }
        return left.path.equals(right.path);
    }

    private static boolean intersects(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        for (String value : right) {
            if (left.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static int equivalentGgufPreference(LocalModelIndex.Entry entry) {
        String name = ((entry.name == null ? "" : entry.name) + " "
                + (entry.path == null ? "" : entry.path)).toLowerCase(Locale.ROOT);
        int score = 0;
        if (!name.endsWith(".gguf")) {
            score += 10;
        }
        if (name.contains("q4_k_m")) {
            score -= 5;
        } else if (name.contains("q4")) {
            score -= 3;
        } else if (name.contains("q5")) {
            score += 2;
        } else if (name.contains("q8")) {
            score += 8;
        } else if (name.contains("f16") || name.contains("fp16") || name.contains("bf16")) {
            score += 20;
        }
        return score;
    }

    private static Optional<Path> resolvePath(Path raw) {
        Path expanded = expandHome(raw);
        if (Files.exists(expanded)) {
            return Optional.of(expanded.toAbsolutePath().normalize());
        }
        return Optional.empty();
    }

    private static Path expandHome(Path path) {
        String text = path.toString();
        if (text.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (text.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), text.substring(2));
        }
        return path;
    }

    private static Optional<Path> findGguf(Path path) {
        try {
            if (Files.isRegularFile(path) && isGguf(path)) {
                return Optional.of(path.toAbsolutePath().normalize());
            }
            Path searchDir = Files.isDirectory(path) ? path : path.getParent();
            if (searchDir == null || !Files.isDirectory(searchDir)) {
                return Optional.empty();
            }
            try (var stream = Files.list(searchDir)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(GgufModelResolver::isGguf)
                        .findFirst()
                        .map(candidate -> candidate.toAbsolutePath().normalize());
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean isGguf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gguf");
    }

}
