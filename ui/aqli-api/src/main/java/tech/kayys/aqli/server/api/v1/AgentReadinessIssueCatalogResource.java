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

@Path("/v1/agent/readiness/issues")
@Tag(name = "Agentic AI", description = "OpenAI-compatible serving endpoints for agent integrations")
public class AgentReadinessIssueCatalogResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getAgentReadinessIssueCatalog",
            summary = "Get readiness issue code metadata",
            description = "Returns stable readiness issue codes with default severity, summaries, and remediation "
                    + "text for non-Java dashboards and CI integrations.")
    @APIResponse(responseCode = "200", description = "Readiness issue catalog", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(
                    name = "agent-readiness-issue-catalog",
                    value = AgentOpenApiExamples.READINESS_ISSUE_CATALOG_RESPONSE)))
    public Response catalog() {
        return Response.ok(AgentReadinessIssueCatalogMapper.catalog()).build();
    }
}
