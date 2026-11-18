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
import tech.kayys.aqli.sdk.capability.text.TextGenerationResponse;
import tech.kayys.aqli.sdk.capability.text.TextGenerationRequest;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.ModalityType;

import java.util.List;
import java.util.Map;

@Path("/v1/responses")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class OpenAiResponsesResource {

    @Inject
    SdkProvider sdkProvider;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createResponse",
            summary = "Create an OpenAI Responses-compatible response",
            description = "Accepts Responses-style input, instructions, tools, function-call loop items, "
                    + "and caller-supplied RAG context.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agentic-response", value = AgentOpenApiExamples.RESPONSES_REQUEST)))
    @APIResponse(responseCode = "200", description = "Response object", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "response", value = AgentOpenApiExamples.RESPONSES_RESPONSE)))
    @APIResponse(responseCode = "400", description = "Invalid Responses request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response createResponse(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        try {
            TextGenerationRequest request = AgenticApiMapper.toResponsesTextGenerationRequest(
                    payload, AgenticApiMapper.apiKey(headers), trace);
            if (payload.path("stream").asBoolean(false)) {
                return AgentErrorMapper.badRequest(
                        "Streaming responses require Accept: text/event-stream.",
                        AgentErrorMapper.UNSUPPORTED_STREAMING_ACCEPT,
                        trace);
            }
            TextGenerationResponse generated = sdkProvider.getSdk().generateText(request);
            InferenceResponse response = toInferenceResponse(generated, request);
            return trace.applyHeaders(Response.ok(
                    AgenticApiMapper.toOpenAiResponse(response, request.model(), trace))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
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
            operationId = "streamResponse",
            summary = "Stream an OpenAI Responses-compatible response",
            description = "Streams response lifecycle and output text events as server-sent events.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agentic-response-stream", value = AgentOpenApiExamples.RESPONSES_STREAM_REQUEST)))
    @APIResponse(responseCode = "200", description = "Responses event stream", content = @Content(
            mediaType = MediaType.SERVER_SENT_EVENTS,
            examples = @ExampleObject(name = "responses-stream", value = AgentOpenApiExamples.RESPONSES_STREAM)))
    @APIResponse(responseCode = "400", description = "Invalid Responses streaming request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Multi<String> streamResponse(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        AgentStreamOptions streamOptions = AgentStreamOptions.from(payload);
        try {
            TextGenerationRequest request = AgenticApiMapper.toResponsesTextGenerationRequest(
                    payload, AgenticApiMapper.apiKey(headers), trace);
            return openAiResponseStream(request, trace, streamOptions);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw AgentErrorMapper.badRequestException(e, trace);
        }
    }

    private Multi<String> openAiResponseStream(
            TextGenerationRequest request, AgentTraceContext trace, AgentStreamOptions streamOptions) {
        String requestId = request.requestId() != null ? request.requestId() : trace.requestId();
        InferenceRequest eventRequest = InferenceRequest.builder()
                .requestId(requestId)
                .model(request.model())
                .build();
        StringBuilder outputText = new StringBuilder();
        Multi<String> body = Multi.createFrom().publisher(sdkProvider.getSdk().streamText(request))
                .onItem().transformToMultiAndConcatenate(chunk -> {
                    StreamingInferenceChunk spiChunk = toStreamingChunk(chunk, requestId);
                    if (!spiChunk.delta().isEmpty()) outputText.append(spiChunk.delta());
                    List<String> events = AgenticApiMapper.toOpenAiResponseStreamJson(
                            spiChunk, request.model(), outputText.toString(), trace, streamOptions);
                    return Multi.createFrom().items(events.toArray(String[]::new));
                })
                .onFailure().recoverWithMulti(error -> Multi.createFrom().items(
                        AgenticApiMapper.toOpenAiResponseStreamErrorJson(error, trace)));

        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item(AgenticApiMapper.toOpenAiResponseStreamCreatedJson(
                        eventRequest, trace, streamOptions)),
                body,
                Multi.createFrom().item("[DONE]"));
    }

    private static InferenceResponse toInferenceResponse(
            TextGenerationResponse generated, TextGenerationRequest request) {
        InferenceResponse.FinishReason finishReason = switch (generated.finishReason()) {
            case LENGTH -> InferenceResponse.FinishReason.LENGTH;
            case TOOL_CALLS -> InferenceResponse.FinishReason.TOOL_CALLS;
            case ERROR -> InferenceResponse.FinishReason.ERROR;
            default -> InferenceResponse.FinishReason.STOP;
        };
        InferenceResponse.Builder response = InferenceResponse.builder()
                .requestId(request.requestId() != null ? request.requestId() : java.util.UUID.randomUUID().toString())
                .content(generated.content())
                .model(generated.model() != null ? generated.model() : request.model())
                .inputTokens(generated.inputTokens())
                .outputTokens(generated.outputTokens())
                .tokensUsed(generated.totalTokens())
                .durationMs(generated.durationMs())
                .finishReason(finishReason)
                .sessionId(request.sessionId())
                .metadata(Map.of());
        generated.toolCalls().forEach(call -> response.toolCall(
                new InferenceResponse.ToolCall(call.getFunction().getName(), call.getArguments())));
        return response.build();
    }

    private static StreamingInferenceChunk toStreamingChunk(TextGenerationChunk chunk, String requestId) {
        StreamingInferenceChunk.ChunkUsage usage = chunk.usage() == null ? null
                : new StreamingInferenceChunk.ChunkUsage(
                        chunk.usage().inputTokens(), chunk.usage().outputTokens(), chunk.usage().latencyMs());
        String finishReason = chunk.finishReason() == null ? null
                : chunk.finishReason().name().toLowerCase(java.util.Locale.ROOT);
        String toolCallId = chunk.toolCallId();
        String toolName = chunk.toolCallEvent() == TextGenerationChunk.ToolCallEvent.START ? chunk.toolName() : null;
        String toolInputDelta = chunk.toolCallEvent() == TextGenerationChunk.ToolCallEvent.DELTA
                ? chunk.toolInputDelta() : null;
        return new StreamingInferenceChunk(
                requestId,
                Math.toIntExact(chunk.sequence()),
                ModalityType.TEXT,
                chunk.delta(),
                null,
                chunk.terminal(),
                finishReason,
                usage,
                chunk.emittedAt(),
                chunk.metadata(),
                toolCallId,
                toolName,
                toolInputDelta);
    }
}
