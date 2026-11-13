package tech.kayys.aqli.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import tech.kayys.aqli.audio.output.LivePcmAudioPlayer;
import tech.kayys.aqli.cli.commands.audio.AudioDetailsFormatter;
import tech.kayys.aqli.model.repo.hf.HuggingFaceRepository;
import tech.kayys.aqli.sdk.util.AqliHome;
import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxRunner;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsCacheSettings;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsOnnxCacheDiagnostics;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsOptimizedCacheMaintenance;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.OptimizedCacheDirectoryScanner;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.OptimizedCachePruner;
import tech.kayys.aqli.onnx.runner.tts.moss.codec.MossAudioTokenizerModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.codec.MossAudioTokenizerModelLocator;
import tech.kayys.aqli.onnx.runner.tts.moss.diagnostics.MossTtsModelDiagnostics;
import tech.kayys.aqli.onnx.runner.tts.moss.model.MossTtsModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.voice.MossTtsVoiceCatalog;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.suling.FlacLibraryCheck;
import tech.kayys.suling.audio.FfmpegAudioEncoder;
import tech.kayys.suling.audio.Suling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles CLI operations for MOSS TTS, including voice listing, preflight diagnostics,
 * graph cache inspection and pruning, warmup, and companion audio codec resolution.
 */
final class MossTtsCliDiagnosticsHandler {

    private final RunCommand command;

    MossTtsCliDiagnosticsHandler(RunCommand command) {
        this.command = command;
    }

    record TtsVoiceListFilter(String language, String gender, List<String> terms) {
        boolean hasFilters() {
            return (language != null && !language.isBlank())
                    || (gender != null && !gender.isBlank())
                    || (terms != null && !terms.isEmpty());
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            if (language != null && !language.isBlank()) {
                parts.add("lang=" + language);
            }
            if (gender != null && !gender.isBlank()) {
                parts.add("gender=" + gender);
            }
            if (terms != null && !terms.isEmpty()) {
                parts.add("terms=" + String.join(",", terms));
            }
            return parts.isEmpty() ? "(all voices)" : String.join(", ", parts);
        }
    }

    int printTtsVoices(String modelId, String finalLocalPath) {
        Optional<Path> ttsDir = MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId);
        if (ttsDir.isEmpty()) {
            System.err.println("Error: --list-voices is only available for local MOSS TTS ONNX model directories.");
            System.err.println("Resolved model path: " + (finalLocalPath == null || finalLocalPath.isBlank()
                    ? "(none)"
                    : finalLocalPath));
            return 1;
        }

        Path manifestPath = ttsDir.get().resolve(MossTtsModelLayout.MANIFEST_FILE_NAME);
        try {
            JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
            JsonNode voices = manifest.path("builtin_voices");
            if (!voices.isArray() || voices.isEmpty()) {
                System.err.println("Error: MOSS TTS manifest does not include builtin voices: " + manifestPath);
                return 1;
            }

            TtsVoiceListFilter filter = ttsVoiceListFilter();
            List<JsonNode> matchedVoices = new ArrayList<>();
            for (JsonNode voice : voices) {
                if (MossTtsVoiceCatalog.matches(voice, filter.language(), filter.gender(), filter.terms())) {
                    matchedVoices.add(voice);
                }
            }

            if (ttsVoicesJsonOutput()) {
                printTtsVoicesJson(ttsDir.get(), filter, voices, matchedVoices);
                return 0;
            }

            System.out.println("TTS voices for: " + ttsDir.get().toAbsolutePath());
            if (filter.hasFilters()) {
                System.out.println("Filter: " + filter.describe());
                System.out.println("Matched: " + matchedVoices.size() + "/" + voices.size());
            }
            System.out.println();
            if (matchedVoices.isEmpty()) {
                System.out.println("No voices matched this filter.");
            } else {
                System.out.printf("%-7s %-12s %-8s %-11s %-18s %s%n",
                        "DEFAULT", "VOICE", "LANG", "GENDER", "GROUP", "DISPLAY NAME");
                System.out.println("------------------------------------------------------------------------------------------");
                for (JsonNode voice : matchedVoices) {
                    System.out.printf("%-7s %-12s %-8s %-11s %-18s %s%n",
                            voice == voices.get(0) ? "*" : "",
                            MossTtsVoiceCatalog.voiceId(voice),
                            MossTtsVoiceCatalog.languageLabel(voice),
                            MossTtsVoiceCatalog.genderLabel(voice),
                            truncateDisplay(MossTtsVoiceCatalog.group(voice), 18),
                            MossTtsVoiceCatalog.displayName(voice));
                }
            }
            System.out.println();
            System.out.println("Use one of the VOICE values with --voice, for example: --voice Junhao");
            System.out.println("Omit --voice, or use --voice auto, to pick a voice from the prompt language.");
            System.out.println("Language aliases are supported too: --voice en, --voice jp, --voice zh.");
            System.out.println("You can combine language and gender, for example: --voice en-female or --voice jp female.");
            System.out.println("Use --voice default for the first preset, or a unique display-name part such as --voice bitter.");
            System.out.println("Filter this table with --voice-search, --voice-lang, --voice-gender, or --voice.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: Failed to read MOSS TTS voices: " + e.getMessage());
            return 1;
        }
    }

    boolean ttsVoicesJsonOutput() {
        return command.jsonMode || command.listTtsVoicesJson;
    }

    private void printTtsVoicesJson(
            Path ttsDir,
            TtsVoiceListFilter filter,
            JsonNode voices,
            List<JsonNode> matchedVoices) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "moss_tts_voices");
        payload.put("model_path", ttsDir.toAbsolutePath().normalize().toString());
        payload.put("total", voices.size());
        payload.put("matched", matchedVoices.size());
        payload.put("default_voice", voices.isEmpty() ? null : MossTtsVoiceCatalog.voiceId(voices.get(0)));
        payload.put("filter", ttsVoiceFilterJson(filter));

        List<Map<String, Object>> voiceItems = new ArrayList<>();
        for (JsonNode voice : matchedVoices) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("default", voice.equals(voices.get(0)));
            item.put("voice", MossTtsVoiceCatalog.voiceId(voice));
            item.put("language", MossTtsVoiceCatalog.languageLabel(voice));
            item.put("gender", MossTtsVoiceCatalog.genderLabel(voice));
            item.put("group", MossTtsVoiceCatalog.group(voice));
            item.put("display_name", MossTtsVoiceCatalog.displayName(voice));
            item.put("selector", MossTtsVoiceCatalog.selectorExample(voice));
            voiceItems.add(item);
        }
        payload.put("voices", voiceItems);
        payload.put("examples", List.of(
                "--voice " + (voices.isEmpty() ? "Junhao" : MossTtsVoiceCatalog.voiceId(voices.get(0))),
                "--voice en",
                "--voice jp female",
                "--voice-search bitter"));
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload));
    }

    private Map<String, Object> ttsVoiceFilterJson(TtsVoiceListFilter filter) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("active", filter != null && filter.hasFilters());
        value.put("language", filter == null ? null : filter.language());
        value.put("gender", filter == null ? null : filter.gender());
        value.put("terms", filter == null ? List.of() : filter.terms());
        value.put("description", filter == null ? "(none)" : filter.describe());
        return value;
    }

    private TtsVoiceListFilter ttsVoiceListFilter() {
        String language = MossTtsVoiceCatalog.normalizeLanguageFilter(command.ttsVoiceLanguage);
        String gender = MossTtsVoiceCatalog.normalizeGenderFilter(command.ttsVoiceGender);
        List<String> terms = new ArrayList<>();

        if (command.ttsVoiceGender != null && !command.ttsVoiceGender.isBlank() && gender == null) {
            terms.addAll(MossTtsVoiceCatalog.filterTerms(command.ttsVoiceGender));
        }

        String selectorText = command.ttsVoiceSearch != null && !command.ttsVoiceSearch.isBlank()
                ? command.ttsVoiceSearch
                : command.ttsVoice;
        for (String term : MossTtsVoiceCatalog.filterTerms(selectorText)) {
            String termLanguage = MossTtsVoiceCatalog.normalizeLanguageFilter(term);
            if (termLanguage != null) {
                language = termLanguage;
                continue;
            }
            String termGender = MossTtsVoiceCatalog.normalizeGenderFilter(term);
            if (termGender != null) {
                gender = termGender;
                continue;
            }
            if (!Set.of("auto", "any", "all").contains(term)) {
                terms.add(term);
            }
        }

        return new TtsVoiceListFilter(language, gender, List.copyOf(terms));
    }

    int printTtsDiagnostics(String modelId, String finalLocalPath) {
        if (command.jsonMode) {
            return printTtsDiagnosticsJson(modelId, finalLocalPath);
        }
        int exitCode = 0;
        System.out.println("MOSS TTS diagnostics");
        System.out.println("===================");
        System.out.println("Requested model: " + nullToDash(modelId));
        System.out.println("Resolved path: " + nullToDash(finalLocalPath));

        Optional<Path> ttsDir = MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId);
        if (ttsDir.isEmpty()) {
            System.err.println();
            System.err.println("Error: resolved model is not a local MOSS TTS ONNX directory.");
            System.err.println("Expected files: " + MossTtsModelLayout.MANIFEST_FILE_NAME + " and "
                    + MossTtsModelLayout.METADATA_FILE_NAME);
            return 1;
        }

        Path modelDir = ttsDir.get().toAbsolutePath().normalize();
        System.out.println("TTS directory: " + CliPathUtils.displayPath(modelDir));
        Path manifestPath = modelDir.resolve(MossTtsModelLayout.MANIFEST_FILE_NAME);
        Path ttsMetaPath = modelDir.resolve(MossTtsModelLayout.METADATA_FILE_NAME);
        System.out.println("Manifest: " + diagnosticFileStatus(manifestPath));
        System.out.println("TTS metadata: " + diagnosticFileStatus(ttsMetaPath));

        MossTtsModelDiagnostics.Snapshot modelDiagnostics =
                MossTtsModelDiagnostics.inspect(modelDir);
        System.out.println("Voices: " + modelDiagnostics.voiceCount()
                + (modelDiagnostics.voiceCount() == 0 ? " (missing)" : ""));
        if (modelDiagnostics.defaultVoice() != null) {
            System.out.println("Voice groups: " + modelDiagnostics.voiceGroupSummary());
            MossTtsModelDiagnostics.VoiceSummary voice = modelDiagnostics.defaultVoice();
            System.out.println("Default voice: " + voice.id() + " (" + voice.displayName() + ")");
        } else {
            System.err.println("Manifest read error: " + modelDiagnostics.manifestError());
            exitCode = 1;
        }

        String selector = effectiveTtsVoiceSelector();
        if (selector != null && !selector.isBlank()) {
            System.out.println("Requested voice selector: " + selector);
        }
        if (command.prompt != null && !command.prompt.isBlank()) {
            System.out.println("Prompt preview: " + truncateDisplay(command.prompt.replaceAll("\\s+", " "), 80));
        }

        System.out.println();
        System.out.println("Companion codec");
        System.out.println("---------------");
        Optional<Path> codecDir = diagnosticCodecDirectory();
        if (codecDir.isPresent()) {
            Path dir = codecDir.get().toAbsolutePath().normalize();
            System.out.println("Codec directory: " + CliPathUtils.displayPath(dir));
            System.out.println("Codec metadata: " + diagnosticFileStatus(dir.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME)));
        } else {
            exitCode = 1;
            System.out.println("Codec directory: missing");
            if (command.ttsCodec != null && !command.ttsCodec.isBlank()) {
                System.out.println("Explicit codec ref: " + command.ttsCodec);
            } else if (command.offline) {
                System.out.println("Auto pull: disabled by --offline/--local");
            } else if (command.disableTtsAutoCodec) {
                System.out.println("Auto pull: disabled by --no-tts-auto-codec");
            } else {
                System.out.println("Auto pull: enabled for generation");
            }
            System.out.println("Install with: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
        }

        System.out.println();
        System.out.println("ONNX runtime caches");
        System.out.println("-------------------");
        printTtsOnnxCacheDiagnostics(modelDir, codecDir);

        System.out.println();
        System.out.println("Audio output");
        System.out.println("------------");
        RunCommand.AudioQualityPreset preset = command.audioQualityPreset();
        String effectiveFormat = command.effectiveAudioFormat(preset);
        String diagnosticFormat = diagnosticAudioFormat(effectiveFormat);
        Integer bitrate = command.effectiveAudioBitrateKbps(preset, diagnosticFormat);
        Integer compression = command.effectiveFlacCompression(preset, diagnosticFormat);
        System.out.println("Format: " + diagnosticFormat);
        System.out.println("Quality preset: " + (command.audioQuality == null || command.audioQuality.isBlank()
                ? "auto/default"
                : command.audioQuality.trim().toLowerCase(Locale.ROOT)));
        String encoderStatus = diagnosticAudioEncoderStatus(diagnosticFormat);
        System.out.println("Encoder: " + encoderStatus);
        if (encoderStatus.startsWith("missing")) {
            exitCode = 1;
        }
        if (bitrate != null) {
            System.out.println("Bitrate: " + bitrate + " kbps");
        }
        if (compression != null) {
            System.out.println("FLAC compression: " + compression);
        }
        System.out.println("Channels: " + (command.audioChannels == null || command.audioChannels.isBlank()
                ? "auto"
                : command.audioChannels.trim().toLowerCase(Locale.ROOT)));
        System.out.println("Polish: " + (command.disableAudioPolish ? "disabled" : "enabled")
                + (command.disableAudioNormalize ? " (peak normalization disabled)" : ""));
        System.out.println("Trim silence: " + (command.audioTrimSilence ? "enabled" : "disabled")
                + (command.audioTrimSilence
                        ? " (threshold " + (command.audioTrimDbfs == null ? "-48" : formatRangeNumber(command.audioTrimDbfs))
                                + " dBFS, padding "
                                + (command.audioTrimPaddingMs == null ? "25" : formatRangeNumber(command.audioTrimPaddingMs))
                                + " ms)"
                        : ""));
        System.out.println("Low-latency PCM: " + (command.disableLowLatencyAudio ? "disabled" : "enabled when streaming PCM/live audio"));
        System.out.println("Live audio: " + (command.liveAudio ? "requested" : "off"));

        System.out.println();
        System.out.println("Suling audio backends");
        System.out.println("---------------------");
        try {
            System.out.println("Formats: " + String.join(", ", Suling.supportedAudioFormats()));
            System.out.println(indentBlock(Suling.diagnostics(), "  "));
        } catch (Throwable e) {
            exitCode = 1;
            System.out.println("Diagnostics unavailable: " + e.getMessage());
        }

        System.out.println();
        System.out.println(exitCode == 0 ? "Status: ready" : "Status: attention needed");
        return exitCode;
    }

    Optional<Path> diagnosticCodecDirectory() {
        if (command.ttsCodec != null && !command.ttsCodec.isBlank()) {
            try {
                return MossAudioTokenizerModelLayout.directoryFrom(Path.of(resolveTtsCodecReference(command.ttsCodec)));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return findInstalledMossAudioCodecDir();
    }

    private int printTtsDiagnosticsJson(String modelId, String finalLocalPath) {
        int exitCode = 0;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "moss_tts_diagnostics");
        payload.put("requested_model", modelId);
        payload.put("resolved_path", finalLocalPath);

        Optional<Path> ttsDir = MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId);
        if (ttsDir.isEmpty()) {
            payload.put("status", "attention_needed");
            payload.put("error", "resolved model is not a local MOSS TTS ONNX directory");
            printJsonPayload(payload);
            return 1;
        }

        Path modelDir = ttsDir.get().toAbsolutePath().normalize();
        Path manifestPath = modelDir.resolve(MossTtsModelLayout.MANIFEST_FILE_NAME);
        Path ttsMetaPath = modelDir.resolve(MossTtsModelLayout.METADATA_FILE_NAME);
        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("directory", modelDir.toString());
        tts.put("manifest", diagnosticFileJson(manifestPath));
        tts.put("metadata", diagnosticFileJson(ttsMetaPath));
        payload.put("tts", tts);

        MossTtsModelDiagnostics.Snapshot modelDiagnostics =
                MossTtsModelDiagnostics.inspect(modelDir);
        Map<String, Object> voiceInfo = new LinkedHashMap<>();
        voiceInfo.put("count", modelDiagnostics.voiceCount());
        voiceInfo.put("groups", modelDiagnostics.voiceGroups());
        if (modelDiagnostics.defaultVoice() != null) {
            MossTtsModelDiagnostics.VoiceSummary voice = modelDiagnostics.defaultVoice();
            Map<String, Object> defaultVoice = new LinkedHashMap<>();
            defaultVoice.put("voice", voice.id());
            defaultVoice.put("display_name", voice.displayName());
            defaultVoice.put("language", voice.language());
            defaultVoice.put("gender", voice.gender());
            voiceInfo.put("default", defaultVoice);
        } else {
            exitCode = 1;
        }
        if (modelDiagnostics.manifestError() != null) {
            tts.put("manifest_error", modelDiagnostics.manifestError());
        }
        String selector = effectiveTtsVoiceSelector();
        if (selector != null && !selector.isBlank()) {
            voiceInfo.put("requested_selector", selector);
        }
        tts.put("voices", voiceInfo);

        Optional<Path> codecDir = diagnosticCodecDirectory();
        Map<String, Object> codec = new LinkedHashMap<>();
        if (codecDir.isPresent()) {
            Path dir = codecDir.get().toAbsolutePath().normalize();
            codec.put("present", true);
            codec.put("directory", dir.toString());
            codec.put("metadata", diagnosticFileJson(dir.resolve(MossAudioTokenizerModelLayout.METADATA_FILE_NAME)));
        } else {
            codec.put("present", false);
            codec.put("install_command", "aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            exitCode = 1;
        }
        payload.put("codec", codec);
        payload.put("onnx_caches", ttsOnnxCacheDiagnosticsJson(modelDir, codecDir));

        RunCommand.AudioQualityPreset preset = command.audioQualityPreset();
        String diagnosticFormat = diagnosticAudioFormat(command.effectiveAudioFormat(preset));
        String encoderStatus = diagnosticAudioEncoderStatus(diagnosticFormat);
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", diagnosticFormat);
        audio.put("quality_preset", command.audioQuality == null || command.audioQuality.isBlank()
                ? "auto/default"
                : command.audioQuality.trim().toLowerCase(Locale.ROOT));
        audio.put("encoder", encoderStatus);
        audio.put("channels", command.audioChannels == null || command.audioChannels.isBlank()
                ? "auto"
                : command.audioChannels.trim().toLowerCase(Locale.ROOT));
        audio.put("polish", !command.disableAudioPolish);
        audio.put("trim_silence", command.audioTrimSilence);
        audio.put("live_audio", command.liveAudio);
        if (encoderStatus.startsWith("missing")) {
            exitCode = 1;
        }
        payload.put("audio", audio);

        try {
            Map<String, Object> suling = new LinkedHashMap<>();
            suling.put("formats", Suling.supportedAudioFormats());
            suling.put("diagnostics", Suling.diagnostics());
            payload.put("suling", suling);
        } catch (Throwable e) {
            payload.put("suling", Map.of("error", e.getMessage()));
            exitCode = 1;
        }

        payload.put("status", exitCode == 0 ? "ready" : "attention_needed");
        printJsonPayload(payload);
        return exitCode;
    }

    int warmupMossTts(String modelId, String finalLocalPath) {
        if (!command.jsonMode) {
            System.out.println("MOSS TTS warmup");
            System.out.println("==============");
        }
        Optional<Path> ttsDir = MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId);
        if (ttsDir.isEmpty()) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_warmup", "resolved model is not a local MOSS TTS ONNX directory");
                return 1;
            }
            System.err.println("Error: resolved model is not a local MOSS TTS ONNX directory.");
            return 1;
        }
        Optional<Path> codecDir = diagnosticCodecDirectory();
        if (codecDir.isEmpty()) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_warmup", "MOSS TTS companion codec is not installed");
                return 1;
            }
            System.err.println("Error: MOSS TTS companion codec is not installed.");
            System.err.println("Install with: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return 1;
        }

        Path modelDir = ttsDir.get().toAbsolutePath().normalize();
        Path codecPath = codecDir.get().toAbsolutePath().normalize();
        int threads = onnxProviderThreads();
        if (!command.jsonMode) {
            System.out.println("TTS directory: " + CliPathUtils.displayPath(modelDir));
            System.out.println("Codec directory: " + CliPathUtils.displayPath(codecPath));
            System.out.println("Threads: " + threads);
        }
        try {
            if (command.mossTtsWarmupRunner == null || command.mossTtsWarmupRunner.isUnsatisfied()) {
                throw new IllegalStateException("MOSS TTS ONNX runner is not available in this runtime");
            }
            MossTtsOnnxRunner.MossTtsWarmup warmup =
                    command.mossTtsWarmupRunner.get().warmup(modelDir, codecPath, threads);
            Map<String, Object> metadata = warmup.metadata();
            if (command.jsonMode) {
                printMossTtsWarmupJson(modelId, modelDir, codecPath, threads, warmup, metadata);
                return 0;
            }
            System.out.println("Warmup duration: " + String.format(Locale.ROOT, "%.2fs", warmup.durationMs() / 1000.0));
            String graphCache = onnxOptimizedCacheDetail(metadata);
            if (graphCache != null) {
                System.out.println("Optimized graph cache: " + graphCache);
            }
            String sessionCache = onnxSessionCacheDetail(metadata);
            if (sessionCache != null) {
                System.out.println("Session cache: " + sessionCache);
            }
            String assetCache = onnxAssetCacheDetail(metadata);
            if (assetCache != null) {
                System.out.println("Asset cache: " + assetCache);
            }
            System.out.println("Opened sessions: " + Math.round(defaultedDouble(
                    metadata,
                    "onnx_optimized_model_cache_sessions")));
            System.out.println("Status: warmed");
            return 0;
        } catch (Exception e) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_warmup", "MOSS TTS warmup failed: " + e.getMessage());
                return 1;
            }
            System.err.println("Error: MOSS TTS warmup failed: " + e.getMessage());
            return 1;
        }
    }

    int onnxProviderThreads() {
        String raw = System.getProperty("aqli.providers.onnx.threads");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AQLI_ONNX_THREADS");
        }
        try {
            return Math.max(1, raw == null || raw.isBlank()
                    ? 4
                    : Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 4;
        }
    }

    int pruneMossTtsOptimizedCache(String modelId, String finalLocalPath) {
        boolean dryRun = !command.ttsCachePruneApply;
        Optional<Path> ttsDir = MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId);
        if (ttsDir.isEmpty()) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_cache_prune", "resolved model is not a local MOSS TTS ONNX directory");
                return 1;
            }
            System.err.println("Error: resolved model is not a local MOSS TTS ONNX directory.");
            return 1;
        }
        Optional<Path> codecDir = diagnosticCodecDirectory();
        if (codecDir.isEmpty()) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_cache_prune", "MOSS TTS companion codec is not installed");
                return 1;
            }
            System.err.println("Error: MOSS TTS companion codec is not installed.");
            System.err.println("Install with: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return 1;
        }

        Path modelDir = ttsDir.get().toAbsolutePath().normalize();
        Path codecPath = codecDir.get().toAbsolutePath().normalize();
        Path cacheRoot = MossTtsCacheSettings.load().optimizedGraphCacheRoot();
        try {
            MossTtsOptimizedCacheMaintenance.Result result =
                    new MossTtsOptimizedCacheMaintenance().prune(modelDir, codecPath, cacheRoot, dryRun);
            if (command.jsonMode) {
                printMossTtsCachePruneJson(
                        modelId,
                        modelDir,
                        codecPath,
                        cacheRoot,
                        dryRun,
                        result.before(),
                        result.after(),
                        result.operations(),
                        result.affectedBytes(),
                        result.errors());
            } else {
                printMossTtsCachePrune(
                        modelDir,
                        codecPath,
                        cacheRoot,
                        dryRun,
                        result.before(),
                        result.after(),
                        result.operations(),
                        result.affectedBytes(),
                        result.errors());
            }
            return result.errors() == 0 ? 0 : 1;
        } catch (Exception e) {
            if (command.jsonMode) {
                printTtsJsonError("moss_tts_cache_prune", "MOSS TTS cache prune failed: " + e.getMessage());
                return 1;
            }
            System.err.println("Error: MOSS TTS cache prune failed: " + e.getMessage());
            return 1;
        }
    }

    private void printMossTtsCachePruneJson(
            String modelId,
            Path modelDir,
            Path codecPath,
            Path cacheRoot,
            boolean dryRun,
            OptimizedCacheDirectoryScanner.Stats before,
            OptimizedCacheDirectoryScanner.Stats after,
            List<OptimizedCachePruner.Operation> operations,
            long affectedBytes,
            int errors) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "moss_tts_cache_prune");
        payload.put("status", errors == 0 ? (dryRun ? "dry_run" : "pruned") : "error");
        payload.put("dry_run", dryRun);
        payload.put("requested_model", modelId);
        payload.put("tts_directory", modelDir.toString());
        payload.put("codec_directory", codecPath.toString());
        payload.put("cache_root", cacheRoot.toAbsolutePath().normalize().toString());
        payload.put("affected_files", operations.size());
        payload.put("affected_optimized_graph_files", countOperations(operations, "optimized_graph"));
        payload.put("affected_external_data_sidecar_files", countOperations(operations, "external_data_sidecar"));
        payload.put("affected_size_bytes", affectedBytes);
        payload.put("errors", errors);
        payload.put("before", before.toJson());
        payload.put("after", after.toJson());
        payload.put("operations", operations.stream().map(OptimizedCachePruner.Operation::toJson).toList());
        printJsonPayload(payload);
    }

    private void printMossTtsCachePrune(
            Path modelDir,
            Path codecPath,
            Path cacheRoot,
            boolean dryRun,
            OptimizedCacheDirectoryScanner.Stats before,
            OptimizedCacheDirectoryScanner.Stats after,
            List<OptimizedCachePruner.Operation> operations,
            long affectedBytes,
            int errors) {
        System.out.println("MOSS TTS optimized cache prune");
        System.out.println("=============================");
        System.out.println("TTS directory: " + CliPathUtils.displayPath(modelDir));
        System.out.println("Codec directory: " + CliPathUtils.displayPath(codecPath));
        System.out.println("Cache root: " + CliPathUtils.displayPath(cacheRoot));
        System.out.println("Mode: " + (dryRun ? "dry-run (use --tts-cache-prune-apply to delete)" : "apply"));
        System.out.println("Before: " + before.currentOptimizedGraphFiles() + " current, "
                + before.staleOptimizedGraphFiles() + " stale, "
                + before.unrelatedOptimizedGraphFiles() + " unrelated, "
                + before.optimizedGraphFiles() + " optimized total, "
                + before.externalDataSidecarFiles() + " sidecar(s), "
                + formatBytes(before.sizeBytes()));
        if (before.externalDataSidecarFiles() > 0) {
            System.out.println("Sidecar prune policy: " + before.externalDataSidecarPrunePolicy());
            if (before.retainedExternalDataSidecarFiles() > 0) {
                System.out.println("Retained external data sidecars: "
                        + before.retainedExternalDataSidecarFiles() + " file(s), "
                        + formatBytes(before.retainedExternalDataSidecarSizeBytes()) + " ("
                        + formatDiagnosticList(before.retainedExternalDataSidecarFileNames(), 10) + ")");
            }
        }
        if (before.unrelatedOptimizedGraphFiles() > 0) {
            System.out.println("Unrelated optimized graph files kept: "
                    + formatDiagnosticList(before.unrelatedOptimizedGraphFileNames(), 10));
        }
        List<OptimizedCachePruner.Operation> graphOperations = operationsOfKind(operations, "optimized_graph");
        List<OptimizedCachePruner.Operation> sidecarOperations = operationsOfKind(operations, "external_data_sidecar");
        if (graphOperations.isEmpty()) {
            System.out.println("Stale optimized graph files: none");
        } else {
            System.out.println((dryRun ? "Would delete" : "Deleted") + " stale optimized graph files: "
                    + graphOperations.size() + " file(s), " + formatBytes(totalOperationBytes(graphOperations)));
            printCachePruneOperations(graphOperations, dryRun, 10);
        }
        if (sidecarOperations.isEmpty()) {
            System.out.println("Orphan external data sidecars: none");
        } else {
            System.out.println((dryRun ? "Would delete" : "Deleted") + " orphan external data sidecars: "
                    + sidecarOperations.size() + " file(s), " + formatBytes(totalOperationBytes(sidecarOperations)));
            printCachePruneOperations(sidecarOperations, dryRun, 10);
        }
        if (!dryRun) {
            System.out.println("After: " + after.currentOptimizedGraphFiles() + " current, "
                    + after.staleOptimizedGraphFiles() + " stale, "
                    + after.unrelatedOptimizedGraphFiles() + " unrelated, "
                    + after.optimizedGraphFiles() + " optimized total, "
                    + after.externalDataSidecarFiles() + " sidecar(s), "
                    + formatBytes(after.sizeBytes()));
        }
        System.out.println(errors == 0
                ? "Status: " + (dryRun ? "dry run complete" : "pruned")
                : "Status: completed with " + errors + " error(s)");
    }

    private void printCachePruneOperations(
            List<OptimizedCachePruner.Operation> operations,
            boolean dryRun,
            int limit) {
        for (OptimizedCachePruner.Operation operation : operations.stream().limit(limit).toList()) {
            String status = dryRun
                    ? "would delete"
                    : (operation.deleted() ? "deleted" : (operation.error() == null ? "missing" : "failed"));
            System.out.println("  " + operation.path().getFileName()
                    + " (" + formatBytes(operation.sizeBytes()) + ", " + status
                    + (operation.error() == null ? "" : ": " + operation.error())
                    + ")");
        }
        if (operations.size() > limit) {
            System.out.println("  ... +" + (operations.size() - limit) + " more");
        }
    }

    private int countOperations(List<OptimizedCachePruner.Operation> operations, String kind) {
        return (int) operations.stream()
                .filter(operation -> kind.equals(operation.kind()))
                .count();
    }

    private long totalOperationBytes(List<OptimizedCachePruner.Operation> operations) {
        long total = 0L;
        for (OptimizedCachePruner.Operation operation : operations) {
            total += operation.sizeBytes();
        }
        return total;
    }

    private List<OptimizedCachePruner.Operation> operationsOfKind(List<OptimizedCachePruner.Operation> operations, String kind) {
        return operations.stream()
                .filter(operation -> kind.equals(operation.kind()))
                .toList();
    }

    private void printMossTtsWarmupJson(
            String modelId,
            Path modelDir,
            Path codecPath,
            int threads,
            MossTtsOnnxRunner.MossTtsWarmup warmup,
            Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "moss_tts_warmup");
        payload.put("status", "warmed");
        payload.put("requested_model", modelId);
        payload.put("tts_directory", modelDir.toString());
        payload.put("codec_directory", codecPath.toString());
        payload.put("threads", threads);
        payload.put("duration_ms", warmup.durationMs());
        payload.put("duration_seconds", warmup.durationMs() / 1000.0);
        payload.put("optimized_graph_cache", onnxOptimizedCacheDetail(metadata));
        payload.put("session_cache", onnxSessionCacheDetail(metadata));
        payload.put("asset_cache", onnxAssetCacheDetail(metadata));
        payload.put("opened_sessions", Math.round(defaultedDouble(
                metadata,
                "onnx_optimized_model_cache_sessions")));
        payload.put("metadata", metadata);
        printJsonPayload(payload);
    }

    private void printTtsJsonError(String type, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("status", "error");
        payload.put("error", message);
        printJsonPayload(payload);
    }

    private Map<String, Object> diagnosticFileJson(Path path) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("path", path == null ? null : path.toAbsolutePath().normalize().toString());
        boolean present = path != null && Files.isRegularFile(path);
        value.put("present", present);
        if (present) {
            try {
                value.put("size_bytes", Files.size(path));
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    private Map<String, Object> ttsOnnxCacheDiagnosticsJson(Path modelDir, Optional<Path> codecDir) {
        MossTtsOnnxCacheDiagnostics.Snapshot snapshot =
                MossTtsOnnxCacheDiagnostics.inspect(modelDir, codecDir, MossTtsCacheSettings.load());
        MossTtsCacheSettings settings = snapshot.settings();
        Path cacheRoot = settings.optimizedGraphCacheRoot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("optimized_graph_cache_enabled", settings.optimizedGraphCacheEnabled());
        payload.put("graph_cache_root", cacheRoot.toString());
        payload.put("onnx_runtime_log_level", settings.onnxRuntimeLogLevel());

        if (!snapshot.graphsAvailable()) {
            payload.put("graphs_available", false);
            if (snapshot.graphError() != null) {
                payload.put("graph_error", snapshot.graphError());
            }
        } else {
            List<Map<String, Object>> graphItems = new ArrayList<>();
            for (MossTtsOnnxCacheDiagnostics.GraphStatus graph : snapshot.graphs()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", graph.graph().label());
                item.put("state", graph.warm() ? "warm" : "cold");
                item.put("source_path", graph.graph().path().toAbsolutePath().normalize().toString());
                item.put("optimized_path", graph.optimizedPath().toAbsolutePath().normalize().toString());
                item.put("optimized_file", graph.optimizedPath().getFileName().toString());
                item.put("external_data_sidecars", graph.externalDataSidecars());
                graphItems.add(item);
            }
            payload.put("graphs_available", true);
            payload.put("warmable_graphs", snapshot.graphs().size());
            payload.put("normal_generation_graphs", 4);
            payload.put("optimized_graph_files_present", snapshot.warmGraphCount());
            payload.put("graphs", graphItems);
        }
        payload.put("cache_directory", snapshot.cacheDirectory().toJson());
        payload.put("session_cache", Map.of(
                "enabled", settings.sessionCacheEnabled(),
                "max_entries", settings.sessionCacheMaxEntries(),
                "scope", "in-process"));
        payload.put("asset_cache", Map.of(
                "enabled", settings.assetCacheEnabled(),
                "max_entries", settings.assetCacheMaxEntries(),
                "scope", "in-process"));
        return payload;
    }

    private void printTtsOnnxCacheDiagnostics(Path modelDir, Optional<Path> codecDir) {
        MossTtsOnnxCacheDiagnostics.Snapshot snapshot =
                MossTtsOnnxCacheDiagnostics.inspect(modelDir, codecDir, MossTtsCacheSettings.load());
        MossTtsCacheSettings settings = snapshot.settings();
        Path cacheRoot = settings.optimizedGraphCacheRoot();
        System.out.println("Optimized graph cache: "
                + (settings.optimizedGraphCacheEnabled() ? "enabled" : "disabled"));
        System.out.println("Graph cache root: " + CliPathUtils.displayPath(cacheRoot));
        System.out.println("ONNX Runtime log level: " + settings.onnxRuntimeLogLevel());

        if (codecDir.isEmpty()) {
            System.out.println("Optimized graph files: unavailable until companion codec is installed");
        } else if (!snapshot.graphsAvailable()) {
            System.out.println("Optimized graph files: unavailable (" + snapshot.graphError() + ")");
        } else {
            List<MossTtsOnnxCacheDiagnostics.GraphStatus> graphs = snapshot.graphs();
            System.out.println("Warmable graphs: " + graphs.size() + " (normal generation opens 4)");
            System.out.println("Optimized graph files: " + snapshot.warmGraphCount() + "/" + graphs.size()
                    + (settings.optimizedGraphCacheEnabled() ? " present" : " present but cache disabled"));
            for (MossTtsOnnxCacheDiagnostics.GraphStatus graph : graphs) {
                System.out.println("  " + graph.graph().label() + ": "
                        + (graph.warm() ? "warm" : "cold")
                        + " -> " + graph.optimizedPath().getFileName()
                        + (graph.externalDataSidecars() > 0
                                ? " (" + graph.externalDataSidecars() + " external data sidecars)"
                                : ""));
            }
        }
        printOptimizedCacheDirectoryStats(snapshot.cacheDirectory());

        System.out.println("Session cache: "
                + (settings.sessionCacheEnabled() && settings.sessionCacheMaxEntries() > 0 ? "enabled" : "disabled")
                + " (max entries " + settings.sessionCacheMaxEntries() + ", in-process)");
        System.out.println("Asset cache: "
                + (settings.assetCacheEnabled() && settings.assetCacheMaxEntries() > 0 ? "enabled" : "disabled")
                + " (max entries " + settings.assetCacheMaxEntries() + ", in-process)");
    }

    private void printOptimizedCacheDirectoryStats(OptimizedCacheDirectoryScanner.Stats stats) {
        if (!stats.exists()) {
            System.out.println("Cache directory files: not created yet");
            return;
        }
        System.out.println("Cache directory files: " + stats.fileCount()
                + " file(s), " + formatBytes(stats.sizeBytes()));
        System.out.println("Optimized graph cache inventory: "
                + stats.currentOptimizedGraphFiles() + " current, "
                + stats.staleOptimizedGraphFiles() + " stale, "
                + stats.unrelatedOptimizedGraphFiles() + " unrelated, "
                + stats.optimizedGraphFiles() + " optimized total, "
                + stats.externalDataSidecarFiles() + " external data sidecar(s)");
        if (stats.orphanExternalDataSidecarFiles() > 0) {
            System.out.println("Orphan external data sidecars: "
                    + formatDiagnosticList(stats.orphanExternalDataSidecarFileNames(), 5));
        }
        if (stats.externalDataSidecarFiles() > 0) {
            System.out.println("External data sidecar prune policy: " + stats.externalDataSidecarPrunePolicy());
            if (stats.retainedExternalDataSidecarFiles() > 0) {
                System.out.println("Retained external data sidecars: "
                        + stats.retainedExternalDataSidecarFiles() + " file(s), "
                        + formatBytes(stats.retainedExternalDataSidecarSizeBytes()) + " ("
                        + formatDiagnosticList(stats.retainedExternalDataSidecarFileNames(), 5) + ")");
            }
        }
        if (stats.staleOptimizedGraphFiles() > 0) {
            System.out.println("Stale optimized graph files: "
                    + formatDiagnosticList(stats.staleOptimizedGraphFileNames(), 5));
        }
        if (stats.unrelatedOptimizedGraphFiles() > 0) {
            System.out.println("Unrelated optimized graph files kept: "
                    + formatDiagnosticList(stats.unrelatedOptimizedGraphFileNames(), 5));
        }
    }

    private String diagnosticAudioEncoderStatus(String format) {
        return switch (format == null ? "" : format.toLowerCase(Locale.ROOT)) {
            case "wav" -> "ready (pure Java)";
            case "flac" -> FlacLibraryCheck.isAvailable()
                    ? "ready (libFLAC " + FlacLibraryCheck.getVersion() + ")"
                    : "missing libFLAC; install libFLAC or use --audio-format wav";
            case "mp3" -> FfmpegAudioEncoder.isMp3EncodingAvailable()
                    ? "ready (FFmpeg/libmp3lame)"
                    : "missing FFmpeg/libmp3lame; install FFmpeg libraries or use --audio-format wav";
            default -> "missing unsupported format";
        };
    }

    private String diagnosticAudioFormat(String effectiveFormat) {
        if (effectiveFormat != null && !effectiveFormat.isBlank()) {
            return effectiveFormat;
        }
        String explicitExt = command.normalizeExtension(command.extension);
        if (command.isKnownAudioExtension(explicitExt)) {
            return explicitExt;
        }
        try {
            String outputExt = command.pathExtension(command.outputPath == null || command.outputPath.isBlank() ? null : Path.of(command.outputPath));
            if (command.isKnownAudioExtension(outputExt)) {
                return outputExt;
            }
        } catch (Exception ignored) {
        }
        return "wav";
    }

    private String diagnosticFileStatus(Path path) {
        if (path == null) {
            return "missing";
        }
        if (!Files.exists(path)) {
            return "missing (" + CliPathUtils.displayPath(path) + ")";
        }
        if (Files.isRegularFile(path)) {
            try {
                return "present (" + formatBytes(Files.size(path)) + ")";
            } catch (Exception ignored) {
                return "present";
            }
        }
        return "present (directory)";
    }

    String resolveTtsCodecReference(String ref) {
        String value = ref == null ? "" : ref.trim();
        if (value.isEmpty()) {
            return value;
        }

        try {
            Path direct = Paths.get(value);
            if (Files.exists(direct)) {
                return direct.toAbsolutePath().normalize().toString();
            }
        } catch (Exception ignored) {
            // Not a local path; try the model index below.
        }

        try {
            Optional<LocalModelIndex.Entry> indexed = LocalModelIndex.find(value);
            if (indexed.isPresent()) {
                String path = indexed.get().path;
                if (path != null && !path.isBlank() && Files.exists(Path.of(path))) {
                    return Path.of(path).toAbsolutePath().normalize().toString();
                }
            }
        } catch (Exception ignored) {
            // Provider will emit the final codec-not-found diagnostic.
        }

        return value;
    }

    boolean ensureMossTtsCodecAvailableIfNeeded(String modelId, String finalLocalPath) {
        if (command.providerId != null && !command.providerId.isBlank() && !"onnx".equalsIgnoreCase(command.providerId)) {
            return true;
        }
        if (command.ttsCodec != null && !command.ttsCodec.isBlank()) {
            return true;
        }
        if (MossTtsModelLayout.resolveDirectory(finalLocalPath, modelId).isEmpty()) {
            return true;
        }
        Optional<Path> installedCodec = findInstalledMossAudioCodecDir();
        if (installedCodec.isPresent()) {
            return true;
        }
        if (command.offline) {
            System.err.println("Error: MOSS TTS companion codec is not installed, and --offline/--local disables automatic downloads.");
            System.err.println("Run once online: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return false;
        }
        if (command.disableTtsAutoCodec) {
            System.err.println("Error: MOSS TTS companion codec is not installed, and automatic codec pull is disabled.");
            System.err.println("Pull it with: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return false;
        }

        System.out.println("MOSS TTS companion codec not found; pulling " + MossAudioTokenizerModelLayout.REPOSITORY_ID + "...");
        try {
            pullMossAudioCodecRepository();
            LocalModelIndex.refreshFromDisk();
        } catch (Exception e) {
            System.err.println("Error: Failed to automatically pull MOSS TTS companion codec: " + e.getMessage());
            System.err.println("Pull it manually with: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return false;
        }

        Optional<Path> verifiedCodec = findInstalledMossAudioCodecDir();
        if (verifiedCodec.isEmpty()) {
            System.err.println("Error: MOSS TTS companion codec pull completed, but local files were not detected.");
            System.err.println("Check: aqli pull " + MossAudioTokenizerModelLayout.REPOSITORY_ID);
            return false;
        }
        System.out.println("Installed MOSS TTS companion codec to: " + CliPathUtils.displayPath(verifiedCodec.get()));
        return true;
    }

    Optional<Path> findInstalledMossAudioCodecDir() {
        List<Path> indexedPaths = new ArrayList<>();
        for (String ref : List.of(MossAudioTokenizerModelLayout.REPOSITORY_ID, MossAudioTokenizerModelLayout.MODEL_SPEC)) {
            try {
                LocalModelIndex.find(ref)
                        .map(entry -> entry.path)
                        .filter(path -> path != null && !path.isBlank())
                        .map(Path::of)
                        .ifPresent(indexedPaths::add);
            } catch (Exception ignored) {
                // Fall back to the model repository scan.
            }
        }
        return MossAudioTokenizerModelLocator.findInstalled(
                AqliHome.path("models"),
                indexedPaths,
                MossAudioTokenizerModelLayout.REPOSITORY_ID);
    }

    private void pullMossAudioCodecRepository() throws Exception {
        if (command.huggingFaceRepository == null || command.huggingFaceRepository.isUnsatisfied()) {
            throw new IllegalStateException("Hugging Face repository service is not available in this runtime");
        }
        try (tech.kayys.aqli.cli.commands.progress.TerminalDownloadProgressRenderer progress =
                new tech.kayys.aqli.cli.commands.progress.TerminalDownloadProgressRenderer(0)) {
            command.huggingFaceRepository.get().pullRepository(
                    MossAudioTokenizerModelLayout.REPOSITORY_ID,
                    "main",
                    null,
                    "moss-audio-tokenizer",
                    Map.of("fallback", "automatic-moss-tts-codec"),
                    progress);
        }
    }

    private String effectiveTtsVoiceSelector() {
        return command.effectiveTtsVoiceSelector();
    }

    private static String indentBlock(String text, String indent) {
        if (text == null || text.isBlank()) {
            return indent + "(none)";
        }
        return indent + text.stripTrailing().replace("\n", "\n" + indent);
    }

    private static String onnxOptimizedCacheDetail(Map<String, Object> metadata) {
        return AudioDetailsFormatter.format(metadata);
    }

    private static String onnxSessionCacheDetail(Map<String, Object> metadata) {
        String state = metadataText(metadata, "onnx_session_cache_state");
        if (state == null || state.isBlank() || "disabled".equalsIgnoreCase(state)) {
            return null;
        }
        if ("hit".equalsIgnoreCase(state)) {
            return "onnx sessions hot";
        }
        if ("miss".equalsIgnoreCase(state)) {
            return "onnx sessions warmed";
        }
        return null;
    }

    private static String onnxAssetCacheDetail(Map<String, Object> metadata) {
        String state = metadataText(metadata, "onnx_asset_cache_state");
        if (state == null || state.isBlank() || "disabled".equalsIgnoreCase(state)) {
            return null;
        }
        if ("hit".equalsIgnoreCase(state)) {
            return "onnx assets hot";
        }
        if ("miss".equalsIgnoreCase(state)) {
            return "onnx assets warmed";
        }
        return null;
    }

    private static double defaultedDouble(Map<String, Object> metadata, String key) {
        Double value = metadataDouble(metadata, key);
        return value == null ? 0.0 : value;
    }

    private static Double metadataDouble(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String formatDiagnosticList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        int safeLimit = Math.max(1, limit);
        if (values.size() <= safeLimit) {
            return String.join(", ", values);
        }
        return String.join(", ", values.subList(0, safeLimit))
                + ", ... +" + (values.size() - safeLimit) + " more";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "B", "KiB", "MiB", "GiB", "TiB" };
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static String formatRangeNumber(double value) {
        return value == Math.rint(value)
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String truncateDisplay(String value, int width) {
        if (value == null) {
            return "";
        }
        if (value.length() <= width) {
            return value;
        }
        return value.substring(0, Math.max(0, width - 3)) + "...";
    }

    private void printJsonPayload(Map<String, Object> payload) {
        try {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        } catch (Exception e) {
            System.err.println("{\"error\": \"failed to render json: " + e.getMessage() + "\"}");
        }
    }
}
