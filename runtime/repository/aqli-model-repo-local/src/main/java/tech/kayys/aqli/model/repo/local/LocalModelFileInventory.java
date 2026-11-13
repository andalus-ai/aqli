package tech.kayys.aqli.model.repo.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Discovers model weight files in the conventional local model format folders.
 *
 * <p>This class only inventories filesystem candidates. It deliberately does
 * not infer model identity, architecture, task type, quantization metadata, or
 * CLI display fields; consumers enrich candidates according to their use case.</p>
 */
public final class LocalModelFileInventory {

    /** A discovered weight file and the format-folder context used to find it. */
    public record Candidate(Path base, Path file, String fallbackFormat, boolean runnable) {
    }

    /**
     * Lists likely model files below one format directory, to the same maximum
     * depth used by the legacy CLI index. Unreadable directories are treated as
     * best-effort empty/partial inventories.
     */
    public List<Candidate> discover(Path base, String fallbackFormat, boolean runnable) {
        if (base == null || !Files.isDirectory(base)) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        try (var files = Files.walk(base, 4)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(LocalModelFileInventory::isLikelyWeightFile)
                    .forEach(path -> candidates.add(new Candidate(base, path, fallbackFormat, runnable)));
        } catch (Exception ignored) {
            // Best-effort inventory; callers have historically tolerated unreadable model folders.
        }
        return List.copyOf(candidates);
    }

    private static boolean isLikelyWeightFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".gguf")
                || name.endsWith(".safetensors")
                || name.endsWith(".safetensor")
                || name.endsWith(".onnx")
                || name.endsWith(".tflite")
                || name.endsWith(".litert")
                || name.endsWith(".pb")
                || name.endsWith(".ckpt")
                || name.endsWith(".pt")
                || name.endsWith(".pth")
                || name.endsWith(".bin")
                || name.endsWith(".litertlm")
                || name.endsWith(".task")
                || !name.contains(".");
    }
}
