package tech.kayys.aqli.server.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.server.api.v1.jobs.BackgroundJobManager;

class ModelsResourcePullTest {

    @Test
    void mapsHuggingFaceOptionsIntoTypedSdkCapability() {
        ModelsResource resource = new ModelsResource();
        RecordingJobManager jobs = new RecordingJobManager();
        resource.jobManager = jobs;
        resource.meterRegistry = new SimpleMeterRegistry();

        Response response = resource.pullModel(new ModelsResource.PullRequestDTO(
                "hf:org/model", "release", true, "gguf", List.of("*.gguf"), "text-generation"));

        assertEquals(202, response.getStatus());
        assertNotNull(jobs.huggingFaceRequest);
        assertEquals("hf:org/model", jobs.huggingFaceRequest.modelSpec());
        assertEquals("release", jobs.huggingFaceRequest.revision());
        assertEquals("gguf", jobs.huggingFaceRequest.format());
        assertEquals(List.of("*.gguf"), jobs.huggingFaceRequest.includePatterns());
        assertEquals("text-generation", jobs.huggingFaceRequest.taskType());
        assertEquals(true, jobs.huggingFaceRequest.force());
    }

    @Test
    void mapsOtherProvidersIntoGenericModelPullRequest() {
        ModelsResource resource = new ModelsResource();
        RecordingJobManager jobs = new RecordingJobManager();
        resource.jobManager = jobs;
        resource.meterRegistry = new SimpleMeterRegistry();

        Response response = resource.pullModel(new ModelsResource.PullRequestDTO(
                "ollama:llama3", "latest", false, "gguf", List.of(), null));

        assertEquals(202, response.getStatus());
        assertNotNull(jobs.modelRequest);
        assertEquals("ollama:llama3", jobs.modelRequest.modelSpec());
        assertEquals("latest", jobs.modelRequest.revision());
        assertEquals("gguf", jobs.modelRequest.format());
        assertEquals(false, jobs.modelRequest.force());
    }

    private static final class RecordingJobManager extends BackgroundJobManager {
        private HuggingFacePullRequest huggingFaceRequest;
        private ModelPullRequest modelRequest;

        @Override
        public String startPullJob(HuggingFacePullRequest request) {
            this.huggingFaceRequest = request;
            return "hf-job";
        }

        @Override
        public String startPullJob(ModelPullRequest request) {
            this.modelRequest = request;
            return "model-job";
        }
    }
}
