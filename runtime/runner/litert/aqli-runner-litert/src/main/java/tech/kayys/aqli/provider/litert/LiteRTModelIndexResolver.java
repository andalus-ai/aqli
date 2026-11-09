package tech.kayys.aqli.provider.litert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a requested model identity against AQLI's local model index and,
 * where explicitly enabled, a compatible local LiteRT equivalent.
 *
 * <p>The index location and equivalent-artifact policy are supplied by the
 * caller. This keeps filesystem layout and CLI configuration out of the
 * LiteRT runner module while centralizing LiteRT-specific matching.</p>
 */
public final class LiteRTModelIndexResolver {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record ModelMatch(Path path, String source) {}

    private record Candidate(
            String id, String shortId, String name,
            String architecture, String format, String path) {}

    public Optional<ModelMatch> resolve(Path indexPath, String ref, boolean allowEquivalent) {
        if (indexPath == null || ref == null || ref.isBlank() || !Files.isRegularFile(indexPath)) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(indexPath.toFile());
            if (root == null || !root.isArray()) return Optional.empty();
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode node : root) {
                Candidate candidate = candidate(node);
                if (candidate.path() == null || candidate.path().isBlank()) continue;
                candidates.add(candidate);
                if (matches(candidate, ref.trim()) && isLiteRt(candidate)) {
                    return Optional.of(new ModelMatch(Path.of(candidate.path()), "fast-index-litert"));
                }
            }
            if (!allowEquivalent) return Optional.empty();
            Candidate requested = candidates.stream()
                    .filter(candidate -> matches(candidate, ref.trim()))
                    .findFirst().orElse(null);
            if (requested == null || !canUseEquivalent(requested)) return Optional.empty();
            Set<String> keys = canonicalKeys(requested);
            if (keys.isEmpty()) return Optional.empty();
            return candidates.stream()
                    .filter(LiteRTModelIndexResolver::isLiteRt)
                    .filter(candidate -> !samePath(candidate, requested))
                    .filter(candidate -> compatibleArchitecture(requested, candidate))
                    .filter(candidate -> intersects(keys, canonicalKeys(candidate)))
                    .sorted((left, right) -> Integer.compare(preference(left), preference(right)))
                    .map(Candidate::path)
                    .filter(path -> path != null && !path.isBlank())
                    .map(Path::of)
                    .findFirst()
                    .map(path -> new ModelMatch(path, "fast-index-equivalent-litert"));
        } catch (Exception ignored) {
            // The full CLI resolver remains the fallback for malformed or unreadable indexes.
            return Optional.empty();
        }
    }

    private static Candidate candidate(JsonNode node) {
        return new Candidate(
                text(node, "id"), text(node, "shortId"), text(node, "name"),
                text(node, "architecture"), text(node, "format"), text(node, "path"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean matches(Candidate candidate, String ref) {
        if (equalsRef(ref, candidate.id()) || equalsRef(ref, candidate.shortId())
                || equalsRef(ref, candidate.name()) || equalsRef(ref, candidate.path())) return true;
        String normalized = ref.replace("\\", "/").toLowerCase(Locale.ROOT);
        String path = candidate.path() == null ? "" : candidate.path().replace("\\", "/").toLowerCase(Locale.ROOT);
        return path.endsWith(normalized);
    }

    private static boolean equalsRef(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean isLiteRt(Candidate candidate) {
        return isLiteRtFormat(candidate.format()) || isLiteRtPath(candidate.path());
    }

    private static boolean isLiteRtFormat(String format) {
        if (format == null || format.isBlank()) return false;
        return switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "litert", "litertlm", "tflite", "tfl", "task" -> true;
            default -> false;
        };
    }

    private static boolean isLiteRtPath(String path) {
        return path != null && path.replace("\\", "/").toLowerCase(Locale.ROOT).endsWith(".litertlm");
    }

    private static boolean canUseEquivalent(Candidate requested) {
        if (isLiteRt(requested)) return false;
        String format = requested.format() == null ? "" : requested.format().trim().toLowerCase(Locale.ROOT);
        if (!format.isBlank() && !format.equals("safetensor")
                && !format.equals("safetensors") && !format.equals("gguf")) return false;
        String architecture = requested.architecture() == null ? "" : requested.architecture().toLowerCase(Locale.ROOT);
        String identity = value(requested.id()) + " " + value(requested.name()) + " " + value(requested.path());
        return architecture.contains("gemma") || identity.toLowerCase(Locale.ROOT).contains("gemma");
    }

    private static boolean compatibleArchitecture(Candidate requested, Candidate candidate) {
        String left = normalizedArchitecture(requested.architecture());
        String right = normalizedArchitecture(candidate.architecture());
        return left.isBlank() || right.isBlank() || left.equals(right);
    }

    private static String normalizedArchitecture(String architecture) {
        if (architecture == null || architecture.isBlank() || architecture.equalsIgnoreCase("unknown")) return "";
        String normalized = architecture.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("gemma")) return "gemma";
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private static Set<String> canonicalKeys(Candidate candidate) {
        Set<String> keys = new HashSet<>();
        addKey(keys, candidate.id());
        addKey(keys, candidate.name());
        addKey(keys, lastPathSegment(candidate.path()));
        return keys;
    }

    private static void addKey(Set<String> keys, String value) {
        String key = canonicalKey(value);
        if (!key.isBlank()) keys.add(key);
    }

    static String canonicalKey(String value) {
        if (value == null || value.isBlank()) return "";
        String leaf = lastPathSegment(value).replace("__", "/").replace('\\', '/').toLowerCase(Locale.ROOT);
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < leaf.length()) leaf = leaf.substring(slash + 1);
        leaf = leaf.replaceAll("\\.(litertlm|litert|tflite|tfl|task|gguf|safetensors?|bin)$", "")
                .replaceAll("(?i)(-?litert-?lm|-?litertlm|-?tflite|-?safetensors?|\\.safetensors?)$", "");
        StringBuilder key = new StringBuilder();
        for (String token : leaf.split("[^a-z0-9]+")) {
            if (!token.isBlank() && !isNoise(token)) key.append(token);
        }
        return key.toString();
    }

    private static String lastPathSegment(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 && slash + 1 < normalized.length() ? normalized.substring(slash + 1) : normalized;
    }

    private static boolean isNoise(String token) {
        return switch (token) {
            case "google", "litert", "community", "lm", "gguf", "safetensor", "safetensors",
                    "qat", "mobile", "transformers", "q4", "q5", "q6", "q8", "k", "m", "f16", "fp16", "bf16" -> true;
            default -> false;
        };
    }

    private static int preference(Candidate candidate) {
        String path = value(candidate.path()).toLowerCase(Locale.ROOT);
        String name = value(candidate.name()).toLowerCase(Locale.ROOT);
        int score = 0;
        if (!path.endsWith(".litertlm") && !name.endsWith(".litertlm")) score += 20;
        if (path.contains("qualcomm") || name.contains("qualcomm")) score += 100;
        return score;
    }

    private static boolean samePath(Candidate left, Candidate right) {
        return left.path() != null && right.path() != null && left.path().equals(right.path());
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        for (String value : right) if (left.contains(value)) return true;
        return false;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
