package tech.kayys.aqli.model.repo.hf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aqli.model.repo.local.AqliManifest;
import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aljabr.spi.download.DownloadProgressListener;

class HuggingFaceRepositoryPullTest {
    @TempDir
    Path tempDir;

    private String previousUserHome;

    @BeforeEach
    void setUp() {
        previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", previousUserHome);
        }
    }

    @Test
    void downloadsCompleteRepositoryAndRegistersManifestFromSingleFileListing() throws Exception {
        RecordingHuggingFaceClient client = new RecordingHuggingFaceClient(
                List.of("config.json", "model.onnx", "tokenizer.json"));
        ManifestStore manifests = new ManifestStore();
        HuggingFaceRepository repository = new HuggingFaceRepository(
                tempDir.resolve("repository-cache"), client, manifests);

        Path directory = repository.pullRepository(
                "org/model",
                "main",
                null,
                "moss-audio-tokenizer",
                Map.of("fallback", "test-pull"),
                null);

        assertTrue(Files.isRegularFile(directory.resolve("config.json")));
        assertTrue(Files.isRegularFile(directory.resolve("model.onnx")));
        assertTrue(Files.isRegularFile(directory.resolve("tokenizer.json")));
        assertEquals(1, client.listCalls);
        assertEquals(List.of("config.json", "model.onnx", "tokenizer.json"), client.downloadedFiles);

        AqliManifest manifest = manifests.findByModelId("org/model", "main").orElseThrow();
        assertEquals(directory.toAbsolutePath().toString(), manifest.getBlobPath());
        assertEquals("onnx", manifest.getFormat());
        assertEquals("moss-audio-tokenizer", manifest.getArchitecture());
        assertEquals("test-pull", manifest.getMetadata().get("fallback"));
        assertEquals("onnx", manifest.getMetadata().get("format"));
        assertEquals(List.of("config.json", "model.onnx", "tokenizer.json"), manifest.getFiles());
        assertTrue(manifest.getSizeBytes() > 0);
    }

    @Test
    void appliesPreferredFormatWhenRepositoryHasNoRecognizedWeightExtension() throws Exception {
        RecordingHuggingFaceClient client = new RecordingHuggingFaceClient(
                List.of("codec_browser_onnx_meta.json"));
        ManifestStore manifests = new ManifestStore();
        HuggingFaceRepository repository = new HuggingFaceRepository(
                tempDir.resolve("repository-cache"), client, manifests);

        repository.pullRepository(
                "org/codec",
                "main",
                "onnx",
                null,
                Map.of("fallback", "test-pull"),
                null);

        AqliManifest manifest = manifests.findByModelId("org/codec", "main").orElseThrow();
        assertEquals("onnx", manifest.getFormat());
        assertEquals("onnx", manifest.getMetadata().get("preferred_format"));
    }

    private static final class RecordingHuggingFaceClient extends HuggingFaceClient {
        private final List<String> files;
        private int listCalls;
        private List<String> downloadedFiles = List.of();

        private RecordingHuggingFaceClient(List<String> files) {
            this.files = List.copyOf(files);
        }

        @Override
        public List<String> listFiles(String modelId, String revision) {
            listCalls++;
            return files;
        }

        @Override
        public void downloadRepository(
                String modelId,
                String revision,
                List<String> files,
                Path targetDir,
                DownloadProgressListener progressListener) throws IOException {
            downloadedFiles = List.copyOf(files);
            Files.createDirectories(targetDir);
            for (String file : files) {
                Path target = targetDir.resolve(file);
                Files.createDirectories(target.getParent());
                Files.writeString(target, "fixture:" + file);
            }
        }
    }
}
