package tech.kayys.aqli.onnx.runner.tts.moss.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MossTtsVoiceCatalogTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void resolvesVoiceIdentityDisplayNameAndGroupFallbacks() throws Exception {
        JsonNode voice = JSON.readTree("{\"id\":\"junhao\",\"group\":\"English Female\"}");
        assertEquals("junhao", MossTtsVoiceCatalog.voiceId(voice));
        assertEquals("junhao", MossTtsVoiceCatalog.displayName(voice));
        assertEquals("English Female", MossTtsVoiceCatalog.group(voice));
        assertEquals("default", MossTtsVoiceCatalog.voiceId(JSON.readTree("{}")));
    }

    @Test
    void derivesLanguageGenderAndSelectorFromVoiceMetadata() throws Exception {
        JsonNode voice = JSON.readTree(
                "{\"voice\":\"junhao\",\"group\":\"English Female\",\"display_name\":\"Junhao\"}");
        assertEquals("en", MossTtsVoiceCatalog.languageLabel(voice));
        assertEquals("female", MossTtsVoiceCatalog.genderLabel(voice));
        assertEquals("en female", MossTtsVoiceCatalog.selectorExample(voice));
    }

    @Test
    void normalizesLanguageGenderAndSearchTerms() {
        assertEquals("jp", MossTtsVoiceCatalog.normalizeLanguageFilter("Japanese"));
        assertEquals("zh", MossTtsVoiceCatalog.normalizeLanguageFilter("Mandarin"));
        assertEquals("female", MossTtsVoiceCatalog.normalizeGenderFilter("woman"));
        assertEquals(List.of("junhao", "female"), MossTtsVoiceCatalog.filterTerms("  JUNHAO, female!! "));
        assertEquals("", MossTtsVoiceCatalog.normalizeFilterText(null));
    }

    @Test
    void matchesLanguageGenderAndEverySearchTerm() throws Exception {
        JsonNode voice = JSON.readTree(
                "{\"voice\":\"junhao\",\"group\":\"English Female\",\"display_name\":\"Warm Junhao\"}");
        assertTrue(MossTtsVoiceCatalog.matches(voice, "en", "female", List.of("warm", "junhao")));
        assertFalse(MossTtsVoiceCatalog.matches(voice, "jp", "female", List.of()));
        assertFalse(MossTtsVoiceCatalog.matches(voice, "en", "male", List.of()));
        assertFalse(MossTtsVoiceCatalog.matches(voice, null, null, List.of("warm", "missing")));
    }

    @Test
    void summarizesVoiceGroupsInEncounterOrder() throws Exception {
        JsonNode voices = JSON.readTree("""
                [
                  {"voice":"a","group":"English Female"},
                  {"voice":"b","group":"English Female"},
                  {"voice":"c","group":"Japanese Male"}
                ]
                """);
        assertEquals(Map.of("en female", 2, "jp male", 1), MossTtsVoiceCatalog.groupCounts(voices));
        assertEquals("en female=2, jp male=1", MossTtsVoiceCatalog.groupSummary(voices));
        assertEquals("(none)", MossTtsVoiceCatalog.groupSummary(JSON.readTree("[]")));
    }
}
