package tech.kayys.aqli.sdk.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AqliChatServiceTest {

    private final AqliChatService service = new AqliChatService();

    @Test
    void createChatRequestPreservesPreferredProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "safetensor");

        var request = service.createRequest(
                "model-id",
                null,
                List.of(tech.kayys.aqli.spi.Message.user("hello")),
                List.of(),
                params,
                false);

        assertEquals("safetensor", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
    }

    @Test
    void createsCanonicalTypedTextRequestWithSharedChatDefaultsAndTools() {
        ToolDefinition tool = ToolDefinition.builder().name("lookup").build();
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "safetensor");

        var request = service.createTextGenerationRequest(
                "model-id",
                "be concise",
                List.of(Message.user("hello")),
                List.of(tool),
                params);

        assertEquals("model-id", request.model());
        assertEquals(List.of(Message.system("be concise"), Message.user("hello")), request.messages());
        assertEquals(List.of(tool), request.tools());
        assertEquals(0.2, request.parameters().temperature(), 0.0001);
        assertEquals(0.9, request.parameters().topP(), 0.0001);
        assertEquals(40, request.parameters().topK());
        assertEquals(256, request.parameters().maxTokens());
        assertEquals(1.1, request.parameters().repeatPenalty(), 0.0001);
        assertEquals(false, request.parameters().jsonMode());
    }

    @Test
    void treatsNonPositiveChatTokenLimitAsUnspecified() {
        var request = service.createTextGenerationRequest(
                "model-id",
                null,
                List.of(Message.user("hello")),
                List.of(),
                ChatParams.of(0.0, 0));

        assertEquals(null, request.parameters().maxTokens());
    }

    @Test
    void typedTextStreamPreservesSdkEventMetadataAndToolCallFields() {
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var emittedAt = java.time.Instant.parse("2026-11-18T02:00:00Z");
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var sourceChunk = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 4, "", false, null,
                tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.ToolCallEvent.DELTA,
                "call-1", null, "{\\"query\\":",
                new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.Usage(5, 2, 18),
                emittedAt, java.util.Map.of("provider", "cpu"));
        org.mockito.Mockito.when(sdk.streamText(request))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().item(sourceChunk));

        var chunks = service.streamText(sdk, request).collect().asList().await().indefinitely();

        assertEquals(1, chunks.size());
        var chunk = chunks.getFirst();
        assertEquals("stream-request", chunk.requestId());
        assertEquals(4, chunk.index());
        assertEquals("call-1", chunk.toolCallId());
        assertEquals("{\\"query\\":", chunk.toolInputDelta());
        assertEquals(new tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage(5, 2, 18),
                chunk.usage());
        assertEquals(emittedAt, chunk.emittedAt());
        assertEquals(java.util.Map.of("provider", "cpu"), chunk.metadata());
    }

    @Test
    void createCompletionRequestPreservesPreferredProvider() {
        ChatParams params = new ChatParams(0.2, 256, 0.9, 1.1, "llamacpp");

        var request = service.createCompletionRequest("model-id", "hello", params, false);

        assertEquals("llamacpp", request.getPreferredProvider().orElseThrow());
        assertEquals(0.2, request.getTemperature(), 0.0001);
        assertEquals(256, request.getMaxTokens());
        assertEquals(0.9, request.getTopP(), 0.0001);
        assertEquals(1.1, request.getRepeatPenalty(), 0.0001);
    }

    @Test
    void typedTextExecutionPreservesCallerRequestId() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = new tech.kayys.aqli.sdk.capability.text.TextGenerationRequest(
                "model-id",
                List.of(tech.kayys.aqli.sdk.capability.text.TextGenerationMessage.user("hello")),
                tech.kayys.aqli.sdk.capability.text.TextGenerationParameters.unspecified(),
                List.of(), null, java.util.Map.of(),
                "caller-request-42", "user-1", "session-1", "trace-1");
        var response = new tech.kayys.aqli.sdk.capability.text.TextGenerationResponse(
                "hello", "model-id", 1, 1, 10,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP);
        org.mockito.Mockito.when(sdk.generateTextAsync(request))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));

        service.generateText(sdk, request).await().indefinitely();

        var captor = org.mockito.ArgumentCaptor.forClass(
                tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.class);
        org.mockito.Mockito.verify(sdk).generateTextAsync(captor.capture());
        assertEquals("caller-request-42", captor.getValue().requestId());
        assertEquals("user-1", captor.getValue().userId());
        assertEquals("session-1", captor.getValue().sessionId());
        assertEquals("trace-1", captor.getValue().traceId());
    }

    @Test
    void typedTextStreamingAssignsRequestIdWhenCallerOmitsIt() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        org.mockito.Mockito.when(sdk.streamText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().empty());

        service.streamText(sdk, request).collect().asList().await().indefinitely();

        var captor = org.mockito.ArgumentCaptor.forClass(
                tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.class);
        org.mockito.Mockito.verify(sdk).streamText(captor.capture());
        assertEquals(false, captor.getValue().requestId() == null
                || captor.getValue().requestId().isBlank());
    }

    @Test
    void typedTextStreamingRecordsTimeToFirstTokenOnlyOnce() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider.noop()
                        .getAuditLogger();
            }
        };
        var serviceWithMetrics = new AqliChatService(observability);
        var empty = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 0, "", false, null,
                null,
                null, null, null, null, java.time.Instant.now(), java.util.Map.of());
        var firstToken = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 1, "a", false, null,
                null,
                null, null, null, null, java.time.Instant.now(), java.util.Map.of());
        var secondToken = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 2, "b", true,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP,
                null,
                null, null, null, null, java.time.Instant.now(), java.util.Map.of());
        org.mockito.Mockito.when(sdk.streamText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().items(empty, firstToken, secondToken));

        serviceWithMetrics.streamText(sdk, request).collect().asList().await().indefinitely();

        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.times(1))
                .recordClientTtft(org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.times(3)).recordClientChunk("model-id");
    }
    @Test
    void typedTextStreamingAuditsTerminalOutputUsagePerSubscription() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return org.mockito.Mockito.mock(
                        tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        var terminal = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-request", 1, "hello", true,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP,
                null, null, null, null,
                new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk.Usage(5, 2, 18),
                java.time.Instant.now(), java.util.Map.of());
        org.mockito.Mockito.when(sdk.streamText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().item(terminal));

        var stream = new AqliChatService(observability).streamText(sdk, request);
        stream.collect().asList().await().indefinitely();
        stream.collect().asList().await().indefinitely();

        org.mockito.Mockito.verify(audit, org.mockito.Mockito.times(2))
                .logClientRequestComplete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void legacyTextStreamingRecordsFirstOutputAndTerminalUsagePerSubscription() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        var empty = new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                "legacy-request", 0, tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                "", null, false, null, null, java.time.Instant.now(), java.util.Map.of(),
                null, null, null);
        var firstOutput = new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                "legacy-request", 1, tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                "a", null, false, null, null, java.time.Instant.now(), java.util.Map.of(),
                null, null, null);
        var terminal = new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                "legacy-request", 2, tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                "b", null, true, "stop",
                new tech.kayys.aqli.spi.inference.StreamingInferenceChunk.ChunkUsage(5, 2, 18),
                java.time.Instant.now(), java.util.Map.of(), null, null, null);
        org.mockito.Mockito.when(sdk.streamCompletion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().items(empty, firstOutput, terminal));

        var stream = new AqliChatService(observability).streamChat(
                sdk, "model-id", null, List.of(Message.user("hello")), List.of(), ChatParams.defaults());
        stream.collect().asList().await().indefinitely();
        stream.collect().asList().await().indefinitely();

        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.times(2))
                .recordClientTtft(org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(metrics, org.mockito.Mockito.times(6)).recordClientChunk("model-id");
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.times(2))
                .logClientRequestComplete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.eq(2),
                        org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void typedTextStreamingRecordsCancellationWithoutSuccessOrFailure() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        org.mockito.Mockito.when(sdk.streamText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().<tech.kayys.aqli.sdk.capability.text.TextGenerationChunk>emitter(emitter -> {}));

        var subscription = new AqliChatService(observability).streamText(sdk, request)
                .subscribe().with(chunk -> {}, failure -> {});
        subscription.cancel();

        org.mockito.Mockito.verify(metrics).recordClientCancellation(
                org.mockito.ArgumentMatchers.eq("model-id"), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit).logClientRequestCancelled(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("model-id"),
                org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).logClientRequestComplete(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).logClientRequestFailure(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void legacyTextStreamingRecordsCancellationWithoutSuccessOrFailure() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        org.mockito.Mockito.when(sdk.streamCompletion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().<tech.kayys.aqli.spi.inference.StreamingInferenceChunk>emitter(emitter -> {}));

        var subscription = new AqliChatService(observability).streamChat(
                sdk, "model-id", null, List.of(Message.user("hello")), List.of(), ChatParams.defaults())
                .subscribe().with(chunk -> {}, failure -> {});
        subscription.cancel();

        org.mockito.Mockito.verify(metrics).recordClientCancellation(
                org.mockito.ArgumentMatchers.eq("model-id"), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit).logClientRequestCancelled(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("model-id"),
                org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).logClientRequestComplete(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).logClientRequestFailure(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }


    @Test
    void throwingObservabilityCallbacksDoNotInterruptTypedTextStream() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var request = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestStart(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"));
        org.mockito.Mockito.doThrow(new IllegalStateException("metrics unavailable"))
                .when(metrics).recordClientChunk("model-id");
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestComplete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyLong());
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        var chunk = new tech.kayys.aqli.sdk.capability.text.TextGenerationChunk(
                "stream-1", 0, "hello", true,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP,
                null, null, null, null, null, java.time.Instant.now(), java.util.Map.of());
        org.mockito.Mockito.when(sdk.streamText(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().item(chunk));

        var result = new AqliChatService(observability).streamText(sdk, request)
                .collect().asList().await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("hello", result.getFirst().delta());
    }

    @Test
    void throwingObservabilityCallbacksDoNotInterruptLegacyTextStream() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestStart(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"));
        org.mockito.Mockito.doThrow(new IllegalStateException("metrics unavailable"))
                .when(metrics).recordClientChunk("model-id");
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestComplete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("model-id"),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyLong());
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        var chunk = new tech.kayys.aqli.spi.inference.StreamingInferenceChunk(
                "legacy-stream-1", 0, tech.kayys.aljabr.spi.model.ModalityType.TEXT,
                "hello", null, true, "stop", null, java.time.Instant.now(), java.util.Map.of(),
                null, null, null);
        org.mockito.Mockito.when(sdk.streamCompletion(org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.smallrye.mutiny.Multi.createFrom().item(chunk));

        var result = new AqliChatService(observability).streamChat(
                sdk, "model-id", null, List.of(Message.user("hello")), List.of(), ChatParams.defaults())
                .collect().asList().await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("hello", result.getFirst().delta());
    }

    @Test
    void typedAndLegacyUnaryExecutionKeepObservabilityFailuresIsolated() {
        var sdk = org.mockito.Mockito.mock(tech.kayys.aqli.sdk.runtime.AqliRuntime.class);
        var metrics = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector.class);
        var audit = org.mockito.Mockito.mock(
                tech.kayys.aqli.sdk.core.observability.SdkAuditLogger.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("metrics unavailable"))
                .when(metrics).recordClientSuccess(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestStart(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).logClientRequestComplete(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyLong());
        var observability = new tech.kayys.aqli.sdk.core.observability.SdkObservabilityProvider() {
            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkMetricsCollector getMetricsCollector() {
                return metrics;
            }

            @Override
            public tech.kayys.aqli.sdk.core.observability.SdkAuditLogger getAuditLogger() {
                return audit;
            }
        };
        var typedRequest = tech.kayys.aqli.sdk.capability.text.TextGenerationRequest.of("model-id", "hello");
        var typedResponse = new tech.kayys.aqli.sdk.capability.text.TextGenerationResponse(
                "hello", "model-id", 1, 1, 10,
                tech.kayys.aqli.sdk.capability.text.TextGenerationResponse.FinishReason.STOP);
        org.mockito.Mockito.when(sdk.generateTextAsync(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(typedResponse));
        var legacyResponse = tech.kayys.aqli.spi.inference.InferenceResponse.builder()
                .requestId("legacy-request")
                .content("hello")
                .model("model-id")
                .tokensUsed(2)
                .build();
        org.mockito.Mockito.when(sdk.createCompletionAsync(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(legacyResponse));

        var serviceWithObservability = new AqliChatService(observability);
        var typedResult = serviceWithObservability.generateText(sdk, typedRequest)
                .await().indefinitely();
        var legacyResult = serviceWithObservability.chat(
                sdk, "model-id", null, List.of(Message.user("hello")), List.of(), ChatParams.defaults())
                .await().indefinitely();

        assertEquals(typedResponse, typedResult);
        assertEquals(legacyResponse, legacyResult);
    }

}
