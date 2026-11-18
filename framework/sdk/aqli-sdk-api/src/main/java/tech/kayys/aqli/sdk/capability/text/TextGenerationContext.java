package tech.kayys.aqli.sdk.capability.text;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Optional retrieval context supplied to a text-generation invocation. */
public record TextGenerationContext(
        Boolean enabled,
        String embeddingModel,
        List<TextGenerationContextItem> items,
        List<Map<String, Object>> sources) {

    public TextGenerationContext {
        items = items == null ? List.of() : List.copyOf(items);
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("items must not contain null entries");
        }
        sources = sources == null ? List.of() : sources.stream()
                .map(source -> Collections.unmodifiableMap(new HashMap<>(source)))
                .toList();
    }

    public static TextGenerationContext ofItems(List<TextGenerationContextItem> items) {
        return new TextGenerationContext(true, null, items, List.of());
    }
}
