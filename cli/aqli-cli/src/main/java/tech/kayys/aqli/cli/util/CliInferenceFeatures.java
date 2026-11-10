package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared CLI plumbing for advanced inference features.
 */
public final class CliInferenceFeatures {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int RAG_CHUNK_TARGET_CHARS = 1200;
    private static final Set<String> RAG_STOP_WORDS = Set.of(
            "the", "and", "for", "that", "this", "with", "from", "into", "your", "you",
            "are", "can", "how", "what", "when", "where", "which", "why", "who", "about",
            "use", "using", "then", "than", "their", "there", "have", "has", "had");

    private CliInferenceFeatures() {
    }

    public static List<ToolDefinition> loadTools(List<String> toolFiles, List<String> mcpTools) throws IOException {
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

    public static RagContext loadRagContext(List<String> inlineContexts, List<String> files, int maxChars)
            throws IOException {
        return loadRagContext(inlineContexts, files, maxChars, null);
    }

    public static RagContext loadRagContext(
            List<String> inlineContexts,
            List<String> files,
            int maxChars,
            String query)
            throws IOException {
        List<String> sources = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        int budget = maxChars > 0 ? maxChars : 12000;
        Set<String> queryTerms = terms(query);
        boolean queryAware = !queryTerms.isEmpty();
        boolean usedRankedChunks = false;

        for (String context : safeList(inlineContexts)) {
            if (isBlank(context)) {
                continue;
            }
            String clipped = takeWithinBudget(context.trim(), budget - lengthOf(parts));
            if (!clipped.isEmpty()) {
                sources.add("inline");
                parts.add(clipped);
            }
        }

        for (String file : safeList(files)) {
            if (isBlank(file)) {
                continue;
            }
            Path path = Path.of(file.trim());
            String content = Files.readString(path);
            List<RagChunk> chunks = queryAware
                    ? rankChunks(path, content, queryTerms)
                    : List.of(new RagChunk(path.toString(), content, 0, 0));
            usedRankedChunks = usedRankedChunks || queryAware;
            for (RagChunk chunk : chunks) {
                String clipped = takeWithinBudget(chunk.content().trim(), budget - lengthOf(parts));
                if (!clipped.isEmpty()) {
                    sources.add(chunk.source());
                    parts.add("Source: " + chunk.source() + "\n" + clipped);
                }
                if (lengthOf(parts) >= budget) {
                    break;
                }
            }
        }

        return new RagContext(
                String.join("\n\n---\n\n", parts),
                List.copyOf(sources),
                usedRankedChunks ? "query_chunked" : "full_context");
    }

    public static void applyTools(
            InferenceRequest.Builder builder,
            List<ToolDefinition> tools,
            Object toolChoice) {
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

    public static void applyRagMetadata(
            InferenceRequest.Builder builder,
            RagContext ragContext,
            String embeddingModel) {
        if (ragContext != null && ragContext.hasContent()) {
            builder.parameter("rag_enabled", true);
            builder.parameter("rag_context", ragContext.content());
            builder.parameter("rag_sources", ragContext.sources());
            builder.parameter("rag_strategy", ragContext.strategy());
            builder.metadata("rag_enabled", true);
            builder.metadata("rag_source_count", ragContext.sources().size());
            builder.metadata("rag_sources", ragContext.sources());
            builder.metadata("rag_strategy", ragContext.strategy());
        }
        if (!isBlank(embeddingModel)) {
            String model = embeddingModel.trim();
            builder.parameter("embedding_model", model);
            builder.metadata("embedding_model", model);
        }
    }

    public static String ragSystemPrompt(RagContext ragContext) {
        if (ragContext == null || !ragContext.hasContent()) {
            return null;
        }
        return "Use the following retrieval context when it is relevant. "
                + "If it does not contain the answer, say what is missing instead of inventing facts.\n\n"
                + ragContext.content();
    }

    public record RagContext(String content, List<String> sources, String strategy) {
        public RagContext(String content, List<String> sources) {
            this(content, sources, "full_context");
        }

        public boolean hasContent() {
            return content != null && !content.isBlank();
        }

        public String strategy() {
            return strategy == null || strategy.isBlank() ? "full_context" : strategy;
        }
    }

    private record RagChunk(String source, String content, int score, int index) {
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

    private static List<RagChunk> rankChunks(Path path, String content, Set<String> queryTerms) {
        List<String> chunks = splitIntoChunks(content);
        List<RagChunk> ranked = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (isBlank(chunk)) {
                continue;
            }
            ranked.add(new RagChunk(
                    path + "#chunk-" + (i + 1),
                    chunk,
                    scoreChunk(chunk, queryTerms),
                    i));
        }
        ranked.sort(Comparator
                .comparingInt(RagChunk::score).reversed()
                .thenComparingInt(RagChunk::index));
        return ranked;
    }

    private static List<String> splitIntoChunks(String content) {
        if (isBlank(content)) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String paragraph : content.split("\\R\\s*\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > RAG_CHUNK_TARGET_CHARS) {
                splitLongChunk(trimmed, chunks);
                continue;
            }
            chunks.add(trimmed);
        }
        return chunks;
    }

    private static void splitLongChunk(String content, List<String> chunks) {
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + RAG_CHUNK_TARGET_CHARS);
            chunks.add(content.substring(start, end));
            start = end;
        }
    }

    private static int scoreChunk(String content, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0;
        }
        Set<String> chunkTerms = terms(content);
        int score = 0;
        for (String term : queryTerms) {
            if (chunkTerms.contains(term)) {
                score += term.length() >= 6 ? 3 : 2;
            }
        }
        return score;
    }

    private static Set<String> terms(String value) {
        if (isBlank(value)) {
            return Set.of();
        }
        Set<String> terms = new HashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9_\\-]+")) {
            String normalized = token.trim();
            if (normalized.length() < 3 || RAG_STOP_WORDS.contains(normalized)) {
                continue;
            }
            terms.add(normalized);
        }
        return terms;
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

    private static int lengthOf(List<String> parts) {
        return parts.stream().mapToInt(String::length).sum();
    }

    private static String takeWithinBudget(String value, int remaining) {
        if (value == null || remaining <= 0) {
            return "";
        }
        return value.length() <= remaining ? value : value.substring(0, remaining);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
