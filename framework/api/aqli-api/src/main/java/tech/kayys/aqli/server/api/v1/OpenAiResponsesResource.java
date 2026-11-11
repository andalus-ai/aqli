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

import java.util.List;

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
            InferenceRequest request = AgenticApiMapper.toResponsesInferenceRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace);
            if (request.isStreaming()) {
                return AgentErrorMapper.badRequest(
                        "Streaming responses require Accept: text/event-stream.",
                        AgentErrorMapper.UNSUPPORTED_STREAMING_ACCEPT,
                        trace);
            }

            InferenceResponse response = sdkProvider.getSdk().createCompletion(request);
            return trace.applyHeaders(Response.ok(
                    AgenticApiMapper.toOpenAiResponse(response, request.getModel(), trace))).build();
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
            InferenceRequest request = AgenticApiMapper.toResponsesInferenceRequest(
                    payload,
                    AgenticApiMapper.apiKey(headers),
                    trace)
                    .toBuilder()
                    .streaming(true)
                    .build();
            return openAiResponseStream(request, trace, streamOptions);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw AgentErrorMapper.badRequestException(e, trace);
        }
    }

    private Multi<String> openAiResponseStream(
            InferenceRequest request, AgentTraceContext trace, AgentStreamOptions streamOptions) {
        StringBuilder outputText = new StringBuilder();
        Multi<String> body = sdkProvider.getSdk()
                .streamCompletion(request)
                .onItem().transformToMultiAndConcatenate(chunk -> {
                    if (chunk.delta() != null) {
                        outputText.append(chunk.delta());
                    }
                    List<String> events = AgenticApiMapper.toOpenAiResponseStreamJson(
                            chunk,
                            request.getModel(),
                            outputText.toString(),
                            trace,
                            streamOptions);
                    return Multi.createFrom().items(events.toArray(String[]::new));
                })
                .onFailure().recoverWithMulti(error -> Multi.createFrom().items(
                        AgenticApiMapper.toOpenAiResponseStreamErrorJson(error, trace)));

        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item(AgenticApiMapper.toOpenAiResponseStreamCreatedJson(
                        request,
                        trace,
                        streamOptions)),
                body,
                Multi.createFrom().item("[DONE]"));
    }
}
