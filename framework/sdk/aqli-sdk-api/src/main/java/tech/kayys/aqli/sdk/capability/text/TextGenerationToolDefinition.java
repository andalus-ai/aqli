package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** SDK-owned, transport-neutral tool declaration for text generation. */
public record TextGenerationToolDefinition(
        String name,
        Type type,
        String description,
        Map<String, Object> parameters,
        boolean strict,
        Map<String, Object> metadata) {

    public TextGenerationToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.trim();
        type = type == null ? Type.FUNCTION : type;
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(parameters));
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public enum Type {
        FUNCTION,
        MCP_TOOL,
        CODE_INTERPRETER,
        FILE_SEARCH
    }
}
