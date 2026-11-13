package tech.kayys.aqli.inference.gguf;

import java.nio.file.Path;
import java.util.Objects;

import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.inference.InferenceResponse;

/**
 * Executes Java-native GGUF inference while keeping backend session ownership
 * explicit at the runner boundary.
 *
 * <p>The caller supplies the session provider so one-shot execution and daemon
 * session reuse share the same execution path without moving daemon policy
 * into the runner.</p>
 */
public final class GgufJavaExecutionStrategy {

    @FunctionalInterface
    public interface SessionProvider {
        GgufJavaBackendSession acquire(Path modelPath) throws Exception;
    }

    public record Execution(
            InferenceResponse response,
            long openNanos,
            long executionNanos,
            long completedAtNanos) {
    }

    public Execution execute(
            Path modelPath,
            InferenceRequest request,
            SessionProvider sessionProvider,
            boolean closeSessionAfterExecution) throws Exception {
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sessionProvider, "sessionProvider");

        long openStartNanos = System.nanoTime();
        GgufJavaBackendSession session = sessionProvider.acquire(modelPath);
        long openNanos = System.nanoTime() - openStartNanos;
        try {
            long executionStartNanos = System.nanoTime();
            InferenceResponse response = session.execute(request);
            long completedAtNanos = System.nanoTime();
            return new Execution(
                    response,
                    openNanos,
                    completedAtNanos - executionStartNanos,
                    completedAtNanos);
        } finally {
            if (closeSessionAfterExecution) {
                session.close();
            }
        }
    }
}
