package tech.kayys.aqli.server.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/agent/contract")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentContractResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAgentContract",
            summary = "Get the machine-readable agent integration contract",
            description = "Describes endpoint paths, request schema hints, auth options, and the "
                    + "serving/orchestrator ownership boundary.")
    @APIResponse(responseCode = "200", description = "Agent integration contract", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "agent-contract", value = AgentOpenApiExamples.AGENT_CONTRACT_RESPONSE)))
    public Response contract() {
        return Response.ok(AgentContractMapper.contract()).build();
    }
}
