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

@Path("/v1/agent/preflight")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentPreflightResource {

    @Inject
    SdkProvider sdkProvider;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "preflightAgentServingIntegration",
            summary = "Preflight an agent-serving integration",
            description = "Aggregates serving capability, model route, MCP discovery, tool contract, and request "
                    + "validation checks. This endpoint is validation-only and never invokes a model, executes "
                    + "tools, authorizes tools, or runs retrieval.")
    @RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agent-preflight", value = AgentOpenApiExamples.PREFLIGHT_REQUEST)))
    @APIResponse(responseCode = "200", description = "Agent serving readiness report", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agent-preflight-report", value = AgentOpenApiExamples.PREFLIGHT_RESPONSE)))
    @APIResponse(responseCode = "500", description = "Server error", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "server-error", value = AgentOpenApiExamples.ERROR_RESPONSE)))
    public Response preflight(@Context HttpHeaders headers, JsonNode payload) {
        AgentTraceContext trace = AgentTraceContext.from(headers, AgentPreflightMapper.tracePayload(payload));
        try {
            return trace.applyHeaders(Response.ok(
                    AgentPreflightMapper.preflight(headers, payload, trace, sdkProvider.getSdk()))).build();
        } catch (Exception e) {
            return AgentErrorMapper.serverError(e, trace);
        }
    }
}
