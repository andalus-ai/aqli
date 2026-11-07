/*
 * Aqli Inference Engine — SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.warmup;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.spi.model.HealthStatus;

/**
 * Bridges AQLI's inference SPI to the pure-Java Safetensors backend.
 *
 * <p>The model execution implementation remains in Aljabr/AQLI's
 * {@link DirectSafetensorBackend}; this class only translates the two
 * independently versioned SPI value types at the integration boundary.
 */
@ApplicationScoped
public class SafetensorInferenceEngineAdapter
        implements tech.kayys.aqli.spi.inference.LocalInferenceEngine {

    @Inject
    Instance<DirectSafetensorBackend> backend;

    @Override
    public Uni<tech.kayys.aqli.spi.inference.InferenceResponse> infer(
            tech.kayys.aqli.spi.inference.InferenceRequest request) {
        try {
            DirectSafetensorBackend delegate = delegate();
            tech.kayys.aljabr.spi.inference.InferenceRequest translated = toAljabr(request);
            return delegate.infer(translated).map(SafetensorInferenceEngineAdapter::toAqli);
        } catch (RuntimeException failure) {
            return Uni.createFrom().failure(failure);
        }
    }

    @Override
    public Multi<tech.kayys.aqli.spi.inference.StreamingInferenceChunk> stream(
            tech.kayys.aqli.spi.inference.InferenceRequest request) {
        try {
            DirectSafetensorBackend delegate = delegate();
            tech.kayys.aljabr.spi.inference.InferenceRequest translated = toAljabr(request);
            return delegate.stream(translated).map(SafetensorInferenceEngineAdapter::toAqli);
        } catch (RuntimeException failure) {
            return Multi.createFrom().failure(failure);
        }
    }

    @Override
    public void initialize() {
        delegate().initialize();
    }

    @Override
    public void shutdown() {
        if (backend != null && backend.isResolvable()) {
            backend.get().shutdown();
        }
    }

    @Override
    public boolean isHealthy() {
        return backend != null && backend.isResolvable() && backend.get().isHealthy();
    }

    @Override
    public HealthStatus health() {
        return isHealthy()
                ? backend.get().health()
                : HealthStatus.unhealthy("Pure-Java Safetensors backend is not available");
    }

    @Override
    public EngineStats getStats() {
        if (!isHealthy()) {
            return new EngineStats(0, 0, 0, 0.0d, "unavailable");
        }
        tech.kayys.aljabr.spi.inference.InferenceEngine.EngineStats stats = backend.get().getStats();
        return stats == null ? new EngineStats(0, 0, 0, 0.0d, "available")
                : new EngineStats(stats.activeInferences(), stats.totalInferences(),
                        stats.failedInferences(), stats.avgLatencyMs(), stats.status());
    }

    private DirectSafetensorBackend delegate() {
        if (backend == null || !backend.isResolvable()) {
            throw new IllegalStateException("DirectSafetensorBackend is not available");
        }
        return backend.get();
    }

    static tech.kayys.aljabr.spi.inference.InferenceRequest toAljabr(
            tech.kayys.aqli.spi.inference.InferenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            throw new UnsupportedOperationException("Safetensors text generation does not support tool definitions");
        }
        if (request.getToolChoice() != null) {
            throw new UnsupportedOperationException("Safetensors text generation does not support tool choice");
        }
        if (!request.getAttachments().isEmpty()) {
            throw new UnsupportedOperationException("Safetensors text generation does not support attachments");
        }
        if (request.getNativeContextSegment().isPresent()) {
            throw new UnsupportedOperationException("Safetensors adapter cannot transfer a native context segment");
        }

        List<tech.kayys.aljabr.spi.Message> messages = request.getMessages().stream()
                .map(SafetensorInferenceEngineAdapter::toAljabr)
                .toList();

        return new tech.kayys.aljabr.spi.inference.InferenceRequest(
                request.getRequestId(),
                request.getApiKey(),
                request.getModel(),
                messages,
                request.getParameters(),
                null,
                null,
                request.isStreaming(),
                request.getPreferredProvider().orElse(null),
                request.getPlugin().orElse(null),
                request.getTimeout().orElse(null),
                tech.kayys.aljabr.spi.inference.Priority.valueOf(request.getPriority().name()),
                request.isCacheBypass(),
                request.getUserId().orElse(null),
                request.getSessionId().orElse(null),
                request.getTraceId().orElse(null),
                request.hasExplicitStage()
                        ? tech.kayys.aljabr.spi.inference.InferenceStage.valueOf(request.getInferenceStage().name())
                        : null,
                request.getPromptTokenCount(),
                request.getMetadata(),
                List.of());
    }

    private static tech.kayys.aljabr.spi.Message toAljabr(tech.kayys.aqli.spi.Message message) {
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            throw new UnsupportedOperationException("Safetensors text generation does not support message tool calls");
        }
        if (message.getContentParts() != null && !message.getContentParts().isEmpty()) {
            throw new UnsupportedOperationException("Safetensors text generation does not support multimodal message content");
        }
        return new tech.kayys.aljabr.spi.Message(
                tech.kayys.aljabr.spi.Message.Role.valueOf(message.getRole().name()),
                message.getContent(),
                null,
                message.getName(),
                null,
                message.getToolCallId());
    }

    private static tech.kayys.aqli.spi.inference.InferenceResponse toAqli(
            tech.kayys.aljabr.spi.inference.InferenceResponse response) {
        if (!response.getToolCalls().isEmpty()) {
            throw new UnsupportedOperationException("Safetensors adapter cannot translate generated tool calls");
        }
        return new tech.kayys.aqli.spi.inference.InferenceResponse(
                response.getRequestId(),
                response.getContent(),
                response.getModel(),
                response.getTokensUsed(),
                response.getInputTokens(),
                response.getOutputTokens(),
                response.getDurationMs(),
                response.getTimestamp(),
                response.getMetadata(),
                List.of(),
                tech.kayys.aqli.spi.inference.InferenceResponse.FinishReason.valueOf(
                        response.getFinishReason().name()),
                response.getSessionId());
    }

    private static tech.kayys.aqli.spi.inference.StreamingInferenceChunk toAqli(
            tech.kayys.aljabr.spi.inference.StreamingInferenceChunk chunk) {
        tech.kayys.aljabr.spi.inference.StreamingInferenceChunk.ChunkUsage usage = chunk.usage();
        tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage translatedUsage = usage == null ? null
                : new tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage(
                        usage.inputTokens(), usage.outputTokens(), usage.latencyMs());
        return new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                chunk.requestId(), chunk.index(), chunk.modality(), chunk.delta(), chunk.imageDeltaBase64(),
                chunk.finished(), chunk.finishReason(), translatedUsage, chunk.emittedAt(), chunk.metadata(),
                chunk.toolCallId(), chunk.toolName(), chunk.toolInputDelta());
    }
}
