package tech.kayys.aqli.onnx.runner.tts.moss.voice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads and searches the built-in voice catalog in a MOSS TTS model manifest.
 *
 * <p>CLI selector parsing and output formatting remain in the CLI adapter.</p>
 */
public final class MossTtsVoiceCatalog {
    private MossTtsVoiceCatalog() {
    }

    public static String voiceId(JsonNode voice) {
        if (voice == null) {
            return "default";
        }
        for (String field : List.of("voice", "id", "name", "display_name")) {
            String value = voice.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "default";
    }

    public static String displayName(JsonNode voice) {
        String displayName = voice == null ? "" : voice.path("display_name").asText("");
        return displayName.isBlank() ? voiceId(voice) : displayName;
    }

    public static String group(JsonNode voice) {
        String group = voice == null ? "" : voice.path("group").asText("");
        return group.isBlank() ? "-" : group;
    }

    public static String searchText(JsonNode voice) {
        String raw = voiceId(voice) + " " + group(voice) + " " + displayName(voice);
        return " " + normalizeFilterText(raw) + " ";
    }

    public static String languageLabel(JsonNode voice) {
        String text = searchText(voice);
        if (text.contains("english") || text.contains(" en ") || text.startsWith("en ")) {
            return "en";
        }
        if (text.contains("japanese") || text.contains(" jp ") || text.startsWith("jp ")) {
            return "jp";
        }
        if (text.contains("chinese") || text.contains(" cn ") || text.contains(" zh ")
                || text.startsWith("cn ") || text.startsWith("zh ")) {
            return "zh";
        }
        return "other";
    }

    public static String genderLabel(JsonNode voice) {
        String text = searchText(voice);
        if (text.contains("female")) {
            return "female";
        }
        if (text.contains("male")) {
            return "male";
        }
        return "unspecified";
    }

    public static String selectorExample(JsonNode voice) {
        String language = languageLabel(voice);
        String gender = genderLabel(voice);
        if (!"other".equals(language) && !"unspecified".equals(gender)) {
            return language + " " + gender;
        }
        if (!"other".equals(language)) {
            return language;
        }
        return voiceId(voice);
    }

    public static List<String> filterTerms(String value) {
        String normalized = normalizeFilterText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    public static String normalizeLanguageFilter(String value) {
        return switch (normalizeFilterText(value)) {
            case "en", "eng", "english" -> "en";
            case "jp", "ja", "jpn", "japanese" -> "jp";
            case "cn", "zh", "zho", "chi", "chinese", "mandarin" -> "zh";
            default -> null;
        };
    }

    public static String normalizeGenderFilter(String value) {
        return switch (normalizeFilterText(value)) {
            case "f", "female", "woman", "women", "girl" -> "female";
            case "m", "male", "man", "men", "boy" -> "male";
            default -> null;
        };
    }

    public static String normalizeFilterText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static boolean matches(JsonNode voice, String language, String gender, List<String> terms) {
        if (language != null && !language.equals(languageLabel(voice))) {
            return false;
        }
        if (gender != null && !gender.equals(genderLabel(voice))) {
            return false;
        }
        if (terms != null && !terms.isEmpty()) {
            String searchText = searchText(voice);
            for (String term : terms) {
                if (term != null && !term.isBlank() && !searchText.contains(term)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Map<String, Integer> groupCounts(JsonNode voices) {
        if (voices == null || !voices.isArray()) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode voice : voices) {
            String key = languageLabel(voice) + " " + genderLabel(voice);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    public static String groupSummary(JsonNode voices) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : groupCounts(voices).entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return parts.isEmpty() ? "(none)" : String.join(", ", parts);
    }
}
