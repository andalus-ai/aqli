package tech.kayys.aqli.cli.commands.features.packaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionPackageReaderTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesJarWithoutExtractingIt() throws Exception {
        Path jar = Files.write(tempDir.resolve("extension.jar"), new byte[] {1, 2, 3});

        try (var source = ExtensionPackageReader.resolve(jar)) {
            assertEquals(jar.toAbsolutePath().normalize(), source.jarPath());
            assertNull(source.packageReport());
            assertEquals("extension.jar", source.defaultInstalledName());
            assertEquals(source.jarPath().toString(), source.displayJarPath());
        }
    }

    @Test
    void extractsDeclaredJarAndPreservesPackageMetadata() throws Exception {
        Path archive = packageZip("dist/aqli-package.json",
                manifest("extensions.demo", "lib/demo.jar"));

        Path extracted;
        try (var source = ExtensionPackageReader.resolve(archive)) {
            extracted = source.jarPath();
            assertTrue(Files.isRegularFile(extracted));
            assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(extracted));
            assertEquals("demo.jar", source.defaultInstalledName());
            assertEquals(archive + "!dist/lib/demo.jar", source.displayJarPath());
            assertEquals("extensions.demo", source.packageReport().toLockMap().get("id"));
            assertEquals("feature-adapter", source.packageReport().toLockMap().get("kind"));
            assertEquals(64, source.packageReport().sha256().length());
        }
        assertFalse(Files.exists(extracted), "temporary extracted artifact must be deleted on close");
    }

    @Test
    void rejectsTraversalArtifactPath() throws Exception {
        Path archive = packageZip("aqli-package.json", manifest("extensions.demo", "../escape.jar"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ExtensionPackageReader.resolve(archive));
        assertTrue(error.getMessage().contains("Invalid extension package artifact path"));
        assertFalse(Files.exists(tempDir.resolve("escape.jar")));
    }

    @Test
    void rejectsMultiplePackageManifests() throws Exception {
        Path archive = tempDir.resolve("multiple.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "aqli-package.json", mapper.writeValueAsBytes(manifest("one", "lib/one.jar")));
            put(zip, "nested/aqli-package.json", mapper.writeValueAsBytes(manifest("two", "lib/two.jar")));
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ExtensionPackageReader.resolve(archive));
        assertTrue(error.getMessage().contains("multiple aqli-package.json"));
    }

    @Test
    void rejectsUnsupportedArtifact() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ExtensionPackageReader.resolve(tempDir.resolve("extension.tar")));
        assertTrue(error.getMessage().contains("must be a .jar or .zip"));
    }

    private Path packageZip(String manifestEntry, Map<String, Object> manifest) throws Exception {
        Path archive = tempDir.resolve("extension.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, manifestEntry, mapper.writeValueAsBytes(manifest));
            put(zip, "dist/lib/demo.jar", new byte[] {4, 5, 6});
            put(zip, "lib/demo.jar", new byte[] {4, 5, 6});
        }
        return archive;
    }

    private Map<String, Object> manifest(String id, String artifactPath) {
        return Map.of(
                "id", id,
                "name", "Demo extension",
                "version", "1.0.0",
                "kind", "feature-adapter",
                "requires", Map.of("aqli", ">=1.0.0"),
                "artifacts", List.of(Map.of("type", "java-jar", "path", artifactPath)));
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
