package tech.kayys.aqli.cli.commands.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Parses audio replay sidecars without depending on CLI command state. */
public final class AudioMetadataReplayParser {
    private static final Set<String> VALUE_FLAGS = Set.of(
            "--model", "--modelDir", "--modelFile", "--model-path", "--provider", "--runner", "--prompt",
            "--voice", "--tts-voice", "--audio-format", "--tts-audio-format", "--seed", "--output",
            "--audio-quality", "--tts-audio-quality", "--audio-bitrate", "--audio-bitrate-kbps",
            "--tts-bitrate", "--tts-bitrate-kbps", "--audio-channels", "--tts-audio-channels",
            "--tts-codec-decode", "--codec-decode", "--tts-max-frames", "--max-frames",
            "--tts-max-seconds", "--max-seconds", "--tts-duration", "--audio-gain-db", "--tts-gain-db",
            "--audio-peak-db", "--audio-peak-dbfs", "--tts-peak-db", "--audio-fade-ms", "--tts-fade-ms",
            "--audio-trim-db", "--audio-trim-dbfs", "--tts-trim-db", "--audio-trim-padding-ms",
            "--tts-trim-padding-ms", "--flac-compression");

    private final ObjectMapper mapper;

    public AudioMetadataReplayParser() {
        this(new ObjectMapper());
    }

    AudioMetadataReplayParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Plan parse(Path sidecarPath) throws IOException {
        JsonNode root = mapper.readTree(sidecarPath.toFile());
        if (root == null || !root.isObject()) {
            return null;
        }
        List<ReplayOption> options = new ArrayList<>();
        JsonNode args = root.get("replay_args");
        if (args != null && args.isArray()) {
            parseArgs(args, options);
        }
        String outputPath = lastOutput(options);
        appendPayloadFallback(root, options);
        if (outputPath == null || outputPath.isBlank()) {
            outputPath = text(root, "output_path");
        }
        return new Plan(options, outputPath);
    }

    private static void parseArgs(JsonNode args, List<ReplayOption> options) {
        for (int i = 0; i < args.size(); i++) {
            String flag = nodeText(args.get(i));
            if (flag == null || !flag.startsWith("--")) {
                continue;
            }
            switch (flag) {
                case "--model" -> options.add(string(Option.MODEL, argument(args, ++i, flag)));
                case "--modelDir" -> options.add(string(Option.MODEL_DIR, argument(args, ++i, flag)));
                case "--modelFile" -> options.add(string(Option.MODEL_FILE, argument(args, ++i, flag)));
                case "--model-path" -> options.add(string(Option.MODEL_PATH, argument(args, ++i, flag)));
                case "--provider" -> options.add(string(Option.PROVIDER, argument(args, ++i, flag)));
                case "--runner" -> options.add(string(Option.RUNNER, argument(args, ++i, flag)));
                case "--prompt" -> options.add(string(Option.PROMPT, argument(args, ++i, flag)));
                case "--voice", "--tts-voice" -> options.add(string(Option.VOICE, argument(args, ++i, flag)));
                case "--audio-format", "--tts-audio-format" ->
                        options.add(string(Option.AUDIO_FORMAT, argument(args, ++i, flag)));
                case "--seed" -> options.add(new ReplayOption(Option.SEED,
                        parseLong(flag, argument(args, ++i, flag))));
                case "--output" -> options.add(string(Option.OUTPUT, argument(args, ++i, flag)));
                case "--audio-quality", "--tts-audio-quality" ->
                        options.add(string(Option.AUDIO_QUALITY, argument(args, ++i, flag)));
                case "--audio-bitrate", "--audio-bitrate-kbps", "--tts-bitrate", "--tts-bitrate-kbps" ->
                        options.add(new ReplayOption(Option.AUDIO_BITRATE, parseInt(flag, argument(args, ++i, flag))));
                case "--audio-channels", "--tts-audio-channels" ->
                        options.add(string(Option.AUDIO_CHANNELS, argument(args, ++i, flag)));
                case "--tts-codec-decode", "--codec-decode" ->
                        options.add(string(Option.CODEC_DECODE, argument(args, ++i, flag)));
                case "--tts-max-frames", "--max-frames" ->
                        options.add(new ReplayOption(Option.MAX_FRAMES, parseInt(flag, argument(args, ++i, flag))));
                case "--tts-max-seconds", "--max-seconds", "--tts-duration" ->
                        options.add(new ReplayOption(Option.MAX_SECONDS, parseDouble(flag, argument(args, ++i, flag))));
                case "--audio-gain-db", "--tts-gain-db" ->
                        options.add(new ReplayOption(Option.GAIN_DB, parseDouble(flag, argument(args, ++i, flag))));
                case "--audio-peak-db", "--audio-peak-dbfs", "--tts-peak-db" ->
                        options.add(new ReplayOption(Option.PEAK_DB, parseDouble(flag, argument(args, ++i, flag))));
                case "--audio-fade-ms", "--tts-fade-ms" ->
                        options.add(new ReplayOption(Option.FADE_MS, parseDouble(flag, argument(args, ++i, flag))));
                case "--audio-trim-db", "--audio-trim-dbfs", "--tts-trim-db" ->
                        options.add(new ReplayOption(Option.TRIM_DBFS, parseDouble(flag, argument(args, ++i, flag))));
                case "--audio-trim-padding-ms", "--tts-trim-padding-ms" ->
                        options.add(new ReplayOption(Option.TRIM_PADDING_MS,
                                parseDouble(flag, argument(args, ++i, flag))));
                case "--flac-compression" ->
                        options.add(new ReplayOption(Option.FLAC_COMPRESSION, parseInt(flag, argument(args, ++i, flag))));
                case "--no-audio-polish", "--no-tts-audio-polish" ->
                        options.add(new ReplayOption(Option.DISABLE_AUDIO_POLISH, Boolean.TRUE));
                case "--no-audio-normalize", "--no-tts-audio-normalize" ->
                        options.add(new ReplayOption(Option.DISABLE_AUDIO_NORMALIZE, Boolean.TRUE));
                case "--audio-trim-silence", "--tts-trim-silence" ->
                        options.add(new ReplayOption(Option.TRIM_SILENCE, Boolean.TRUE));
                default -> {
                    if (VALUE_FLAGS.contains(flag) && i + 1 < args.size()) {
                        i++;
                    }
                }
            }
        }
    }

    private static void appendPayloadFallback(JsonNode root, List<ReplayOption> options) {
        addString(options, Option.MODEL, text(root, "model"));
        addString(options, Option.PROVIDER, text(root, "provider"));
        addString(options, Option.RUNNER, text(root, "runner"));
        addString(options, Option.PROMPT, text(root, "prompt"));
        addString(options, Option.VOICE, text(root, "voice_selector"));
        addString(options, Option.AUDIO_FORMAT, text(root, "audio_format"));

        JsonNode metadata = root.get("metadata");
        if (metadata != null && metadata.isObject()) {
            String seed = text(metadata, "tts_seed");
            if (seed != null && !seed.isBlank()) {
                options.add(new ReplayOption(Option.SEED, parseLong("metadata.tts_seed", seed)));
            }
            String format = text(metadata, "audio_format");
            if (format != null && !format.isBlank()) {
                addString(options, Option.AUDIO_FORMAT, format);
            }
            String bitrate = text(metadata, "audio_bitrate_kbps");
            if (bitrate != null && !bitrate.isBlank()) {
                options.add(new ReplayOption(Option.AUDIO_BITRATE,
                        parseInt("metadata.audio_bitrate_kbps", bitrate)));
            }
        }
    }

    private static String lastOutput(List<ReplayOption> options) {
        String result = null;
        for (ReplayOption option : options) {
            if (option.option() == Option.OUTPUT) result = (String) option.value();
        }
        return result;
    }

    private static String argument(JsonNode args, int index, String flag) {
        if (index >= args.size()) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        String value = nodeText(args.get(index));
        if (value == null) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        return node == null ? null : nodeText(node.get(field));
    }

    private static String nodeText(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asText();
    }

    private static void addString(List<ReplayOption> options, Option option, String value) {
        if (value != null && !value.isBlank()) options.add(string(option, value));
    }

    private static ReplayOption string(Option option, String value) {
        return new ReplayOption(option, value);
    }

    private static Long parseLong(String option, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + option + " value in replay metadata: " + value);
        }
    }

    private static Integer parseInt(String option, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + option + " value in replay metadata: " + value);
        }
    }

    private static Double parseDouble(String option, String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + option + " value in replay metadata: " + value);
        }
    }

    public enum Option {
        MODEL, MODEL_DIR, MODEL_FILE, MODEL_PATH, PROVIDER, RUNNER, PROMPT, VOICE, AUDIO_FORMAT, SEED, OUTPUT,
        AUDIO_QUALITY, AUDIO_BITRATE, AUDIO_CHANNELS, CODEC_DECODE, MAX_FRAMES, MAX_SECONDS, GAIN_DB, PEAK_DB,
        FADE_MS, TRIM_DBFS, TRIM_PADDING_MS, FLAC_COMPRESSION, DISABLE_AUDIO_POLISH, DISABLE_AUDIO_NORMALIZE,
        TRIM_SILENCE
    }

    public record ReplayOption(Option option, Object value) {
    }

    public record Plan(List<ReplayOption> options, String outputPath) {
        public Plan {
            options = List.copyOf(options);
        }
    }
}
