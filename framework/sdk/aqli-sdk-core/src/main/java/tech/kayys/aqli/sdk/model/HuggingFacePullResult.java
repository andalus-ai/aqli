package tech.kayys.aqli.sdk.model;

import java.net.URI;
import java.util.Objects;

/**
 * Result of a Hugging Face repository pull.
 *
 * @param repositoryId normalized Hugging Face repository identifier
 * @param location opaque artifact location; local runtimes may return a file URI
 */
public record HuggingFacePullResult(String repositoryId, URI location) {
    public HuggingFacePullResult {
        if (repositoryId == null || repositoryId.isBlank()) {
            throw new IllegalArgumentException("repositoryId is required");
        }
        Objects.requireNonNull(location, "location");
    }
}
