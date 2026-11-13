package tech.kayys.aqli.inference.gguf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LlamaCppGgufLibraryPathsTest {

    @Test
    void resolvesEstablishedPerUserDefaults() {
        LlamaCppGgufLibraryPaths paths = LlamaCppGgufLibraryPaths.resolve(
                Path.of("/home/tester"), null, null);

        assertEquals(
                Path.of("/home/tester/.aqli/libs/libaqli_gguf_fast.dylib"),
                paths.bridgeLibrary());
        assertEquals(Path.of("/home/tester/.aqli/libs/llama"), paths.backendDirectory());
    }

    @Test
    void resolvesConfiguredLocationsToAbsoluteNormalizedPaths() {
        LlamaCppGgufLibraryPaths paths = LlamaCppGgufLibraryPaths.resolve(
                Path.of("/home/tester"),
                "native/llama/../llama",
                "build/native/../libaqli_gguf_fast.dylib");

        assertEquals(
                Path.of(System.getProperty("user.dir"), "build/libaqli_gguf_fast.dylib")
                        .toAbsolutePath().normalize(),
                paths.bridgeLibrary());
        assertEquals(
                Path.of(System.getProperty("user.dir"), "native/llama").toAbsolutePath().normalize(),
                paths.backendDirectory());
    }

    @Test
    void resolvesEachConfiguredLocationIndependently() {
        LlamaCppGgufLibraryPaths paths = LlamaCppGgufLibraryPaths.resolve(
                Path.of("/home/tester"), "/opt/llama", null);

        assertEquals(Path.of("/home/tester/.aqli/libs/libaqli_gguf_fast.dylib"), paths.bridgeLibrary());
        assertEquals(Path.of("/opt/llama"), paths.backendDirectory());
    }
}
