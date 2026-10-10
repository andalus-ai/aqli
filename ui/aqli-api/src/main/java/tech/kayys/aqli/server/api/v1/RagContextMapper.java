package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RagContextMapper {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> CONTEXT_FIELDS = List.of(
            "rag_context",
            "retrieval_context",
            "retrieved_context",
            "context_documents",
            "retrieved_documents");

    private RagContextMapper() {
    }

    static boolean apply(InferenceRequest.Builder builder, JsonNode root) {
        ContextNode contextNode = contextNode(root);
        if (contextNode == null) {
            return false;
        }

        List<ContextItem> items = contextItems(contextNode.node());
        if (items.isEmpty()) {
            return false;
        }

        builder.message(Message.system(formatContext(items)));
        builder.parameter("rag_context", toJava(contextNode.node()));
        builder.metadata("rag_context_injected", true);
        builder.metadata("rag_context_items", items.size());
        if (!"rag_context".equals(contextNode.field())) {
            builder.metadata("rag_context_alias", contextNode.field());
        }
        if (!hasObjectOrArray(root.path("rag_sources"))) {
            List<Map<String, Object>> sources = sourceMetadata(items);
            if (!sources.isEmpty()) {
                builder.parameter("rag_sources", sources);
            }
        }
        return true;
    }

    private static ContextNode contextNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        for (String field : CONTEXT_FIELDS) {
            JsonNode node = root.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return new ContextNode(field, node);
            }
        }
        return null;
    }

    private static List<ContextItem> contextItems(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<ContextItem> items = new ArrayList<>();
            for (JsonNode item : node) {
                addContextItem(items, item);
            }
            return items;
        }
        if (node.isObject()) {
            JsonNode nested = nestedContextArray(node);
            if (nested != null) {
                return contextItems(nested);
            }
        }
        List<ContextItem> items = new ArrayList<>();
        addContextItem(items, node);
        return items;
    }

    private static JsonNode nestedContextArray(JsonNode node) {
        for (String field : List.of("documents", "chunks", "items", "sources", "contexts", "results")) {
            JsonNode nested = node.path(field);
            if (nested.isArray()) {
                return nested;
            }
        }
        return null;
    }

    private static void addContextItem(List<ContextItem> items, JsonNode node) {
        String text = itemText(node);
        if (isBlank(text)) {
            return;
        }
        items.add(new ContextItem(
                text,
                firstText(node, "id", "chunk_id", "document_id", "doc_id"),
                firstText(node, "source", "uri", "url", "path", "file"),
                firstText(node, "title", "name"),
                firstNumber(node, "score", "relevance", "similarity"),
                metadata(node)));
    }

    private static String itemText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String text = itemText(item);
                if (!isBlank(text)) {
                    parts.add(text);
                }
            }
            return String.join("\n", parts);
        }
        if (node.isObject()) {
            for (String field : List.of("text", "content", "page_content", "snippet", "document")) {
                JsonNode value = node.path(field);
                if (!value.isMissingNode() && !value.isNull()) {
                    return itemText(value);
                }
            }
        }
        return node.toString();
    }

    private static String formatContext(List<ContextItem> items) {
        StringBuilder out = new StringBuilder("Retrieved context supplied by the caller. Use it when relevant.");
        for (int i = 0; i < items.size(); i++) {
            ContextItem item = items.get(i);
            out.append("\n\n[").append(i + 1).append("]");
            List<String> labels = new ArrayList<>();
            if (!isBlank(item.title())) {
                labels.add(item.title());
            }
            if (!isBlank(item.source())) {
                labels.add("source: " + item.source());
            }
            if (item.score() != null) {
                labels.add("score: " + trimScore(item.score()));
            }
            if (!labels.isEmpty()) {
                out.append(" ").append(String.join(", ", labels));
            }
            out.append("\n").append(item.text());
        }
        return out.toString();
    }

    private static List<Map<String, Object>> sourceMetadata(List<ContextItem> items) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ContextItem item = items.get(i);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", i + 1);
            putIfPresent(source, "id", item.id());
            putIfPresent(source, "source", item.source());
            putIfPresent(source, "title", item.title());
            if (item.score() != null) {
                source.put("score", item.score());
            }
            if (!item.metadata().isEmpty()) {
                source.put("metadata", item.metadata());
            }
            if (source.size() > 1) {
                sources.add(source);
            }
        }
        return sources;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (!isBlank(value)) {
            target.put(key, value);
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !isBlank(value.asText())) {
                return value.asText();
            }
        }
        JsonNode metadata = node.path("metadata");
        if (metadata.isObject()) {
            return firstText(metadata, fields);
        }
        return null;
    }

    private static Double firstNumber(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
        }
        JsonNode metadata = node.path("metadata");
        if (metadata.isObject()) {
            return firstNumber(metadata, fields);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("metadata").isObject()) {
            return Map.of();
        }
        return MAPPER.convertValue(node.path("metadata"), Map.class);
    }

    private static boolean hasObjectOrArray(JsonNode node) {
        return node != null && (node.isObject() || node.isArray());
    }

    private static Object toJava(JsonNode node) {
        return MAPPER.convertValue(node, Object.class);
    }

    private static String trimScore(double score) {
        String value = String.format(Locale.ROOT, "%.4f", score);
        return value.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ContextNode(String field, JsonNode node) {
    }

    private record ContextItem(
            String text,
            String id,
            String source,
            String title,
            Double score,
            Map<String, Object> metadata) {
    }
}
