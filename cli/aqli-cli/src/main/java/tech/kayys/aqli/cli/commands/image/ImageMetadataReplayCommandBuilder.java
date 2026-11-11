package tech.kayys.aqli.cli.commands.image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the reproducible CLI argument vector for an image metadata replay.
 *
 * <p>This class owns command serialization only. Metadata interpretation and
 * CLI option precedence remain responsibilities of {@code RunCommand}.</p>
 */
public final class ImageMetadataReplayCommandBuilder {

    private ImageMetadataReplayCommandBuilder() {
    }

    public static List<String> build(Request request) {
        List<String> args = new ArrayList<>();
        args.add("aqli");
        args.add("run");
        addModelArgs(args, request.model());
        add(args, "--provider", request.provider());
        add(args, "--runner", request.runner());
        add(args, "--prompt", request.prompt());
        add(args, "--seed", request.seed());
        add(args, "--steps", request.steps());
        add(args, "--guidance-scale", request.guidanceScale());
        add(args, "--width", request.width());
        add(args, "--height", request.height());
        add(args, "--output", request.outputPath());
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
            String seed,
            Object steps,
            Object guidanceScale,
            Object width,
            Object height,
            String outputPath) {
    }
}
