package tech.kayys.aqli.onnx.runner.tts.moss.codec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Locates an installed MOSS audio-tokenizer companion repository. */
public final class MossAudioTokenizerModelLocator {
    private MossAudioTokenizerModelLocator() {
    }

    /**
     * Checks indexed locations first, then scans the local model cache.
     * When multiple unindexed codecs are present, the official repository path wins.
     */
    public static Optional<Path> findInstalled(
            Path modelsRoot,
            List<Path> indexedPaths,
            String repository) {
        if (indexedPaths != null) {
            for (Path indexedPath : indexedPaths) {
                Optional<Path> directory = MossAudioTokenizerModelLayout.directoryFrom(indexedPath);
                if (directory.isPresent()) {
                    return directory.map(path -> path.toAbsolutePath().normalize());
                }
            }
        }

        if (modelsRoot == null || !Files.isDirectory(modelsRoot)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(modelsRoot, 8)) {
            List<Path> codecDirectories = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .equals(MossAudioTokenizerModelLayout.METADATA_FILE_NAME))
                    .map(Path::getParent)
                    .filter(path -> path != null)
                    .distinct()
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            Optional<Path> official = codecDirectories.stream()
                    .filter(path -> MossAudioTokenizerModelLayout.looksLikeRepository(path, repository))
                    .findFirst();
            return official.or(() -> codecDirectories.stream().findFirst())
                    .map(path -> path.toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
