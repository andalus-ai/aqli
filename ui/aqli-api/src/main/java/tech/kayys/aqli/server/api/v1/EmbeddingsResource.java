package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import tech.kayys.aqli.server.SdkProvider;
import tech.kayys.aqli.spi.embedding.EmbeddingRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingResponse;

@Path("/v1/embeddings")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class EmbeddingsResource {

    @Inject
    SdkProvider sdkProvider;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createEmbedding",
            summary = "Create embeddings",
            description = "Creates embeddings for RAG pipelines. Accepts OpenAI-compatible input or native inputs.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "openai-embedding", value = AgentOpenApiExamples.EMBEDDINGS_REQUEST)))
    @APIResponse(responseCode = "200", description = "Embedding response", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "embedding-response", value = AgentOpenApiExamples.EMBEDDINGS_RESPONSE)))
    @APIResponse(responseCode = "400", description = "Invalid embedding request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response createEmbedding(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        try {
            var sdk = sdkProvider.getSdk();
            EmbeddingRequest request = AgenticApiMapper.toEmbeddingRequest(payload, trace);
            EmbeddingResponse resp = sdk.createEmbedding(request);
            Object body = AgenticApiMapper.isOpenAiEmbeddingRequest(payload)
                    ? AgenticApiMapper.toOpenAiEmbeddingResponse(resp, trace)
                    : resp;
            return trace.applyHeaders(Response.ok(body)).build();
        } catch (IllegalArgumentException e) {
            return AgentErrorMapper.badRequest(e, trace);
        } catch (Exception e) {
            return AgentErrorMapper.serverError(e, trace);
        }
    }
}
