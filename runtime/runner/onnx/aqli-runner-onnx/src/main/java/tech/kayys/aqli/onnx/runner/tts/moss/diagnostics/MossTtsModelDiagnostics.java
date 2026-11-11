package tech.kayys.aqli.onnx.runner.tts.moss.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.onnx.runner.tts.moss.model.MossTtsModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.voice.MossTtsVoiceCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/** Collects a stable, presentation-neutral snapshot of a MOSS TTS model manifest. */
public final class MossTtsModelDiagnostics {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MossTtsModelDiagnostics() {
    }

    public static Snapshot inspect(Path modelDirectory) {
        if (modelDirectory == null || !Files.isDirectory(modelDirectory)) {
            return Snapshot.unavailable("MOSS TTS model directory is unavailable");
        }
        Path manifest = modelDirectory.resolve(MossTtsModelLayout.MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            return Snapshot.unavailable("MOSS TTS manifest is missing: " + manifest);
        }
        try {
            JsonNode voices = MAPPER.readTree(manifest.toFile()).path("builtin_voices");
            int count = voices.isArray() ? voices.size() : 0;
            VoiceSummary defaultVoice = count == 0 ? null : new VoiceSummary(
                    MossTtsVoiceCatalog.voiceId(voices.get(0)),
                    MossTtsVoiceCatalog.displayName(voices.get(0)),
                    MossTtsVoiceCatalog.languageLabel(voices.get(0)),
                    MossTtsVoiceCatalog.genderLabel(voices.get(0)));
            return new Snapshot(true, count, MossTtsVoiceCatalog.groupCounts(voices), defaultVoice,
                    count == 0 ? "manifest does not contain builtin_voices" : null);
        } catch (Exception failure) {
            return Snapshot.unavailable("could not read MOSS TTS manifest: " + failure.getMessage());
        }
    }

    public record VoiceSummary(String id, String displayName, String language, String gender) { }

    public record Snapshot(boolean manifestAvailable, int voiceCount, Map<String, Integer> voiceGroups,
            VoiceSummary defaultVoice, String manifestError) {
        public Snapshot {
            voiceGroups = voiceGroups == null ? Map.of() : Map.copyOf(voiceGroups);
        }

        public String voiceGroupSummary() {
            return voiceGroups.isEmpty() ? "(none)" : voiceGroups.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
        }

        private static Snapshot unavailable(String error) {
            return new Snapshot(false, 0, Map.of(), null, error);
        }
    }
}
