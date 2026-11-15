package tech.kayys.aqli.cli.util;

import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.io.IOException;
import java.util.List;

/**
 * Backwards-compatible facade for CLI inference feature configuration.
 *
 * <p>New command code should depend directly on the capability-specific
 * configuration classes. Shared CLI/REST/gRPC semantics belong in the
 * transport-neutral SDK/application layer.</p>
 */
@Deprecated(forRemoval = false)
public final class CliInferenceFeatures {
    private CliInferenceFeatures() {
    }

    public static List<ToolDefinition> loadTools(List<String> toolFiles, List<String> mcpTools)
            throws IOException {
        return tech.kayys.aqli.cli.commands.tools.ToolRunRequestConfiguration.loadTools(toolFiles, mcpTools);
    }

    public static Object parseToolChoice(String value) throws IOException {
        return tech.kayys.aqli.cli.commands.tools.ToolRunRequestConfiguration.parseToolChoice(value);
    }

    public static RagContext loadRagContext(List<String> inlineContexts, List<String> files, int maxChars)
            throws IOException {
        return loadRagContext(inlineContexts, files, maxChars, null);
    }

    public static RagContext loadRagContext(
            List<String> inlineContexts,
            List<String> files,
            int maxChars,
            String query) throws IOException {
        var context = tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration
                .load(inlineContexts, files, maxChars, query);
        return new RagContext(context.content(), context.sources(), context.strategy());
    }

    public static void applyTools(
            InferenceRequest.Builder builder,
            List<ToolDefinition> tools,
            Object toolChoice) {
        tech.kayys.aqli.cli.commands.tools.ToolRunRequestConfiguration.apply(builder, tools, toolChoice);
    }

    public static void applyRagMetadata(
            InferenceRequest.Builder builder,
            RagContext ragContext,
            String embeddingModel) {
        var context = ragContext == null
                ? null
                : new tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration.RagContext(
                        ragContext.content(), ragContext.sources(), ragContext.strategy());
        tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration.apply(builder, context, embeddingModel);
    }

    public static String ragSystemPrompt(RagContext ragContext) {
        var context = ragContext == null
                ? null
                : new tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration.RagContext(
                        ragContext.content(), ragContext.sources(), ragContext.strategy());
        return tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration.systemPrompt(context);
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
}
