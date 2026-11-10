package tech.kayys.aqli.server;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
public class ServerApiTest {

    @Test
    public void testHealth() {
        RestAssured.given()
                .when().get("/health")
                .then().statusCode(200)
                .body("status", equalTo("ok"));
    }

    @Test
    public void testListModels() {
        RestAssured.given().header("X-API-Key", "community")
                .when().get("/v1/models")
                .then().statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void testAgentCapabilities() {
        RestAssured.given().header("Authorization", "Bearer community")
                .when().get("/v1/agent/capabilities")
                .then().statusCode(200)
                .body("service_role", equalTo("inference_serving_engine"))
                .body("compatibility", hasItem("openai_chat_completions"))
                .body("compatibility", hasItem("openai_chat_streaming"))
                .body("compatibility", hasItem("openai_responses"))
                .body("compatibility", hasItem("openai_responses_streaming"))
                .body("compatibility", hasItem("openai_models"))
                .body("compatibility", hasItem("model_capability_matrix"))
                .body("compatibility", hasItem("agent_contract"))
                .body("compatibility", hasItem("agent_preflight"))
                .body("compatibility", hasItem("agent_request_validation"))
                .body("compatibility", hasItem("agent_tool_contract_validation"))
                .body("compatibility", hasItem("request_trace_context"))
                .body("compatibility", hasItem("stream_options"))
                .body("compatibility", hasItem("stream_usage_reporting"))
                .body("compatibility", hasItem("stream_trace_metadata"))
                .body("compatibility", hasItem("mcp_registry"))
                .body("compatibility", hasItem("mcp_tool_discovery"))
                .body("compatibility", hasItem("mcp_tool_definitions"))
                .body("endpoints.openai_chat_completions", equalTo("/v1/chat/completions"))
                .body("endpoints.openai_chat_streaming", equalTo("/v1/chat/completions"))
                .body("endpoints.openai_responses", equalTo("/v1/responses"))
                .body("endpoints.openai_responses_streaming", equalTo("/v1/responses"))
                .body("endpoints.openai_models", equalTo("/v1/models?compat=openai"))
                .body("endpoints.model_capabilities", equalTo("/v1/models/{id}/capabilities"))
                .body("endpoints.agent_contract", equalTo("/v1/agent/contract"))
                .body("endpoints.agent_preflight", equalTo("/v1/agent/preflight"))
                .body("endpoints.agent_validation", equalTo("/v1/agent/validate"))
                .body("endpoints.agent_tool_validation", equalTo("/v1/agent/tools/validate"))
                .body("endpoints.mcp_servers", equalTo("/v1/mcp/servers"))
                .body("endpoints.mcp_tools", equalTo("/v1/mcp/tools"))
                .body("endpoints.mcp_server_tools", equalTo("/v1/mcp/servers/{name}/tools"))
                .body("auth.authorization_header", equalTo("Bearer token"))
                .body("traceability.request_id_header", equalTo("X-Aqli-Request-Id"))
                .body("traceability.metadata_key", equalTo("aqli_trace"));
    }

    @Test
    public void testAgentContract() {
        RestAssured.given().header("Authorization", "Bearer community")
                .when().get("/v1/agent/contract")
                .then().statusCode(200)
                .body("object", equalTo("aqli.agent_contract"))
                .body("version", equalTo("v1"))
                .body("service_role", equalTo("inference_serving_engine"))
                .body("boundary.tool_execution", equalTo(false))
                .body("boundary.retrieval_execution", equalTo(false))
                .body("traceability.propagates_to_inference_request", equalTo(true))
                .body("traceability.response_metadata_key", equalTo("aqli_trace"))
                .body("streaming.done_sentinel", equalTo("[DONE]"))
                .body("streaming.responses_events", hasItem("response.completed"))
                .body("streaming.stream_options.include_usage",
                        equalTo("Emit usage on final events when provider usage is available."))
                .body("boundary.agent_orchestrator_owns", hasItem("tool_execution"))
                .body("boundary.agent_orchestrator_owns", hasItem("vector_store_ownership"))
                .body("endpoints.chat_completions.path", equalTo("/v1/chat/completions"))
                .body("endpoints.responses.path", equalTo("/v1/responses"))
                .body("endpoints.mcp_tools.path", equalTo("/v1/mcp/tools?compat=openai"))
                .body("endpoints.agent_preflight.path", equalTo("/v1/agent/preflight"))
                .body("endpoints.agent_validation.path", equalTo("/v1/agent/validate?surface={surface}"))
                .body("endpoints.agent_tool_validation.path", equalTo("/v1/agent/tools/validate"))
                .body("schemas.chat_completions_request.required", hasItem("model"))
                .body("schemas.chat_completions_request.required", hasItem("messages"))
                .body("schemas.responses_request.required", hasItem("input"))
                .body("schemas.chat_completions_request.properties.stream_options.'$ref'",
                        equalTo("#/schemas/stream_options"))
                .body("schemas.responses_request.properties.stream_options.'$ref'",
                        equalTo("#/schemas/stream_options"))
                .body("schemas.stream_options.properties.include_usage.type", equalTo("boolean"))
                .body("schemas.agent_validation_response.required", hasItem("normalized"))
                .body("schemas.agent_validation_response.required", hasItem("trace"))
                .body("schemas.agent_preflight_response.required", hasItem("issues_by_area"))
                .body("schemas.agent_preflight_response.required", hasItem("boundary"))
                .body("schemas.agent_preflight_response.properties.check_results.additionalProperties.'$ref'",
                        equalTo("#/schemas/agent_preflight_check_result"))
                .body("schemas.agent_preflight_check_result.properties.details.properties.embedding.properties.rag.properties.vector_store_owned_by.type",
                        equalTo("string"))
                .body("schemas.tool_contract_validation_response.required", hasItem("warnings"))
                .body("schemas.openai_tool_definition.execution", equalTo(false))
                .body("schemas.openai_tool_definition.validation_endpoint", equalTo("/v1/agent/tools/validate"))
                .body("schemas.error_response.required", hasItem("error"))
                .body("schemas.rag_context.accepted_aliases", hasItem("retrieval_context"))
                .body("schemas.rag_context.retrieval_policy_owned_by", equalTo("agent_orchestrator"));
    }

    @Test
    public void testOpenApiIncludesAgentExamples() {
        RestAssured.given()
                .accept("application/yaml")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body(containsString("/v1/agent/contract"))
                .body(containsString("/v1/agent/preflight"))
                .body(containsString("agentic-chat"))
                .body(containsString("agentic-response"))
                .body(containsString("agent-preflight"))
                .body(containsString("openai-embedding"))
                .body(containsString("validate-chat"))
                .body(containsString("validate-tools"))
                .body(containsString("invalid-request"))
                .body(containsString("mcp-tools"));
    }

    @Test
    public void testAgentValidateChatRequest() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-validate-1")
                .header("X-Aqli-Trace-Id", "trace-validate-1")
                .header("X-Aqli-Session-Id", "session-validate-1")
                .header("X-Aqli-User-Id", "user-validate-1")
                .contentType("application/json")
                .queryParam("surface", "chat")
                .body("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {"role": "system", "content": "Use context."},
                            {"role": "user", "content": "Validate this."}
                          ],
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "lookup",
                                "parameters": {"type": "object"}
                              },
                              "x_aqli": {"mcp_server": "knowledge"}
                            }
                          ],
                          "rag_context": [
                            {"source": "docs/agentic", "text": "Validation is dry-run."}
                          ]
                        }
                        """)
                .when().post("/v1/agent/validate")
                .then().statusCode(200)
                .header("X-Aqli-Request-Id", equalTo("req-validate-1"))
                .header("X-Aqli-Trace-Id", equalTo("trace-validate-1"))
                .header("X-Aqli-Session-Id", equalTo("session-validate-1"))
                .header("X-Aqli-User-Id", equalTo("user-validate-1"))
                .body("object", equalTo("aqli.agent_validation"))
                .body("surface", equalTo("chat"))
                .body("valid", equalTo(true))
                .body("model_invoked", equalTo(false))
                .body("trace.request_id", equalTo("req-validate-1"))
                .body("trace.trace_id", equalTo("trace-validate-1"))
                .body("boundary.validation_only", equalTo(true))
                .body("boundary.tool_execution", equalTo(false))
                .body("normalized.request_id", equalTo("req-validate-1"))
                .body("normalized.trace_id", equalTo("trace-validate-1"))
                .body("normalized.session_id", equalTo("session-validate-1"))
                .body("normalized.user_id", equalTo("user-validate-1"))
                .body("normalized.model", equalTo("demo-model"))
                .body("normalized.message_count", equalTo(3))
                .body("normalized.messages[0].role", equalTo("system"))
                .body("normalized.messages[1].role", equalTo("system"))
                .body("normalized.messages[2].role", equalTo("user"))
                .body("normalized.tool_count", equalTo(1))
                .body("normalized.tools[0].metadata.mcp_server", equalTo("knowledge"))
                .body("normalized.tool_contract.valid", equalTo(true))
                .body("normalized.tool_contract.tool_count", equalTo(1))
                .body("normalized.rag.injected", equalTo(true))
                .body("normalized.rag.items", equalTo(1))
                .body("normalized.stream_options.include_usage", equalTo(false))
                .body("normalized.stream_options.include_trace", equalTo(true))
                .body("normalized.stream_options.include_stream_metadata", equalTo(true));
    }

    @Test
    public void testAgentPreflightReportsBlockedDryRunInDemoRuntime() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-preflight-1")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "surface": "chat",
                          "request": {
                            "messages": [
                              {"role": "system", "content": "Use context."},
                              {"role": "user", "content": "Preflight this route."}
                            ],
                            "tools": [
                              {
                                "type": "function",
                                "function": {
                                  "name": "lookup_context",
                                  "parameters": {"type": "object"}
                                }
                              }
                            ],
                            "rag_context": [
                              {"source": "docs/agentic", "text": "Validation is dry-run."}
                            ]
                          }
                        }
                        """)
                .when().post("/v1/agent/preflight")
                .then().statusCode(200)
                .header("X-Aqli-Request-Id", equalTo("req-preflight-1"))
                .body("object", equalTo("aqli.agent_preflight"))
                .body("status", equalTo("blocked"))
                .body("ready", equalTo(false))
                .body("surface", equalTo("chat"))
                .body("model", equalTo("demo-model"))
                .body("boundary.validation_only", equalTo(true))
                .body("boundary.model_invoked", equalTo(false))
                .body("boundary.tool_execution", equalTo(false))
                .body("boundary.retrieval_execution", equalTo(false))
                .body("model_capabilities.available", equalTo(false))
                .body("mcp_discovery.available", equalTo(false))
                .body("tool_validation.valid", equalTo(true))
                .body("tool_validation.model_invoked", equalTo(false))
                .body("request_validation.valid", equalTo(true))
                .body("request_validation.model_invoked", equalTo(false))
                .body("issues_by_area.model_route", hasItem("model is not available through Aqli serving"))
                .body("issues_by_area.mcp_discovery", hasItem("MCP discovery is not available"))
                .body("blocking_messages", hasItem("MCP discovery is not available"));
    }

    @Test
    public void testAgentValidateToolsContract() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-tools-1")
                .contentType("application/json")
                .body("""
                        {
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "lookup_context",
                                "strict": true,
                                "parameters": {
                                  "type": "object",
                                  "properties": {
                                    "query": {
                                      "type": "string",
                                      "anyOf": [{"minLength": 1}]
                                    }
                                  },
                                  "required": ["query"]
                                }
                              },
                              "x_aqli": {"mcp_server": "knowledge"}
                            },
                            {
                              "type": "mcp_tool",
                              "function": {
                                "name": "mcp_knowledge_search",
                                "parameters": {"type": "object"}
                              },
                              "x_aqli": {
                                "mcp_server": "knowledge",
                                "mcp_tool_name": "search"
                              }
                            }
                          ]
                        }
                        """)
                .when().post("/v1/agent/tools/validate")
                .then().statusCode(200)
                .header("X-Aqli-Request-Id", equalTo("req-tools-1"))
                .body("object", equalTo("aqli.tool_contract_validation"))
                .body("valid", equalTo(true))
                .body("model_invoked", equalTo(false))
                .body("tool_count", equalTo(2))
                .body("boundary.validation_only", equalTo(true))
                .body("boundary.tool_execution", equalTo(false))
                .body("boundary.tool_authorization", equalTo(false))
                .body("normalized[0].name", equalTo("lookup_context"))
                .body("normalized[0].type", equalTo("function"))
                .body("normalized[0].strict", equalTo(true))
                .body("normalized[0].metadata.mcp_server", equalTo("knowledge"))
                .body("normalized[1].type", equalTo("mcp_tool"))
                .body("normalized[1].metadata.mcp_tool_name", equalTo("search"))
                .body("warnings.code", hasItem("schema_feature_may_be_ignored"));
    }

    @Test
    public void testAgentValidateToolsContractRejectsMalformedSchema() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "bad tool",
                                "parameters": {"type": "object"}
                              }
                            }
                          ]
                        }
                        """)
                .when().post("/v1/agent/tools/validate")
                .then().statusCode(400)
                .body("error.message", equalTo("tools[0].function.name must match [A-Za-z0-9_-]{1,64}"))
                .body("error.type", equalTo("invalid_request_error"));
    }

    @Test
    public void testAgentValidateEmbeddingsRequest() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .queryParam("surface", "embeddings")
                .body("""
                        {
                          "model": "demo-embed",
                          "input": ["alpha", "beta"],
                          "metadata": {"tenant": "agent-project"}
                        }
                        """)
                .when().post("/v1/agent/validate")
                .then().statusCode(200)
                .body("surface", equalTo("embeddings"))
                .body("valid", equalTo(true))
                .body("model_invoked", equalTo(false))
                .body("normalized.model", equalTo("demo-embed"))
                .body("normalized.input_count", equalTo(2))
                .body("normalized.encoding_format", equalTo("float"))
                .body("normalized.metadata.tenant", equalTo("agent-project"))
                .body("normalized.rag.embedding_generation", equalTo(true))
                .body("normalized.rag.retrieval_execution", equalTo(false))
                .body("normalized.rag.vector_store_owned_by", equalTo("agent_orchestrator"));
    }

    @Test
    public void testAgentValidateInvalidSurfaceErrorShape() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .queryParam("surface", "planner")
                .body("""
                        {
                          "model": "demo-model",
                          "messages": []
                        }
                        """)
                .when().post("/v1/agent/validate")
                .then().statusCode(400)
                .body("error.message", equalTo("surface must be one of chat, responses, or embeddings"))
                .body("error.type", equalTo("invalid_request_error"));
    }

    @Test
    public void testOpenAiChatInvalidRequestErrorShape() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-error-1")
                .header("X-Aqli-Trace-Id", "trace-error-1")
                .contentType("application/json")
                .body("""
                        {
                          "messages": []
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(400)
                .header("X-Aqli-Request-Id", equalTo("req-error-1"))
                .header("X-Aqli-Trace-Id", equalTo("trace-error-1"))
                .body("error.message", equalTo("model is required"))
                .body("error.type", equalTo("invalid_request_error"))
                .body("error.request_id", equalTo("req-error-1"))
                .body("error.trace_id", equalTo("trace-error-1"));
    }

    @Test
    public void testOpenAiEmbeddingsInvalidRequestErrorShape() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-embed"
                        }
                        """)
                .when().post("/v1/embeddings")
                .then().statusCode(400)
                .body("error.message", equalTo("input or inputs is required"))
                .body("error.type", equalTo("invalid_request_error"));
    }

    @Test
    public void testOpenAiChatRejectsInvalidToolBeforeInference() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {"role": "user", "content": "This should not invoke the model."}
                          ],
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "lookup",
                                "parameters": {"required": "query"}
                              }
                            }
                          ]
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(400)
                .body("error.message",
                        equalTo("tools[0].function.parameters.required must be an array of strings"))
                .body("error.type", equalTo("invalid_request_error"));
    }

    @Test
    public void testMcpServersDiscoveryFallback() {
        RestAssured.given().header("Authorization", "Bearer community")
                .when().get("/v1/mcp/servers")
                .then().statusCode(200)
                .body("available", equalTo(false))
                .body("servers", hasSize(0))
                .body("boundary.role", equalTo("discovery_only"))
                .body("boundary.aqli_exposes", hasItem("registered_servers"))
                .body("boundary.agent_orchestrator_owns", hasItem("tool_execution"))
                .body("boundary.tool_execution", equalTo(false));
    }

    @Test
    public void testMcpToolsDiscoveryFallback() {
        RestAssured.given().header("Authorization", "Bearer community")
                .queryParam("compat", "openai")
                .when().get("/v1/mcp/tools")
                .then().statusCode(200)
                .body("available", equalTo(false))
                .body("compat", equalTo("openai"))
                .body("enabled_only", equalTo(true))
                .body("tools", hasSize(0))
                .body("boundary.role", equalTo("discovery_only"))
                .body("boundary.aqli_exposes", hasItem("tool_schemas"))
                .body("boundary.agent_orchestrator_owns", hasItem("tool_authorization"))
                .body("boundary.tool_execution", equalTo(false));
    }

    @Test
    public void testOpenAiModelsShape() {
        RestAssured.given().header("Authorization", "Bearer community")
                .queryParam("compat", "openai")
                .when().get("/v1/models")
                .then().statusCode(200)
                .body("object", equalTo("list"))
                .body("data", hasSize(0));
    }

    @Test
    public void testModelCapabilitiesMatrix() {
        RestAssured.given().header("Authorization", "Bearer community")
                .when().get("/v1/models/demo-model/capabilities")
                .then().statusCode(200)
                .body("model_id", equalTo("demo-model"))
                .body("known", equalTo(false))
                .body("available", equalTo(false))
                .body("api_contract.responses", equalTo(true))
                .body("api_contract.responses_streaming", equalTo(true))
                .body("api_contract.tools_request_schema", equalTo(true))
                .body("openai_compatibility.chat_completions", equalTo(true))
                .body("tooling.tool_execution", equalTo(false))
                .body("rag.context_injection", equalTo(true));
    }

    @Test
    public void testOpenAiChatCompletions() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {"role": "system", "content": "You are Aqli, a serving engine."},
                            {"role": "user", "content": "Hello agent"}
                          ],
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "lookup",
                                "description": "Lookup a value",
                                "parameters": {"type": "object"}
                              },
                              "x_aqli": {"mcp_server": "demo"}
                            }
                          ],
                          "tool_choice": "auto"
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(200)
                .body("object", equalTo("chat.completion"))
                .body("model", equalTo("demo-model"))
                .body("choices", hasSize(1))
                .body("choices[0].message.role", equalTo("assistant"))
                .body("choices[0].message.content", equalTo("[demo] echo: Hello agent"));
    }

    @Test
    public void testOpenAiChatTraceContext() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-chat-1")
                .header("X-Aqli-Trace-Id", "trace-chat-1")
                .header("X-Aqli-Session-Id", "session-chat-1")
                .header("X-Aqli-User-Id", "user-chat-1")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "messages": [
                            {"role": "user", "content": "Trace me"}
                          ]
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(200)
                .header("X-Aqli-Request-Id", equalTo("req-chat-1"))
                .header("X-Aqli-Trace-Id", equalTo("trace-chat-1"))
                .header("X-Aqli-Session-Id", equalTo("session-chat-1"))
                .header("X-Aqli-User-Id", equalTo("user-chat-1"))
                .body("id", equalTo("chatcmpl-req-chat-1"))
                .body("metadata.aqli_trace.request_id", equalTo("req-chat-1"))
                .body("metadata.aqli_trace.trace_id", equalTo("trace-chat-1"))
                .body("metadata.aqli_trace.session_id", equalTo("session-chat-1"))
                .body("metadata.aqli_trace.user_id", equalTo("user-chat-1"));
    }

    @Test
    public void testOpenAiChatCompletionsStream() {
        RestAssured.given().header("Authorization", "Bearer community")
                .accept("text/event-stream")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "stream": true,
                          "stream_options": {
                            "include_usage": true
                          },
                          "messages": [
                            {"role": "user", "content": "Stream agent"}
                          ]
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(200)
                .body(containsString("data:{"))
                .body(containsString("\"object\":\"chat.completion.chunk\""))
                .body(containsString("\"role\":\"assistant\""))
                .body(containsString("[demo] echo: Stream agent"))
                .body(containsString("\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}"))
                .body(containsString("\"aqli_stream\""))
                .body(containsString("\"surface\":\"chat.completions\""))
                .body(containsString("\"include_usage\":true"))
                .body(containsString("data:[DONE]"));
    }

    @Test
    public void testOpenAiChatCompletionsStreamRequiresSseAccept() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "stream": true,
                          "messages": [
                            {"role": "user", "content": "Stream agent"}
                          ]
                        }
                        """)
                .when().post("/v1/chat/completions")
                .then().statusCode(400)
                .body("error.type", equalTo("unsupported_streaming_accept"));
    }

    @Test
    public void testOpenAiResponses() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "instructions": "You are Aqli, a serving engine.",
                          "input": "Hello responses",
                          "tools": [
                            {
                              "type": "function",
                              "function": {
                                "name": "lookup",
                                "description": "Lookup a value",
                                "parameters": {"type": "object"}
                              }
                            }
                          ],
                          "tool_choice": "auto"
                        }
                        """)
                .when().post("/v1/responses")
                .then().statusCode(200)
                .body("object", equalTo("response"))
                .body("status", equalTo("completed"))
                .body("model", equalTo("demo-model"))
                .body("output_text", equalTo("[demo] echo: Hello responses"))
                .body("output[0].type", equalTo("message"))
                .body("output[0].role", equalTo("assistant"))
                .body("output[0].content[0].type", equalTo("output_text"))
                .body("output[0].content[0].text", equalTo("[demo] echo: Hello responses"));
    }

    @Test
    public void testOpenAiResponsesStreamingUnsupported() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "stream": true,
                          "input": "Hello responses"
                        }
                        """)
                .when().post("/v1/responses")
                .then().statusCode(400)
                .body("error.type", equalTo("unsupported_streaming_accept"));
    }

    @Test
    public void testOpenAiResponsesStream() {
        RestAssured.given().header("Authorization", "Bearer community")
                .accept("text/event-stream")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "stream": true,
                          "stream_options": {
                            "include_usage": true
                          },
                          "input": "Stream responses"
                        }
                        """)
                .when().post("/v1/responses")
                .then().statusCode(200)
                .body(containsString("data:{"))
                .body(containsString("\"type\":\"response.created\""))
                .body(containsString("\"type\":\"response.output_text.delta\""))
                .body(containsString("\"delta\":\"[demo] echo: Stream responses\""))
                .body(containsString("\"type\":\"response.output_text.done\""))
                .body(containsString("\"type\":\"response.completed\""))
                .body(containsString("\"output_text\":\"[demo] echo: Stream responses\""))
                .body(containsString("\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}"))
                .body(containsString("\"aqli_stream\""))
                .body(containsString("\"surface\":\"responses\""))
                .body(containsString("\"include_usage\":true"))
                .body(containsString("data:[DONE]"));
    }

    @Test
    public void testOpenAiResponsesStructuredInput() {
        RestAssured.given().header("Authorization", "Bearer community")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-model",
                          "input": [
                            {"role": "developer", "content": "Keep the service boundary clear."},
                            {
                              "role": "user",
                              "content": [
                                {"type": "input_text", "text": "Hello structured responses"}
                              ]
                            }
                          ],
                          "max_output_tokens": 16
                        }
                        """)
                .when().post("/v1/responses")
                .then().statusCode(200)
                .body("object", equalTo("response"))
                .body("output_text", equalTo("[demo] echo: Hello structured responses"));
    }

    @Test
    public void testOpenAiEmbeddingsShape() {
        RestAssured.given().header("Authorization", "Bearer community")
                .header("X-Aqli-Request-Id", "req-embed-1")
                .header("X-Aqli-Trace-Id", "trace-embed-1")
                .contentType("application/json")
                .body("""
                        {
                          "model": "demo-embed",
                          "input": ["hello"]
                        }
                        """)
                .when().post("/v1/embeddings")
                .then().statusCode(200)
                .header("X-Aqli-Request-Id", equalTo("req-embed-1"))
                .header("X-Aqli-Trace-Id", equalTo("trace-embed-1"))
                .body("object", equalTo("list"))
                .body("model", equalTo("demo-embed"))
                .body("data", hasSize(1))
                .body("data[0].object", equalTo("embedding"))
                .body("data[0].index", equalTo(0))
                .body("metadata.aqli_trace.request_id", equalTo("req-embed-1"))
                .body("metadata.aqli_trace.trace_id", equalTo("trace-embed-1"));
    }
}
