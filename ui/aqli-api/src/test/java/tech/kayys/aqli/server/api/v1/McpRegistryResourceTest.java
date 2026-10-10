package tech.kayys.aqli.server.api.v1;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.sdk.mcp.McpAddRequest;
import tech.kayys.aqli.sdk.mcp.McpDoctorReport;
import tech.kayys.aqli.sdk.mcp.McpEditRequest;
import tech.kayys.aqli.sdk.mcp.McpRegistryManager;
import tech.kayys.aqli.sdk.mcp.McpServerSummary;
import tech.kayys.aqli.sdk.mcp.McpServerView;
import tech.kayys.aqli.sdk.mcp.McpTestReport;
import tech.kayys.aqli.sdk.mcp.McpToolModel;
import tech.kayys.aqli.server.SdkProvider;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpRegistryResourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void listsRegistryServersFromFixture() {
        McpRegistryResource resource = resourceWithFixtureRegistry();

        Response response = resource.listServers();

        assertEquals(200, response.getStatus());
        Map<String, Object> payload = (Map<String, Object>) response.getEntity();
        assertEquals(true, payload.get("available"));
        assertEquals("/fixture/aqli/mcp.json", payload.get("registry_path"));

        List<Map<String, Object>> servers = (List<Map<String, Object>>) payload.get("servers");
        assertEquals(2, servers.size());
        assertEquals("knowledge-base", servers.get(0).get("name"));
        assertEquals(true, servers.get(0).get("enabled"));
        assertEquals("ops/disabled", servers.get(1).get("name"));
        assertEquals(false, servers.get(1).get("enabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsOpenAiCompatibleToolsFromEnabledMcpServers() {
        McpRegistryResource resource = resourceWithFixtureRegistry();

        Response response = resource.listTools("openai", true);

        assertEquals(200, response.getStatus());
        Map<String, Object> payload = (Map<String, Object>) response.getEntity();
        assertEquals(true, payload.get("available"));
        assertEquals("openai", payload.get("compat"));
        assertEquals(true, payload.get("enabled_only"));

        Map<String, Object> boundary = (Map<String, Object>) payload.get("boundary");
        assertEquals("discovery_only", boundary.get("role"));
        assertFalse((Boolean) boundary.get("tool_execution"));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) payload.get("tools");
        assertEquals(1, tools.size());

        Map<String, Object> tool = tools.get(0);
        assertEquals("function", tool.get("type"));

        Map<String, Object> function = (Map<String, Object>) tool.get("function");
        assertEquals("mcp_knowledge-base_search_docs", function.get("name"));
        assertEquals("Search indexed Aqli docs", function.get("description"));

        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        assertEquals("object", parameters.get("type"));
        assertEquals(List.of("query"), parameters.get("required"));

        Map<String, Object> metadata = (Map<String, Object>) tool.get("x_aqli");
        assertEquals("knowledge-base", metadata.get("mcp_server"));
        assertEquals("search.docs", metadata.get("mcp_tool_name"));
        assertEquals(false, metadata.get("tool_execution"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canIncludeDisabledServerToolsWhenExplicitlyRequested() {
        McpRegistryResource resource = resourceWithFixtureRegistry();

        Response response = resource.listTools(null, false);

        assertEquals(200, response.getStatus());
        Map<String, Object> payload = (Map<String, Object>) response.getEntity();
        assertEquals("mcp", payload.get("compat"));
        assertEquals(false, payload.get("enabled_only"));

        List<Map<String, Object>> tools = (List<Map<String, Object>>) payload.get("tools");
        assertEquals(2, tools.size());
        assertEquals("search.docs", tools.get(0).get("name"));
        assertEquals("rotate-key", tools.get(1).get("name"));
        assertEquals(false, tools.get(1).get("execution"));
    }

    private McpRegistryResource resourceWithFixtureRegistry() {
        McpRegistryResource resource = new McpRegistryResource();
        resource.sdkProvider = new FixtureSdkProvider();
        return resource;
    }

    private static class FixtureSdkProvider extends SdkProvider {
        private final AqliSdk sdk = (AqliSdk) Proxy.newProxyInstance(
                AqliSdk.class.getClassLoader(),
                new Class<?>[] { AqliSdk.class },
                (proxy, method, args) -> {
                    if ("mcpRegistry".equals(method.getName())) {
                        return new FixtureMcpRegistry();
                    }
                    if ("toString".equals(method.getName())) {
                        return "FixtureAqliSdk";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        @Override
        public AqliSdk getSdk() {
            return sdk;
        }
    }

    private static class FixtureMcpRegistry implements McpRegistryManager {
        @Override
        public String registryPath() {
            return "/fixture/aqli/mcp.json";
        }

        @Override
        public List<McpServerSummary> list() {
            return List.of(
                    new McpServerSummary("knowledge-base", true),
                    new McpServerSummary("ops/disabled", false));
        }

        @Override
        public McpServerView show(String name) throws SdkException {
            if ("knowledge-base".equals(name)) {
                return new McpServerView(name, true, "stdio", "npx", 2, 1, null, "{}");
            }
            if ("ops/disabled".equals(name)) {
                return new McpServerView(name, false, "http", null, 0, 0, "https://ops.example/mcp", "{}");
            }
            throw new SdkException("MCP_NOT_FOUND", "MCP server not found: " + name);
        }

        @Override
        public List<McpToolModel> listTools(String name) {
            if ("knowledge-base".equals(name)) {
                return List.of(new McpToolModel(
                        "search.docs",
                        "Search indexed Aqli docs",
                        Map.of(
                                "type", "object",
                                "required", List.of("query"),
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "Search query")))));
            }
            if ("ops/disabled".equals(name)) {
                return List.of(new McpToolModel(
                        "rotate-key",
                        "Rotate an integration key",
                        Map.of("type", "object")));
            }
            return List.of();
        }

        @Override
        public List<String> add(McpAddRequest request) {
            throw unsupportedMutation();
        }

        @Override
        public void remove(String name) {
            throw unsupportedMutation();
        }

        @Override
        public void rename(String oldName, String newName) {
            throw unsupportedMutation();
        }

        @Override
        public void edit(McpEditRequest request) {
            throw unsupportedMutation();
        }

        @Override
        public void setEnabled(String name, boolean enabled) {
            throw unsupportedMutation();
        }

        @Override
        public int importFromFile(String filePath, boolean replace) {
            throw unsupportedMutation();
        }

        @Override
        public int exportToFile(String filePath, String name) {
            throw unsupportedMutation();
        }

        @Override
        public McpDoctorReport doctor() {
            throw unsupportedMutation();
        }

        @Override
        public McpTestReport test(String name, boolean all, long timeoutMs) {
            throw unsupportedMutation();
        }

        private UnsupportedOperationException unsupportedMutation() {
            return new UnsupportedOperationException("fixture registry is read-only");
        }
    }
}
