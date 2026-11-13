package tech.kayys.aqli.inference.gguf;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes a llama.cpp GGUF session and captures execution results independently
 * of CLI presentation and daemon/session-selection policy.
 */
public final class LlamaCppGgufExecutionStrategy {

    public record Execution(
            String output,
            String backendName,
            int outputTokens,
            long executionNanos,
            long completedAtNanos,
            Optional<String> nativeMetrics) {
    }

    public Execution execute(
            LlamaCppGgufSession session,
            String prompt,
            int maxTokens,
            double temperature,
            int topK,
            double topP,
            boolean warmSession,
            String reuseKind) throws Exception {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(prompt, "prompt");
        long startNanos = System.nanoTime();
        LlamaCppGgufSession.GenerationResult result = session.generateWithMetrics(
                prompt, maxTokens, temperature, topK, topP, warmSession, reuseKind);
        return new Execution(
                result.output(),
                session.backendName(),
                result.outputTokens(),
                result.completedAtNanos() - startNanos,
                result.completedAtNanos(),
                result.nativeMetrics());
    }
}
