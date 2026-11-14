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

    @Test
    void filteredPullKeepsEssentialFilesUsesRevisionAndPersistsTaskMetadata() throws Exception {
        RecordingHuggingFaceClient client = new RecordingHuggingFaceClient(
                List.of("config.json", "tokenizer.json", "model.onnx", "weights.bin", ".gitattributes"));
        client.pipelineTag = "automatic-speech-recognition";
        client.commitSha = "abc123";
        ManifestStore manifests = new ManifestStore();
        HuggingFaceRepository repository = new HuggingFaceRepository(
                tempDir.resolve("repository-cache"), client, manifests);

        Path directory = repository.pullRepository(
                "org/audio-model",
                "v2",
                "onnx",
                null,
                Map.of(),
                List.of("*onnx"),
                false,
                null,
                null);

        assertTrue(Files.isRegularFile(directory.resolve("config.json")));
        assertTrue(Files.isRegularFile(directory.resolve("tokenizer.json")));
        assertTrue(Files.isRegularFile(directory.resolve("model.onnx")));
        assertTrue(Files.notExists(directory.resolve("weights.bin")));
        assertEquals(List.of("config.json", "tokenizer.json", "model.onnx"), client.downloadedFiles);
        assertEquals("v2", client.requestedRevision);

        AqliManifest manifest = manifests.findByModelId("org/audio-model", "v2").orElseThrow();
        assertEquals("stt", manifest.getTaskType());
        assertEquals("abc123", manifest.getMetadata().get("commit_sha"));
        assertEquals("automatic-speech-recognition", manifest.getMetadata().get("pipeline_tag"));
    }

    @Test
    void forcePullRemovesCompletedAndPartialFilesBeforeDownloadingAgain() throws Exception {
        RecordingHuggingFaceClient client = new RecordingHuggingFaceClient(List.of("model.onnx"));
        ManifestStore manifests = new ManifestStore();
        HuggingFaceRepository repository = new HuggingFaceRepository(
                tempDir.resolve("repository-cache"), client, manifests);
        Path directory = ManifestStore.resolveBlobDir("org/forced", AqliManifest.computeName("org/forced", "main"));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("model.onnx"), "stale");
        Files.writeString(directory.resolve("model.onnx.part"), "partial");

        repository.pullRepository("org/forced", null, null, null, Map.of(),
                List.of(), true, null, null);

        assertEquals(List.of("model.onnx"), client.downloadedFiles);
        assertTrue(Files.notExists(directory.resolve("model.onnx.part")));
        assertEquals("downloaded:model.onnx", Files.readString(directory.resolve("model.onnx")));
    }

    private static final class RecordingHuggingFaceClient extends HuggingFaceClient {
        private final List<String> files;
        private int listCalls;
        private List<String> downloadedFiles = List.of();
        private String requestedRevision;
        private String pipelineTag;
        private String commitSha;

        private RecordingHuggingFaceClient(List<String> files) {
            this.files = List.copyOf(files);
        }

        @Override
        public HuggingFaceModelInfo getModelInfo(String modelId, String revision) {
            requestedRevision = revision;
            HuggingFaceModelInfo info = new HuggingFaceModelInfo();
            info.setPipelineTag(pipelineTag);
            info.setCommitSha(commitSha);
            List<HuggingFaceModelInfo.ModelFile> modelFiles = files.stream().map(filename -> {
                HuggingFaceModelInfo.ModelFile modelFile = new HuggingFaceModelInfo.ModelFile();
                modelFile.setFilename(filename);
                return modelFile;
            }).toList();
            info.setFiles(modelFiles);
            return info;
        }

        @Override
        public void downloadFile(String modelId, String filename, String revision, Path targetPath,
                DownloadProgressListener progressListener) throws IOException {
            downloadedFiles = append(downloadedFiles, filename);
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, "downloaded:" + filename);
        }

        private static List<String> append(List<String> current, String filename) {
            java.util.ArrayList<String> next = new java.util.ArrayList<>(current);
            next.add(filename);
            return List.copyOf(next);
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
