package tech.kayys.aqli.cli.commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tech.kayys.aqli.sdk.util.AqliHome;
import tech.kayys.aqli.cli.util.CLIUtils;

/**
 * Resolves references against the legacy local model index.
 *
 * <p>This matcher preserves the historical lookup order: exact identifiers,
 * legacy short IDs, then path suffixes. It also owns provider-prefix and
 * format-alias normalization so index storage/scanning stays independent of
 * reference interpretation.</p>
 */
final class LocalModelIndexReferenceMatcher {
    private LocalModelIndexReferenceMatcher() {
    }

    static Optional<LocalModelIndex.Entry> findBestMatch(List<LocalModelIndex.Entry> entries, String ref) {
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalModelIndex.Entry> exact = entries.stream().filter(e -> matchesExact(e, ref)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        Optional<LocalModelIndex.Entry> legacyShort = entries.stream().filter(e -> matchesLegacyShortId(e, ref)).findFirst();
        if (legacyShort.isPresent()) {
            return legacyShort;
        }
        return entries.stream().filter(e -> matchesPathSuffix(e, ref)).findFirst();
    }

    private static boolean matchesExact(LocalModelIndex.Entry e, String ref) {
        if (e == null) {
            return false;
        }
        String normalizedRef = stripProviderPrefix(ref);
        return equalsRef(ref, e.id)
                || equalsRef(ref, e.shortId)
                || equalsRef(ref, e.name)
                || equalsRef(ref, e.path)
                || equalsRef(normalizedRef, e.id)
                || equalsRef(normalizedRef, e.shortId)
                || equalsRef(normalizedRef, e.name)
                || equalsRef(normalizedRef, e.path);
    }

    private static boolean matchesPathSuffix(LocalModelIndex.Entry e, String ref) {
        if (e == null || ref == null || ref.isBlank() || looksLikeShortId(ref)) {
            return false;
        }
        String normalizedRef = stripProviderPrefix(ref);
        if (looksLikeShortId(normalizedRef)) {
            return false;
        }
        String normalized = normalizedRef.replace("\\", "/").toLowerCase(Locale.ROOT);
        String path = e.path != null ? e.path.replace("\\", "/").toLowerCase(Locale.ROOT) : "";
        return !normalized.isBlank() && path.endsWith(normalized);
    }

    private static boolean matchesLegacyShortId(LocalModelIndex.Entry e, String ref) {
        String normalizedRef = stripProviderPrefix(ref);
        if (e == null || !looksLikeShortId(normalizedRef)) {
            return false;
        }
        for (String legacyBasis : legacyShortIdBases(e)) {
            if (equalsRef(normalizedRef, CLIUtils.generateShortId(legacyBasis))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> legacyShortIdBases(LocalModelIndex.Entry e) {
        List<String> bases = new ArrayList<>();
        addIfPresent(bases, e.id);
        addIfPresent(bases, e.name);
        addIfPresent(bases, e.path);
        addIfPresent(bases, repositoryRelativePath(e.path));
        return bases;
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value == null || value.isBlank() || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    private static String repositoryRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(absolutePath).toAbsolutePath().normalize();
            Path root = AqliHome.path("models").toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                return null;
            }
            Path relative = root.relativize(path);
            if (relative.getNameCount() < 3) {
                return null;
            }
            return relative.subpath(1, relative.getNameCount()).toString().replace("\\", "/");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean equalsRef(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || left.equalsIgnoreCase(right);
    }

    private static boolean looksLikeShortId(String ref) {
        if (ref == null) {
            return false;
        }
        String value = ref.trim();
        if (value.length() < 4 || value.length() > 12) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isHexChar(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean formatMatches(LocalModelIndex.Entry entry, String preferredFormat) {
        if (entry == null || preferredFormat == null || preferredFormat.isBlank()) {
            return false;
        }
        String entryFormat = canonicalFormat(entry.format);
        String requestedFormat = canonicalFormat(preferredFormat);
        return !entryFormat.isBlank() && entryFormat.equals(requestedFormat);
    }

    private static String canonicalFormat(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "safetensor" -> "safetensors";
            case "tflite", "task", "litertlm" -> "litert";
            case "pt", "pth", "pts", "pytorch" -> "torchscript";
            default -> normalized;
        };
    }

    static String stripProviderPrefix(String ref) {
        if (ref == null) {
            return "";
        }
        String value = ref.trim();
        if (value.startsWith("hf:")) {
            return value.substring("hf:".length()).trim();
        }
        if (value.startsWith("huggingface:")) {
            return value.substring("huggingface:".length()).trim();
        }
        return value;
    }

    private static boolean isHexChar(int ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }


}
