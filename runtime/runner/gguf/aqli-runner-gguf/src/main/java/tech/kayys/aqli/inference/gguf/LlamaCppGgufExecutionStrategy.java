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
        // Generation updates session-owned counters and scratch state that metrics
        // reads immediately afterward. Keep the complete operation atomic per
        // session while allowing independent sessions to execute concurrently.
        synchronized (session) {
            long startNanos = System.nanoTime();
            String output = session.generate(prompt, maxTokens, temperature, topK, topP);
            long completedAtNanos = System.nanoTime();
            Optional<String> nativeMetrics;
            try {
                nativeMetrics = session.metrics(warmSession, reuseKind);
            } catch (Throwable ignored) {
                nativeMetrics = Optional.empty();
            }
            return new Execution(
                    output,
                    session.backendName(),
                    session.lastGeneratedTokens(),
                    completedAtNanos - startNanos,
                    completedAtNanos,
                    nativeMetrics);
        }
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
