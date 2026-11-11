package tech.kayys.aqli.onnx.runner.tts.moss.codec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Resolves the on-disk layout of the MOSS audio-tokenizer companion model. */
public final class MossAudioTokenizerModelLayout {
    public static final String REPOSITORY_ID = "OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX";
    public static final String MODEL_SPEC = "hf:" + REPOSITORY_ID;
    public static final String METADATA_FILE_NAME = "codec_browser_onnx_meta.json";

    private MossAudioTokenizerModelLayout() {
    }

    public static Optional<Path> directoryFrom(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        if (Files.isRegularFile(path)) {
            if (path.getFileName().toString().equals(METADATA_FILE_NAME)) {
                return Optional.ofNullable(path.getParent());
            }
            Path parent = path.getParent();
            return isModelDirectory(parent) ? Optional.of(parent) : Optional.empty();
        }
        if (isModelDirectory(path)) {
            return Optional.of(path);
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path, 6)) {
                return walk.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().equals(METADATA_FILE_NAME))
                        .map(Path::getParent)
                        .findFirst();
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean isModelDirectory(Path path) {
        return path != null && Files.isRegularFile(path.resolve(METADATA_FILE_NAME));
    }

    public static boolean looksLikeRepository(Path path, String repository) {
        if (path == null || repository == null || repository.isBlank()) {
            return false;
        }
        String normalizedPath = normalizeReference(path.toAbsolutePath().normalize().toString());
        String normalizedRepository = normalizeReference(repository);
        return !normalizedRepository.isBlank() && normalizedPath.contains(normalizedRepository);
    }

    private static String normalizeReference(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replace("\\", "/")
                        .replace("__", "/")
                        .replace("--", "/")
                        .replaceAll("[^a-z0-9]+", "/")
                        .replaceAll("/+", "/")
                        .replaceAll("^/|/$", "");
    }
}
