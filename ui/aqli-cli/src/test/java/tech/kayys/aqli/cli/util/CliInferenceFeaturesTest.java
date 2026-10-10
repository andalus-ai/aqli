package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CliInferenceFeaturesTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsOpenAiStyleToolsAndMcpToolHints() throws Exception {
        Path tools = tempDir.resolve("tools.json");
        Files.writeString(tools, """
                {
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "lookupWeather",
                        "description": "Look up weather",
                        "parameters": { "type": "object" }
                      }
                    }
                  ]
                }
                """);

        List<ToolDefinition> definitions = CliInferenceFeatures.loadTools(
                List.of(tools.toString()),
                List.of("filesystem/read_file"));

        assertEquals(2, definitions.size());
        assertEquals("lookupWeather", definitions.get(0).getName());
        assertEquals(ToolDefinition.Type.FUNCTION, definitions.get(0).getType());
        assertEquals("read_file", definitions.get(1).getName());
        assertEquals(ToolDefinition.Type.MCP_TOOL, definitions.get(1).getType());
        assertEquals("filesystem", definitions.get(1).getMetadata().get("mcp_server"));
    }

    @Test
    void appliesToolsRagAndEmbeddingMetadataToRequest() throws Exception {
        Path context = tempDir.resolve("context.txt");
        Files.writeString(context, "Aqli supports local inference and retrieval context.");
        var rag = CliInferenceFeatures.loadRagContext(List.of("Inline fact."), List.of(context.toString()), 1000);
        var tools = CliInferenceFeatures.loadTools(List.of(), List.of("search"));

        InferenceRequest.Builder builder = InferenceRequest.builder()
                .model("test-model")
                .message(Message.user("What does Aqli support?"));
        CliInferenceFeatures.applyTools(builder, tools, "auto");
        CliInferenceFeatures.applyRagMetadata(builder, rag, "embed-model");

        InferenceRequest request = builder.build();

        assertEquals(1, request.getTools().size());
        assertEquals("auto", request.getToolChoice());
        assertEquals(true, request.getParameters().get("rag_enabled"));
        assertEquals("embed-model", request.getParameters().get("embedding_model"));
        assertEquals("full_context", request.getParameters().get("rag_strategy"));
        assertTrue(String.valueOf(request.getParameters().get("rag_context")).contains("Inline fact."));
        assertEquals(2, ((List<?>) request.getMetadata().get("rag_sources")).size());
    }

    @Test
    void loadRagContextRanksFileChunksByQuery() throws Exception {
        Path context = tempDir.resolve("docs.txt");
        Files.writeString(context, """
                Billing setup uses invoices, accounts, and payment reconciliation.

                Install profiles choose cpu, metal, cuda, or rocm. The metal profile is best for Apple Silicon local inference.

                Website deployment uses GitHub Pages and static documentation links.
                """);

        var rag = CliInferenceFeatures.loadRagContext(
                List.of(),
                List.of(context.toString()),
                400,
                "Which install profile should I use for Apple Silicon metal inference?");

        assertEquals("query_chunked", rag.strategy());
        assertFalse(rag.sources().isEmpty());
        int installIndex = rag.content().indexOf("Install profiles");
        int billingIndex = rag.content().indexOf("Billing setup");
        assertTrue(installIndex >= 0);
        assertTrue(billingIndex < 0 || installIndex < billingIndex);
    }

    @Test
    void loadsDirectAqliToolDefinitionMetadata() throws Exception {
        Path tools = tempDir.resolve("aqli-tool.json");
        Files.writeString(tools, """
                {
                  "name": "read_file",
                  "type": "mcp_tool",
                  "description": "Read a file through MCP",
                  "parameters": { "type": "object" },
                  "metadata": { "mcp_server": "filesystem" }
                }
                """);

        List<ToolDefinition> definitions = CliInferenceFeatures.loadTools(List.of(tools.toString()), List.of());

        assertEquals(1, definitions.size());
        assertEquals(ToolDefinition.Type.MCP_TOOL, definitions.get(0).getType());
        assertEquals("filesystem", definitions.get(0).getMetadata().get("mcp_server"));
    }
}
