package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolContractMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void validatesAndNormalizesToolDefinitions() throws Exception {
        Map<String, Object> body = AgentToolContractMapper.validatePayload(mapper.readTree("""
                {
                  "tools": [
                    {
                      "type": "mcp_tool",
                      "function": {
                        "name": "mcp_knowledge_search",
                        "strict": true,
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "query": {"type": "string", "oneOf": [{"minLength": 1}]}
                          },
                          "required": ["query"]
                        }
                      },
                      "x_aqli": {
                        "mcp_server": "knowledge",
                        "mcp_tool_name": "search"
                      }
                    }
                  ]
                }
                """), AgentTraceContext.fromPayload(mapper.readTree("{}")));

        assertEquals("aqli.tool_contract_validation", body.get("object"));
        assertEquals(true, body.get("valid"));
        assertEquals(false, body.get("model_invoked"));
        assertEquals(1, body.get("tool_count"));

        List<Map<String, Object>> normalized = (List<Map<String, Object>>) body.get("normalized");
        assertEquals("mcp_knowledge_search", normalized.get(0).get("name"));
        assertEquals("mcp_tool", normalized.get(0).get("type"));
        assertEquals(true, normalized.get(0).get("strict"));
        assertEquals("knowledge", ((Map<String, Object>) normalized.get(0).get("metadata")).get("mcp_server"));

        List<Map<String, Object>> warnings = (List<Map<String, Object>>) body.get("warnings");
        assertEquals("schema_feature_may_be_ignored", warnings.get(0).get("code"));
        assertTrue(warnings.get(0).get("path").toString().contains("oneOf"));
    }

    @Test
    void rejectsMalformedToolNames() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AgentToolContractMapper.toToolDefinitions(mapper.readTree("""
                        [
                          {
                            "type": "function",
                            "function": {
                              "name": "bad tool",
                              "parameters": {"type": "object"}
                            }
                          }
                        ]
                        """)));

        assertEquals("tools[0].function.name must match [A-Za-z0-9_-]{1,64}", error.getMessage());
    }

    @Test
    void supportsOpenAiBuiltInToolTypesWithoutFunctionName() throws Exception {
        List<ToolDefinition> tools = AgentToolContractMapper.toToolDefinitions(mapper.readTree("""
                [
                  {"type": "code_interpreter"},
                  {"type": "file_search"}
                ]
                """));

        assertEquals(2, tools.size());
        assertEquals("code_interpreter", tools.get(0).getName());
        assertEquals(ToolDefinition.Type.CODE_INTERPRETER, tools.get(0).getType());
        assertEquals("file_search", tools.get(1).getName());
        assertEquals(ToolDefinition.Type.FILE_SEARCH, tools.get(1).getType());
    }
}
