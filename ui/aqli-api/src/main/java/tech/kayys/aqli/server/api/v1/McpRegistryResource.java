package tech.kayys.aqli.server.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.mcp.McpRegistryManager;
import tech.kayys.aqli.sdk.mcp.McpServerSummary;
import tech.kayys.aqli.sdk.mcp.McpServerView;
import tech.kayys.aqli.sdk.mcp.McpToolModel;
import tech.kayys.aqli.server.SdkProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Path("/v1/mcp")
@Tag(name = "MCP Discovery", description = "Read-only MCP registry and tool schema discovery")
public class McpRegistryResource {

    @Inject
    SdkProvider sdkProvider;

    @GET
    @Path("/servers")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listMcpServers",
            summary = "List registered MCP servers",
            description = "Read-only MCP server discovery. Aqli does not execute MCP tools.")
    @APIResponse(responseCode = "200", description = "MCP server registry view", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "mcp-servers", value = AgentOpenApiExamples.MCP_SERVERS_RESPONSE)))
    public Response listServers() {
        Optional<McpRegistryManager> registry = registry();
        if (registry.isEmpty()) {
            return Response.ok(unavailablePayload("servers", List.of())).build();
        }
        try {
            List<Map<String, Object>> servers = registry.get().list().stream()
                    .map(this::serverSummary)
                    .toList();
            Map<String, Object> payload = basePayload(true, registry.get().registryPath());
            payload.put("servers", servers);
            return Response.ok(payload).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/servers/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "showMcpServer",
            summary = "Show an MCP server registration",
            description = "Returns a redacted MCP server registration for discovery and diagnostics.")
    public Response showServer(@PathParam("name") String name) {
        Optional<McpRegistryManager> registry = registry();
        if (registry.isEmpty()) {
            return unavailableRegistryResponse();
        }
        try {
            Map<String, Object> payload = basePayload(true, registry.get().registryPath());
            payload.put("server", serverView(registry.get().show(name)));
            return Response.ok(payload).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/servers/{name}/tools")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listMcpServerTools",
            summary = "List tools for one MCP server",
            description = "Returns MCP tool schemas or OpenAI-compatible function definitions when compat=openai.")
    @APIResponse(responseCode = "200", description = "MCP tool definitions", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "mcp-tools", value = AgentOpenApiExamples.MCP_TOOLS_RESPONSE)))
    public Response listServerTools(@PathParam("name") String name, @QueryParam("compat") String compat) {
        Optional<McpRegistryManager> registry = registry();
        if (registry.isEmpty()) {
            Map<String, Object> payload = unavailablePayload("tools", List.of());
            payload.put("server", name);
            payload.put("compat", compatValue(compat));
            return Response.ok(payload).build();
        }
        try {
            List<Map<String, Object>> tools = registry.get().listTools(name).stream()
                    .map(tool -> toolView(name, tool, isOpenAiCompat(compat)))
                    .toList();
            Map<String, Object> payload = basePayload(true, registry.get().registryPath());
            payload.put("server", name);
            payload.put("compat", compatValue(compat));
            payload.put("tools", tools);
            return Response.ok(payload).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    @GET
    @Path("/tools")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listMcpTools",
            summary = "List MCP tools across registered servers",
            description = "Aggregates enabled MCP server tool schemas by default. Use compat=openai for "
                    + "OpenAI-compatible function tool definitions.")
    @APIResponse(responseCode = "200", description = "MCP tool definitions", content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            examples = @ExampleObject(name = "mcp-tools", value = AgentOpenApiExamples.MCP_TOOLS_RESPONSE)))
    public Response listTools(
            @QueryParam("compat") String compat,
            @QueryParam("enabledOnly") @DefaultValue("true") boolean enabledOnly) {
        Optional<McpRegistryManager> registry = registry();
        if (registry.isEmpty()) {
            Map<String, Object> payload = unavailablePayload("tools", List.of());
            payload.put("compat", compatValue(compat));
            payload.put("enabled_only", enabledOnly);
            return Response.ok(payload).build();
        }
        try {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (McpServerSummary server : registry.get().list()) {
                if (enabledOnly && !server.enabled()) {
                    continue;
                }
                for (McpToolModel tool : registry.get().listTools(server.name())) {
                    tools.add(toolView(server.name(), tool, isOpenAiCompat(compat)));
                }
            }
            Map<String, Object> payload = basePayload(true, registry.get().registryPath());
            payload.put("compat", compatValue(compat));
            payload.put("enabled_only", enabledOnly);
            payload.put("tools", tools);
            return Response.ok(payload).build();
        } catch (Exception e) {
            return serverError(e);
        }
    }

    private Optional<McpRegistryManager> registry() {
        try {
            return Optional.ofNullable(sdkProvider.getSdk().mcpRegistry());
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> basePayload(boolean available, String registryPath) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", available);
        payload.put("registry_path", registryPath);
        payload.put("boundary", boundary());
        return payload;
    }

    private Map<String, Object> unavailablePayload(String collectionName, Object value) {
        Map<String, Object> payload = basePayload(false, null);
        payload.put(collectionName, value);
        payload.put("message", "MCP registry is not available in this SDK runtime.");
        return payload;
    }

    private Map<String, Object> boundary() {
        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("role", "discovery_only");
        boundary.put("aqli_exposes", List.of("registered_servers", "tool_schemas"));
        boundary.put("agent_orchestrator_owns",
                List.of("tool_authorization", "tool_execution", "tool_result_loop"));
        boundary.put("tool_execution", false);
        return boundary;
    }

    private Map<String, Object> serverSummary(McpServerSummary server) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", server.name());
        item.put("enabled", server.enabled());
        return item;
    }

    private Map<String, Object> serverView(McpServerView server) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", server.name());
        item.put("enabled", server.enabled());
        item.put("transport", server.transport());
        item.put("command", server.command());
        item.put("args_count", server.argsCount());
        item.put("env_keys", server.envKeys());
        item.put("url", server.url());
        item.put("raw_json_redacted", true);
        return item;
    }

    private Map<String, Object> toolView(String serverName, McpToolModel tool, boolean openAiCompat) {
        if (openAiCompat) {
            return openAiToolView(serverName, tool);
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "mcp_tool");
        item.put("server", serverName);
        item.put("name", tool.name());
        item.put("description", tool.description());
        item.put("input_schema", tool.inputSchema());
        item.put("execution", false);
        return item;
    }

    private Map<String, Object> openAiToolView(String serverName, McpToolModel tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", openAiFunctionName(serverName, tool.name()));
        function.put("description", tool.description());
        function.put("parameters", tool.inputSchema());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mcp_server", serverName);
        metadata.put("mcp_tool_name", tool.name());
        metadata.put("tool_execution", false);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function");
        item.put("function", function);
        item.put("x_aqli", metadata);
        return item;
    }

    private String openAiFunctionName(String serverName, String toolName) {
        String sourceServer = serverName == null || serverName.isBlank() ? "server" : serverName;
        String sourceTool = toolName == null || toolName.isBlank() ? "tool" : toolName;
        String name = ("mcp_" + sourceServer + "_" + sourceTool)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
        if (name.length() <= 64) {
            return name;
        }
        return name.substring(0, 64);
    }

    private boolean isOpenAiCompat(String value) {
        return value != null && "openai".equalsIgnoreCase(value.trim());
    }

    private String compatValue(String value) {
        return isOpenAiCompat(value) ? "openai" : "mcp";
    }

    private Response unavailableRegistryResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of(
                        "error", Map.of(
                                "message", "MCP registry is not available in this SDK runtime.",
                                "type", "mcp_registry_unavailable")))
                .build();
    }

    private Response serverError(Exception e) {
        if (e instanceof SdkException) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of(
                            "error", Map.of("message", errorMessage(e, "MCP registry request failed."),
                                    "type", "mcp_registry_error")))
                    .build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                        "error", Map.of("message", errorMessage(e, "Server error."),
                                "type", "server_error")))
                .build();
    }

    private String errorMessage(Exception e, String fallback) {
        return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage();
    }
}
