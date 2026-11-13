package tech.kayys.aqli.model.repo.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the architecture declared by a local Hugging Face-style model config.
 *
 * <p>This is intentionally limited to generic {@code config.json} metadata.
 * Model-family-specific marker files and runtime-specific architecture
 * overrides belong to the corresponding integration.</p>
 */
public final class LocalModelArchitectureDetector {

    private final ObjectMapper mapper;

    public LocalModelArchitectureDetector() {
        this(new ObjectMapper());
    }

    LocalModelArchitectureDetector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Returns the first declared architecture, falling back to {@code model_type}.
     * Missing, malformed, or unrecognized config files produce an empty result.
     */
    public Optional<String> detect(Path modelDirectory) {
        if (modelDirectory == null) {
            return Optional.empty();
        }
        Path config = modelDirectory.resolve("config.json");
        if (!Files.isRegularFile(config)) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(config.toFile());
            JsonNode architectures = root.path("architectures");
            if (architectures.isArray() && !architectures.isEmpty()) {
                Optional<String> declared = nonBlankText(architectures.get(0));
                if (declared.isPresent()) {
                    return declared;
                }
            }
            return nonBlankText(root.get("model_type"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> nonBlankText(JsonNode value) {
        if (value == null || !value.isValueNode()) {
            return Optional.empty();
        }
        String text = value.asText();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    }
}
