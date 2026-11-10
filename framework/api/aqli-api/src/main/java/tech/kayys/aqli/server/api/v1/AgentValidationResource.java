package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

@Path("/v1/agent/validate")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentValidationResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "validateAgentRequest",
            summary = "Validate and normalize an agent-facing request",
            description = "Validates chat, Responses, or embedding payloads using the same mapper as the serving "
                    + "endpoints, then returns a safe normalized summary without invoking a model.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "validate-chat", value = AgentOpenApiExamples.VALIDATION_REQUEST)))
    @APIResponse(responseCode = "200", description = "Validation summary", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "validation-summary", value = AgentOpenApiExamples.VALIDATION_RESPONSE)))
    @APIResponse(responseCode = "400", description = "Invalid request", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response validate(
            @Context HttpHeaders headers,
            @QueryParam("surface") String surface,
            JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        try {
            return trace.applyHeaders(Response.ok(
                    AgentValidationMapper.validate(surface, headers, payload, trace))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AgentErrorMapper.badRequest(e, trace);
        }
    }
}
