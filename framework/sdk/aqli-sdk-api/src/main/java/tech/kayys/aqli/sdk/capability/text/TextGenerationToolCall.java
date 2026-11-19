package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** SDK-owned assistant tool call in a text conversation history. */
public record TextGenerationToolCall(String id, String name, Type type, Map<String, Object> arguments) {
    public TextGenerationToolCall {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        type = type == null ? Type.FUNCTION : type;
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(arguments));
    }
    public enum Type { FUNCTION }
}
