package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared CLI payload augmentation for agent request validation and preflight.
 */
final class AgentRequestPayloads {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private AgentRequestPayloads() {
    }

    static void appendToolFiles(Map<String, Object> request, List<String> toolFiles, ObjectMapper mapper)
            throws IOException {
        if (toolFiles == null || toolFiles.isEmpty()) {
            return;
        }
        List<Object> tools = listValue(request.get("tools"), "tools");
        for (String file : toolFiles) {
            if (!hasText(file)) {
                continue;
            }
            tools.addAll(readJsonItems(Path.of(file.trim()), "Tool file", mapper));
        }
        request.put("tools", tools);
    }

    static void appendRagContext(Map<String, Object> request, List<String> ragContexts, List<String> ragFiles)
            throws IOException {
        List<Object> context = listValue(request.get("rag_context"), "rag_context");
        if (ragContexts != null) {
            for (String value : ragContexts) {
                if (hasText(value)) {
                    context.add(Map.of("source", "cli", "text", value.trim()));
                }
            }
        }
        if (ragFiles != null) {
            for (String file : ragFiles) {
                if (hasText(file)) {
                    Path path = Path.of(file.trim());
                    context.add(Map.of("source", path.toString(), "text", Files.readString(path)));
                }
            }
        }
        if (!context.isEmpty()) {
            request.put("rag_context", context);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Object> listValue(Object raw, String field) {
        if (raw == null) {
            return new ArrayList<>();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>((List<Object>) list);
        }
        throw new IllegalArgumentException(field + " must be a JSON array.");
    }

    private static List<Object> readJsonItems(Path path, String label, ObjectMapper mapper) throws IOException {
        JsonNode root = mapper.readTree(Files.readString(path));
        JsonNode items = root.path("tools").isArray() ? root.path("tools") : root;
        List<Object> out = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                out.add(mapper.convertValue(item, Object.class));
            }
        } else if (items.isObject()) {
            out.add(mapper.convertValue(items, MAP_TYPE));
        } else {
            throw new IllegalArgumentException(
                    label + " must contain a JSON object, array, or object with tools array: " + path);
        }
        return out;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
