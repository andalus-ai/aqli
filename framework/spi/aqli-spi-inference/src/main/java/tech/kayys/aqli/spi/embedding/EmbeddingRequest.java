package tech.kayys.aqli.spi.embedding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * AQLI compatibility request for embedding generation.
 *
 * <p>The canonical model-execution contract is Aljabr's embedding request.
 * This DTO mirrors its portable options while existing AQLI SDK and transport
 * integrations migrate.
 */
public record EmbeddingRequest(
        String requestId,
        String model,
        List<String> inputs,
        Map<String, Object> parameters,
        EmbeddingTask task,
        Integer outputDimension) {

    /** Backward-compatible constructor for existing four-argument callers. */
    public EmbeddingRequest(
            String requestId, String model, List<String> inputs, Map<String, Object> parameters) {
        this(requestId, model, inputs, parameters, EmbeddingTask.UNSPECIFIED, null);
    }

    public EmbeddingRequest {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(inputs, "inputs is required");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (parameters == null) {
            parameters = Map.of();
        }
        if (task == null) {
            task = EmbeddingTask.UNSPECIFIED;
        }
        if (outputDimension != null && outputDimension <= 0) {
            throw new IllegalArgumentException("outputDimension must be positive when specified");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private String model;
        private List<String> inputs;
        private Map<String, Object> parameters = Map.of();
        private EmbeddingTask task = EmbeddingTask.UNSPECIFIED;
        private Integer outputDimension;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder inputs(List<String> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder input(String input) {
            this.inputs = List.of(input);
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder task(EmbeddingTask task) {
            this.task = task;
            return this;
        }

        public Builder outputDimension(Integer outputDimension) {
            this.outputDimension = outputDimension;
            return this;
        }

        public Builder outputDimension(int outputDimension) {
            this.outputDimension = outputDimension;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(requestId, model, inputs, parameters, task, outputDimension);
        }
    }
}
