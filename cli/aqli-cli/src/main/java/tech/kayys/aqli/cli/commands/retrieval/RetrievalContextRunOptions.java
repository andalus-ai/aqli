package tech.kayys.aqli.cli.commands.retrieval;

import java.util.List;

/** Immutable retrieval context and embedding-model inputs for one run invocation. */
public record RetrievalContextRunOptions(
        List<String> contexts,
        List<String> files,
        int maxChars,
        String embeddingModel) {

    public RetrievalContextRunOptions {
        contexts = List.copyOf(contexts == null ? List.of() : contexts);
        files = List.copyOf(files == null ? List.of() : files);
    }
}
