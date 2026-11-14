package tech.kayys.aqli.model.repo.local;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Derives stable identity and repository-level paths from conventional local
 * model repository layouts.
 *
 * <p>This class does not assign CLI identifiers, format labels, or runtime
 * capabilities. It only interprets the relative file layout.</p>
 */
public final class LocalModelRepositoryIdentity {

    private LocalModelRepositoryIdentity() {
    }

    /**
     * Derives the legacy model identifier from a path relative to a format
     * directory. Repository layouts with a conventional model file use
     * {@code owner/repository}; other nested files retain {@code owner/file}.
     */
    public static String modelId(Path relative, Path file) {
        if (relative == null) {
            return "";
        }
        String normalized = relative.toString().replace("\\", "/");
        if (relative.getNameCount() >= 3) {
            String fileName = file.getFileName().toString();
            if (!usesRepositoryDisplayName(fileName)) {
                return relative.getName(0) + "/" + fileName;
            }
            return relative.getName(0) + "/" + relative.getName(1);
        }
        return normalized;
    }

    /** Derives a display name while preserving conventional repository naming. */
    public static String displayName(Path relative, Path file) {
        if (relative != null && relative.getNameCount() >= 3
                && usesRepositoryDisplayName(file.getFileName().toString())) {
            return relative.getName(1).toString();
        }
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (isConventionalModelFile(lower)
                && file.getParent() != null
                && file.getParent().getFileName() != null) {
            return file.getParent().getFileName().toString();
        }
        return fileName;
    }

    /**
     * Whether an indexed repository file should expose its containing
     * repository directory as the model path. This currently applies only to
     * nested ONNX repositories, matching the legacy index behavior.
     */
    public static boolean aggregateRepositoryFile(Path relative, Path file) {
        if (relative == null || relative.getNameCount() < 3 || file == null) {
            return false;
        }
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".onnx");
    }

    private static boolean usesRepositoryDisplayName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".onnx") || isConventionalModelFile(lower);
    }

    private static boolean isConventionalModelFile(String lower) {
        return lower.equals("model.safetensors")
                || lower.equals("model.safetensor")
                || lower.equals("model.tflite")
                || lower.equals("model.litert")
                || lower.equals("model.litertlm")
                || lower.equals("model.task");
    }
}
