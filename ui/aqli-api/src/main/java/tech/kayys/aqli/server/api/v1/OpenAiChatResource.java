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
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;

@Path("/v1/chat/completions")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class OpenAiChatResource {

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
            InferenceRequest request = AgenticApiMapper.toInferenceRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace);
            if (request.isStreaming()) {
                return AgentErrorMapper.badRequest(
                        "Streaming chat completions require Accept: text/event-stream.",
                        AgentErrorMapper.UNSUPPORTED_STREAMING_ACCEPT,
                        trace);
            }

            InferenceResponse response = sdkProvider.getSdk().createCompletion(request);
            return trace.applyHeaders(Response.ok(
                    AgenticApiMapper.toOpenAiChatResponse(response, request.getModel(), trace))).build();
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
            InferenceRequest request = AgenticApiMapper.toInferenceRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace)
                    .toBuilder()
                    .streaming(true)
                    .build();
            return openAiStream(request, trace, streamOptions);
        } catch (IllegalArgumentException e) {
            throw AgentErrorMapper.badRequestException(e, trace);
        }
    }

    private Multi<String> openAiStream(
            InferenceRequest request, AgentTraceContext trace, AgentStreamOptions streamOptions) {
        return sdkProvider.getSdk()
                .streamCompletion(request)
                .map(chunk -> AgenticApiMapper.toOpenAiChatStreamJson(
                        chunk,
                        request.getModel(),
                        trace,
                        streamOptions))
                .onCompletion().continueWith("[DONE]")
                .onFailure().recoverWithMulti(error -> Multi.createFrom().items(
                        AgenticApiMapper.toOpenAiChatStreamErrorJson(error, trace),
                        "[DONE]"));
    }
}
