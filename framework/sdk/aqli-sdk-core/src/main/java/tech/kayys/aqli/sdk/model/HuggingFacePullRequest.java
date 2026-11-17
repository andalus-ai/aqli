package tech.kayys.aqli.sdk.model;

import java.util.List;
import java.util.Map;

/**
 * Provider-owned options for pulling a Hugging Face repository.
 *
 * <p>These options deliberately do not extend {@link ModelPullRequest}: file
 * selection, task classification, repository architecture, and manifest metadata
 * are Hugging Face repository semantics, not generic model-pull concerns.</p>
 */
public record HuggingFacePullRequest(
        String modelSpec,
        String revision,
        String format,
        List<String> includePatterns,
        boolean force,
        String taskType,
        String architecture,
        Map<String, String> metadata) {

    public HuggingFacePullRequest {
        if (modelSpec == null || modelSpec.isBlank()) {
            throw new IllegalArgumentException("modelSpec is required");
        }
        includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (includePatterns.stream().anyMatch(pattern -> pattern == null || pattern.isBlank())) {
            throw new IllegalArgumentException("includePatterns must not contain blank patterns");
        }
    }

    /** Backward-compatible constructor for the original typed capability shape. */
    public HuggingFacePullRequest(String modelSpec, String revision, String format,
            List<String> includePatterns, boolean force, String taskType) {
        this(modelSpec, revision, format, includePatterns, force, taskType, null, Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String modelSpec;
        private String revision;
        private String format;
        private List<String> includePatterns = List.of();
        private boolean force;
        private String taskType;
        private String architecture;
        private Map<String, String> metadata = Map.of();

        public Builder modelSpec(String modelSpec) {
            this.modelSpec = modelSpec;
            return this;
        }

        public Builder revision(String revision) {
            this.revision = revision;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder includePatterns(List<String> includePatterns) {
            this.includePatterns = includePatterns;
            return this;
        }

        public Builder force(boolean force) {
            this.force = force;
            return this;
        }

        public Builder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder architecture(String architecture) {
            this.architecture = architecture;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public HuggingFacePullRequest build() {
            return new HuggingFacePullRequest(
                    modelSpec, revision, format, includePatterns, force, taskType, architecture, metadata);
        }
    }
}
