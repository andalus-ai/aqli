package tech.kayys.aqli.cli.commands.rag;

import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads CLI-provided retrieval context and maps it onto an inference request.
 *
 * <p>This is a CLI adapter for local inline/file context. Retrieval semantics
 * intended to be shared by CLI, REST, and gRPC belong in the SDK/application
 * layer rather than in this command-specific component.</p>
 */
public final class RagRunRequestConfiguration {
    private static final int CHUNK_TARGET_CHARS = 1200;
    private static final int DEFAULT_MAX_CHARS = 12000;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "this", "with", "from", "into", "your", "you",
            "are", "can", "how", "what", "when", "where", "which", "why", "who", "about",
            "use", "using", "then", "than", "their", "there", "have", "has", "had");

    private RagRunRequestConfiguration() {
    }

    public static RagContext load(
            List<String> inlineContexts,
            List<String> files,
            int maxChars) throws IOException {
        return load(inlineContexts, files, maxChars, null);
    }

    public static RagContext load(
            List<String> inlineContexts,
            List<String> files,
            int maxChars,
            String query) throws IOException {
        List<String> sources = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        int budget = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
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

    public static void apply(
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

    public static String systemPrompt(RagContext ragContext) {
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
            if (trimmed.length() > CHUNK_TARGET_CHARS) {
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
            int end = Math.min(content.length(), start + CHUNK_TARGET_CHARS);
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
            if (normalized.length() < 3 || STOP_WORDS.contains(normalized)) {
                continue;
            }
            terms.add(normalized);
        }
        return terms;
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
