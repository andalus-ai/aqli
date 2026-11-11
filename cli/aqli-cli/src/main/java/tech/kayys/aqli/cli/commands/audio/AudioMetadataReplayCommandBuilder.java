package tech.kayys.aqli.cli.commands.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the reproducible CLI argument vector for an audio metadata replay.
 *
 * <p>This class owns command serialization only. Sidecar parsing and CLI option
 * precedence remain responsibilities of the replay loader and {@code RunCommand}.</p>
 */
public final class AudioMetadataReplayCommandBuilder {

    private AudioMetadataReplayCommandBuilder() {
    }

    public static List<String> build(Request request) {
        List<String> args = new ArrayList<>();
        args.add("aqli");
        args.add("run");
        addModelArgs(args, request.model());
        add(args, "--provider", request.provider());
        add(args, "--runner", request.runner());
        add(args, "--prompt", request.prompt());
        add(args, "--voice", request.voice());
        add(args, "--audio-format", request.audioFormat());
        add(args, "--seed", request.seed());
        add(args, "--output", request.outputPath());
        add(args, "--audio-quality", request.audioQuality());
        add(args, "--audio-bitrate", request.audioBitrateKbps());
        add(args, "--audio-channels", request.audioChannels());
        add(args, "--tts-codec-decode", request.codecDecode());
        add(args, "--tts-max-frames", request.maxFrames());
        add(args, "--tts-max-seconds", request.maxSeconds());
        add(args, "--audio-gain-db", request.gainDb());
        add(args, "--audio-peak-db", request.peakDb());
        add(args, "--audio-fade-ms", request.fadeMs());
        add(args, "--audio-trim-db", request.trimDbfs());
        add(args, "--audio-trim-padding-ms", request.trimPaddingMs());
        add(args, "--flac-compression", request.flacCompression());
        if (request.disableAudioPolish()) {
            args.add("--no-audio-polish");
        }
        if (request.disableAudioNormalize()) {
            args.add("--no-audio-normalize");
        }
        if (request.trimSilence()) {
            args.add("--audio-trim-silence");
        }
        return List.copyOf(args);
    }

    private static void addModelArgs(List<String> args, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(model);
            if (Files.isDirectory(path)) {
                add(args, "--modelDir", model);
                return;
            }
            if (Files.isRegularFile(path)) {
                add(args, "--modelFile", model);
                return;
            }
        } catch (RuntimeException ignored) {
            // Non-filesystem model identifiers are emitted as --model.
        }
        add(args, "--model", model);
    }

    private static void add(List<String> args, String flag, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return;
        }
        args.add(flag);
        args.add(text);
    }

    public record Request(
            String model,
            String provider,
            String runner,
            String prompt,
            String voice,
            String audioFormat,
            String seed,
            String outputPath,
            String audioQuality,
            Integer audioBitrateKbps,
            String audioChannels,
            String codecDecode,
            Integer maxFrames,
            Double maxSeconds,
            Double gainDb,
            Double peakDb,
            Double fadeMs,
            Double trimDbfs,
            Double trimPaddingMs,
            Integer flacCompression,
            boolean disableAudioPolish,
            boolean disableAudioNormalize,
            boolean trimSilence) {
    }
}
