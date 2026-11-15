package tech.kayys.aqli.cli.commands.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and applies tool configuration supplied to the CLI run command.
 *
 * <p>This is a CLI adapter, not the canonical tool contract. The SDK/application
 * layer remains responsible for the transport-neutral tool semantics shared by
 * CLI, REST, and gRPC.</p>
 */
public final class ToolRunRequestConfiguration {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ToolRunRequestConfiguration() {
    }

    public static List<ToolDefinition> loadTools(List<String> toolFiles, List<String> mcpTools)
            throws IOException {
        List<ToolDefinition> tools = new ArrayList<>();
        for (String file : safeList(toolFiles)) {
            if (isBlank(file)) {
                continue;
            }
            JsonNode root = MAPPER.readTree(Path.of(file.trim()).toFile());
            addToolDefinitions(root, tools);
        }
        for (String spec : safeList(mcpTools)) {
            if (isBlank(spec)) {
                continue;
            }
            tools.add(mcpToolDefinition(spec.trim()));
        }
        return List.copyOf(tools);
    }

    public static Object parseToolChoice(String value) throws IOException {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return MAPPER.readValue(trimmed, Object.class);
        }
        return trimmed;
    }

    public static void apply(
            InferenceRequest.Builder builder,
            List<ToolDefinition> tools,
            Object toolChoice) {
        if (builder == null) {
            throw new IllegalArgumentException("inference request builder is required");
        }
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
            builder.parameter("tools_enabled", true);
            builder.metadata("tool_count", tools.size());
        }
        if (toolChoice != null) {
            builder.toolChoice(toolChoice);
            builder.parameter("tool_choice", toolChoice);
        }
    }

    private static void addToolDefinitions(JsonNode root, List<ToolDefinition> tools) throws IOException {
        if (root == null || root.isNull()) {
            return;
        }
        if (root.has("tools") && root.get("tools").isArray()) {
            addToolDefinitions(root.get("tools"), tools);
            return;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                addToolDefinitions(item, tools);
            }
            return;
        }
        tools.add(toToolDefinition(root));
    }

    private static ToolDefinition toToolDefinition(JsonNode node) throws IOException {
        if (node.has("function")) {
            JsonNode function = node.get("function");
            ToolDefinition.Builder builder = ToolDefinition.builder()
                    .name(requiredText(function, "name"))
                    .type(toolType(node.path("type").asText("function")))
                    .description(optionalText(function, "description"))
                    .parameters(mapFrom(function.path("parameters")))
                    .strict(function.path("strict").asBoolean(node.path("strict").asBoolean(false)));
            applyMetadata(builder, node.path("metadata"));
            applyMetadata(builder, function.path("metadata"));
            return builder.build();
        }

        ToolDefinition.Builder builder = ToolDefinition.builder()
                .name(requiredText(node, "name"))
                .type(toolType(node.path("type").asText("function")))
                .description(optionalText(node, "description"))
                .parameters(mapFrom(node.path("parameters")))
                .strict(node.path("strict").asBoolean(false));
        applyMetadata(builder, node.path("metadata"));
        return builder.build();
    }

    private static ToolDefinition mcpToolDefinition(String spec) {
        String server = null;
        String name = spec;
        int slash = spec.indexOf('/');
        int colon = spec.indexOf(':');
        int split = slash >= 0 ? slash : colon;
        if (split > 0 && split < spec.length() - 1) {
            server = spec.substring(0, split);
            name = spec.substring(split + 1);
        }

        ToolDefinition.Builder builder = ToolDefinition.builder()
                .name(name)
                .type(ToolDefinition.Type.MCP_TOOL)
                .description("MCP tool " + spec)
                .parameters(Map.of());
        if (server != null) {
            builder.metadata("mcp_server", server);
        }
        return builder.build();
    }

    private static ToolDefinition.Type toolType(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "mcp", "mcp_tool" -> ToolDefinition.Type.MCP_TOOL;
            case "code_interpreter" -> ToolDefinition.Type.CODE_INTERPRETER;
            case "file_search" -> ToolDefinition.Type.FILE_SEARCH;
            default -> ToolDefinition.Type.FUNCTION;
        };
    }

    private static Map<String, Object> mapFrom(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return new LinkedHashMap<>(MAPPER.convertValue(node, MAP_TYPE));
    }

    private static void applyMetadata(ToolDefinition.Builder builder, JsonNode node) throws IOException {
        for (Map.Entry<String, Object> entry : mapFrom(node).entrySet()) {
            builder.metadata(entry.getKey(), entry.getValue());
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (isBlank(value)) {
            throw new IllegalArgumentException("Tool definition is missing required field: " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
