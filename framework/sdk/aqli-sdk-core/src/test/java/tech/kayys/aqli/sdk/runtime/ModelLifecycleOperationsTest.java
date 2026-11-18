package tech.kayys.aqli.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aljabr.spi.model.ModelInfo;

class ModelLifecycleOperationsTest {

    @Test
    void legacyPullFallbackPreservesSupportedOptions() throws Exception {
        LegacyRuntime runtime = new LegacyRuntime();
        ModelPullRequest request = ModelPullRequest.builder()
                .modelSpec("hf:org/model")
                .revision("release")
                .format("safetensors")
                .force(true)
                .build();

        runtime.pullModel(request, null);

        assertEquals("hf:org/model|release|safetensors|true", runtime.lastPull.get());
    }

    @Test
    void legacyPullFallbackRejectsNonDefaultQuantization() {
        LegacyRuntime runtime = new LegacyRuntime();
        ModelPullRequest request = ModelPullRequest.builder()
                .modelSpec("hf:org/model")
                .quantization("Q8_0")
                .build();

        SdkException error = assertThrows(SdkException.class, () -> runtime.pullModel(request, null));

        assertEquals("SDK_ERR_NOT_SUPPORTED", error.getErrorCode());
        assertEquals(null, runtime.lastPull.get());
    }

    @Test
    void legacyPullFallbackRejectsDisabledConversion() {
        LegacyRuntime runtime = new LegacyRuntime();
        ModelPullRequest request = ModelPullRequest.builder()
                .modelSpec("hf:org/model")
                .convertIfNecessary(false)
                .build();

        SdkException error = assertThrows(SdkException.class, () -> runtime.pullModel(request, null));

        assertEquals("SDK_ERR_NOT_SUPPORTED", error.getErrorCode());
        assertEquals(null, runtime.lastPull.get());
    }

    @Test
    void legacyPullFallbackRejectsExplicitOutputType() {
        LegacyRuntime runtime = new LegacyRuntime();
        ModelPullRequest request = ModelPullRequest.builder()
                .modelSpec("hf:org/model")
                .outType("f16")
                .build();

        SdkException error = assertThrows(SdkException.class, () -> runtime.pullModel(request, null));

        assertEquals("SDK_ERR_NOT_SUPPORTED", error.getErrorCode());
        assertEquals(null, runtime.lastPull.get());
    }

    private static final class LegacyRuntime implements ModelLifecycleOperations {
        private final AtomicReference<String> lastPull = new AtomicReference<>();

        @Override
        public List<ModelInfo> listModels() {
            return List.of();
        }

        @Override
        public List<ModelInfo> listModels(int offset, int limit) {
            return List.of();
        }

        @Override
        public Optional<ModelInfo> getModelInfo(String modelId) {
            return Optional.empty();
        }

        @Override
        public void pullModel(String modelSpec, Consumer<PullProgress> progressCallback) {
            lastPull.set(modelSpec);
        }

        @Override
        public void pullModel(String modelSpec, String revision, String format, boolean force,
                Consumer<PullProgress> progressCallback) {
            lastPull.set(modelSpec + "|" + revision + "|" + format + "|" + force);
        }

        @Override
        public void deleteModel(String modelId) {
        }
    }
}
