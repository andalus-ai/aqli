package tech.kayys.aqli.sdk.model;

import java.util.List;

/**
 * Provider-owned options for pulling a Hugging Face repository.
 *
 * <p>These options deliberately do not extend {@link ModelPullRequest}: file
 * selection and task classification are Hugging Face repository semantics,
 * not generic model-pull concerns.</p>
 */
public record HuggingFacePullRequest(
        String modelSpec,
        String revision,
        String format,
        List<String> includePatterns,
        boolean force,
        String taskType) {

    public HuggingFacePullRequest {
        if (modelSpec == null || modelSpec.isBlank()) {
            throw new IllegalArgumentException("modelSpec is required");
        }
        includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
        if (includePatterns.stream().anyMatch(pattern -> pattern == null || pattern.isBlank())) {
            throw new IllegalArgumentException("includePatterns must not contain blank patterns");
        }
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

        public HuggingFacePullRequest build() {
            return new HuggingFacePullRequest(
                    modelSpec, revision, format, includePatterns, force, taskType);
        }
    }
}
