package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
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

@Path("/v1/agent/tools/validate")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentToolContractResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "validateAgentToolContract",
            summary = "Validate and normalize agent tool definitions",
            description = "Validates OpenAI-compatible function and MCP tool schemas, returns normalized summaries "
                    + "and portability warnings, and never executes or authorizes tools.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(
                    name = "validate-tools",
                    value = AgentOpenApiExamples.TOOL_VALIDATION_REQUEST)))
    @APIResponse(responseCode = "200", description = "Tool contract validation summary", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(
                    name = "tool-contract-validation",
                    value = AgentOpenApiExamples.TOOL_VALIDATION_RESPONSE)))
    @APIResponse(responseCode = "400", description = "Invalid tool contract", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "invalid-request", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response validate(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, payload);
        try {
            return trace.applyHeaders(Response.ok(AgentToolContractMapper.validatePayload(payload, trace))).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AgentErrorMapper.badRequest(e, trace);
        }
    }
}
