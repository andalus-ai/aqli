package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A retrieved passage and its transport-neutral provenance. */
public record TextGenerationContextItem(
        String text,
        String id,
        String source,
        String title,
        Double score,
        Map<String, Object> metadata) {

    public TextGenerationContextItem {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        text = text.trim();
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(metadata));
    }
}
