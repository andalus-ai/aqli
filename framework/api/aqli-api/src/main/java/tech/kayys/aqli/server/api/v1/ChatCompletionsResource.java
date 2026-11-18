package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.aqli.server.SdkProvider;
import tech.kayys.aqli.sdk.capability.text.TextGenerationChunk;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModalityType;

@Path("/v1/chat/completions")
@Tag(name = "Chat Completions", description = "OpenAI-compatible chat completion API for agent integrations")
public class ChatCompletionsResource {

    @Inject
    SdkProvider sdkProvider;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createChatCompletion",
            summary = "Create an OpenAI-compatible chat completion",
            description = "Accepts messages, system prompts, tools, MCP metadata, and RAG context. "
                    + "Aqli serves inference and does not execute tools.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agentic-chat", value = AgentOpenApiExamples.CHAT_REQUEST)))
    @APIResponse(responseCode = "200", description = "Chat completion", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "chat-completion", value = AgentOpenApiExamples.CHAT_RESPONSE)))
    @APIResponse(responseCode = "400", description = "Invalid chat completion request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response createChatCompletion(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        try {
            TextGenerationRequest request = AgenticApiMapper.toTextGenerationRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace);
            if (payload.path("stream").asBoolean(false)) {
                return AgentErrorMapper.badRequest(
                        "Streaming chat completions require Accept: text/event-stream.",
                        AgentErrorMapper.UNSUPPORTED_STREAMING_ACCEPT,
                        trace);
            }

            TextGenerationResponse generated = sdkProvider.getSdk().generateText(request);
            InferenceResponse response = toInferenceResponse(generated, request);
            return trace.applyHeaders(Response.ok(
                    AgenticApiMapper.toOpenAiChatResponse(response, request.model(), trace))).build();
        } catch (IllegalArgumentException e) {
            return AgentErrorMapper.badRequest(e, trace);
        } catch (Exception e) {
            return AgentErrorMapper.serverError(e, trace);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Operation(
            operationId = "streamChatCompletion",
            summary = "Stream an OpenAI-compatible chat completion",
            description = "Streams chat completion chunks as server-sent events. Request shape matches "
                    + "the non-streaming chat endpoint with stream semantics.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agentic-chat-stream", value = AgentOpenApiExamples.CHAT_STREAM_REQUEST)))
    @APIResponse(responseCode = "200", description = "Chat completion event stream", content = @Content(
            mediaType = MediaType.SERVER_SENT_EVENTS,
            examples = @ExampleObject(name = "chat-stream", value = AgentOpenApiExamples.CHAT_STREAM)))
    @APIResponse(responseCode = "400", description = "Invalid chat streaming request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Multi<String> streamChatCompletion(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        AgentStreamOptions streamOptions = AgentStreamOptions.from(payload);
        try {
            TextGenerationRequest request = AgenticApiMapper.toTextGenerationRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace);
            return openAiStream(request, trace, streamOptions);
        } catch (IllegalArgumentException e) {
            throw AgentErrorMapper.badRequestException(e, trace);
        }
    }

    private Multi<String> openAiStream(TextGenerationRequest request, AgentTraceContext trace, AgentStreamOptions streamOptions) {
        return Multi.createFrom().publisher(sdkProvider.getSdk().streamText(request))
                .map(chunk -> AgenticApiMapper.toOpenAiChatStreamJson(
                        toStreamingInferenceChunk(chunk, request), request.model(), trace, streamOptions))
                .onCompletion().continueWith("[DONE]")
                .onFailure().recoverWithMulti(error -> Multi.createFrom().items(
                        AgenticApiMapper.toOpenAiChatStreamErrorJson(error, trace), "[DONE]"));
    }

    private static InferenceResponse toInferenceResponse(TextGenerationResponse generated, TextGenerationRequest request) {
        InferenceResponse.Builder builder = InferenceResponse.builder()
                .requestId(request.requestId() != null ? request.requestId() : java.util.UUID.randomUUID().toString())
                .content(generated.content())
                .model(generated.model() != null ? generated.model() : request.model())
                .inputTokens(generated.inputTokens())
                .outputTokens(generated.outputTokens())
                .tokensUsed(generated.totalTokens())
                .durationMs(generated.durationMs())
                .finishReason(switch (generated.finishReason()) {
                    case LENGTH -> InferenceResponse.FinishReason.LENGTH;
                    case TOOL_CALLS -> InferenceResponse.FinishReason.TOOL_CALLS;
                    case ERROR -> InferenceResponse.FinishReason.ERROR;
                    default -> InferenceResponse.FinishReason.STOP;
                })
                .metadata(request.metadata());
        for (tech.kayys.aqli.spi.tool.ToolCall call : generated.toolCalls()) {
            builder.toolCall(new InferenceResponse.ToolCall(call.getFunction().getName(), call.getArguments()));
        }
        return builder.build();
    }

    private static StreamingInferenceChunk toStreamingInferenceChunk(TextGenerationChunk chunk, TextGenerationRequest request) {
        TextGenerationChunk.Usage sourceUsage = chunk.usage();
        StreamingInferenceChunk.ChunkUsage usage = sourceUsage == null ? null
                : new StreamingInferenceChunk.ChunkUsage(sourceUsage.inputTokens(), sourceUsage.outputTokens(), sourceUsage.latencyMs());
        String finishReason = chunk.finishReason() == null ? null
                : chunk.finishReason().name().toLowerCase(java.util.Locale.ROOT);
        return new StreamingInferenceChunk(
                chunk.requestId() != null ? chunk.requestId() : request.requestId(),
                Math.toIntExact(chunk.sequence()), ModalityType.TEXT, chunk.delta(), null, chunk.terminal(),
                finishReason, usage, chunk.emittedAt(), chunk.metadata(),
                chunk.toolCallId(), chunk.toolName(), chunk.toolInputDelta());
    }
}
