package tech.kayys.aqli.server.api.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/agent")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentCapabilitiesResource {

    @GET
    @Path("/capabilities")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAgentCapabilities",
            summary = "Get agent-facing service capabilities",
            description = "Summarizes Aqli's serving-engine role, supported compatibility surfaces, "
                    + "and endpoint discovery links.")
    @APIResponse(responseCode = "200", description = "Agent-facing capability discovery")
    public Response capabilities() {
        return Response.ok(AgentCapabilitiesMapper.capabilities()).build();
    }
}
