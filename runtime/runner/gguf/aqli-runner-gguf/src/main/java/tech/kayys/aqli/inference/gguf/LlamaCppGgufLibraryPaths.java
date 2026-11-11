package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Filesystem locations used by the optional llama.cpp GGUF native bridge.
 *
 * <p>This belongs to the GGUF runner capability rather than the CLI: native
 * session setup needs both the AQLI bridge library and the llama.cpp backend
 * directory regardless of which adapter initiates inference.</p>
 */
public record LlamaCppGgufLibraryPaths(Path bridgeLibrary, Path backendDirectory) {

    public static final String DEFAULT_BRIDGE_FILE = "libaqli_gguf_fast.dylib";
    public static final String DEFAULT_BACKEND_DIRECTORY = ".aqli/libs/llama";

    public LlamaCppGgufLibraryPaths {
        bridgeLibrary = normalize(bridgeLibrary, "bridgeLibrary");
        backendDirectory = normalize(backendDirectory, "backendDirectory");
    }

    /**
     * Resolves explicit locations or the established per-user defaults.
     *
     * @param userHome home directory used when a location is not configured
     * @param configuredBackendDirectory optional llama.cpp backend directory
     * @param configuredBridgeLibrary optional AQLI native bridge library
     */
    public static LlamaCppGgufLibraryPaths resolve(
            Path userHome,
            String configuredBackendDirectory,
            String configuredBridgeLibrary) {
        Path home = normalize(userHome, "userHome");
        Path backendDirectory = configuredPath(configuredBackendDirectory)
                .orElseGet(() -> home.resolve(DEFAULT_BACKEND_DIRECTORY));
        Path bridgeLibrary = configuredPath(configuredBridgeLibrary)
                .orElseGet(() -> home.resolve(".aqli/libs").resolve(DEFAULT_BRIDGE_FILE));
        return new LlamaCppGgufLibraryPaths(bridgeLibrary, backendDirectory);
    }

    private static java.util.Optional<Path> configuredPath(String configured) {
        if (configured == null || configured.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Path.of(configured));
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
