package tech.kayys.aqli.server.api.v1;

final class AgentOpenApiExamples {

    static final String CHAT_REQUEST = """
            {
              "model": "demo-model",
              "request_id": "req_agent_123",
              "trace_id": "trace_agent_123",
              "messages": [
                {"role": "system", "content": "Answer using supplied context and tools when relevant."},
                {"role": "user", "content": "What changed for agentic support?"}
              ],
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "search_context",
                    "description": "Search external knowledge",
                    "parameters": {
                      "type": "object",
                      "properties": {"query": {"type": "string"}},
                      "required": ["query"]
                    }
                  },
                  "x_aqli": {"mcp_server": "knowledge"}
                }
              ],
              "tool_choice": "auto",
              "rag_context": [
                {
                  "id": "chunk-1",
                  "source": "docs/agentic",
                  "score": 0.92,
                  "text": "Aqli exposes inference APIs for agent orchestrators."
                }
              ]
            }
            """;

    static final String CHAT_STREAM_REQUEST = """
            {
              "model": "demo-model",
              "request_id": "req_agent_stream_123",
              "trace_id": "trace_agent_stream_123",
              "stream": true,
              "stream_options": {
                "include_usage": true,
                "include_trace": true,
                "include_stream_metadata": true
              },
              "messages": [
                {"role": "system", "content": "Answer using supplied context when relevant."},
                {"role": "user", "content": "Stream the agent-facing answer."}
              ],
              "rag_context": [
                {
                  "source": "docs/agentic",
                  "text": "Aqli streams inference events and leaves orchestration to callers."
                }
              ]
            }
            """;

    static final String CHAT_RESPONSE = """
            {
              "id": "chatcmpl-request-id",
              "object": "chat.completion",
              "created": 1760000000,
              "model": "demo-model",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Aqli provides serving APIs for agent projects."},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
              "metadata": {
                "aqli_trace": {
                  "request_id": "req_agent_123",
                  "trace_id": "trace_agent_123"
                }
              }
            }
            """;

    static final String CHAT_STREAM = """
            data:{"id":"chatcmpl-request-id","object":"chat.completion.chunk","choices":[{"delta":{"role":"assistant","content":"Hello"}}],"metadata":{"aqli_trace":{"request_id":"req_agent_stream_123","trace_id":"trace_agent_stream_123"},"aqli_stream":{"surface":"chat.completions","sequence_number":0,"final":false,"include_usage":true}}}

            data:{"id":"chatcmpl-request-id","object":"chat.completion.chunk","choices":[{"delta":{},"finish_reason":"stop"}],"metadata":{"aqli_stream":{"surface":"chat.completions","sequence_number":1,"final":true,"include_usage":true,"finish_reason":"stop"}},"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}

            data:[DONE]
            """;

    static final String RESPONSES_REQUEST = """
            {
              "model": "demo-model",
              "instructions": "Use the provided context when relevant.",
              "input": "Summarize Aqli's agent integration boundary.",
              "retrieval_context": {
                "documents": [
                  {
                    "content": "Aqli owns serving, MCP discovery, embeddings, and RAG context injection.",
                    "metadata": {"source": "docs/api"}
                  }
                ]
              },
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "lookup",
                    "parameters": {"type": "object"}
                  }
                }
              ]
            }
            """;

    static final String RESPONSES_STREAM_REQUEST = """
            {
              "model": "demo-model",
              "request_id": "req_response_stream_123",
              "trace_id": "trace_response_stream_123",
              "stream": true,
              "stream_options": {
                "include_usage": true,
                "include_trace": true,
                "include_stream_metadata": true
              },
              "instructions": "Use the provided context when relevant.",
              "input": "Stream Aqli's agent integration boundary.",
              "retrieval_context": [
                {
                  "source": "docs/api",
                  "text": "Aqli owns serving, MCP discovery, embeddings, and RAG context injection."
                }
              ]
            }
            """;

    static final String RESPONSES_RESPONSE = """
            {
              "id": "resp_request-id",
              "object": "response",
              "status": "completed",
              "model": "demo-model",
              "output": [
                {
                  "id": "msg_request-id",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "output_text", "text": "Aqli is the serving boundary."}]
                }
              ],
              "output_text": "Aqli is the serving boundary."
            }
            """;

    static final String RESPONSES_STREAM = """
            data:{"type":"response.created","sequence_number":0,"response":{"id":"resp_request-id","status":"in_progress","metadata":{"aqli_trace":{"request_id":"req_response_stream_123","trace_id":"trace_response_stream_123"},"aqli_stream":{"surface":"responses","sequence_number":0,"final":false,"include_usage":true}}}}

            data:{"type":"response.output_text.delta","sequence_number":1,"delta":"Hello","metadata":{"aqli_stream":{"surface":"responses","sequence_number":1,"final":false,"include_usage":true}}}

            data:{"type":"response.completed","sequence_number":3,"response":{"id":"resp_request-id","status":"completed","output_text":"Hello","metadata":{"aqli_stream":{"surface":"responses","sequence_number":3,"final":true,"include_usage":true,"finish_reason":"stop"}},"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}}

            data:[DONE]
            """;

    static final String EMBEDDINGS_REQUEST = """
            {
              "model": "demo-embed",
              "request_id": "req_embed_123",
              "trace_id": "trace_embed_123",
              "input": ["Aqli serves embeddings for RAG pipelines."],
              "metadata": {"tenant": "agent-project"}
            }
            """;

    static final String EMBEDDINGS_RESPONSE = """
            {
              "object": "list",
              "model": "demo-embed",
              "data": [
                {"object": "embedding", "index": 0, "embedding": [0.0, 0.0, 0.0]}
              ],
              "metadata": {
                "aqli_trace": {
                  "request_id": "req_embed_123",
                  "trace_id": "trace_embed_123"
                }
              }
            }
            """;

    static final String ERROR_RESPONSE = """
            {
              "error": {
                "message": "model is required",
                "type": "invalid_request_error",
                "request_id": "req_agent_123",
                "trace_id": "trace_agent_123"
              }
            }
            """;

    static final String VALIDATION_REQUEST = """
            {
              "model": "demo-model",
              "request_id": "req_validate_123",
              "trace_id": "trace_validate_123",
              "messages": [
                {"role": "system", "content": "Use supplied context."},
                {"role": "user", "content": "Validate this agent request."}
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
                {"source": "docs/agentic", "text": "Validation does not invoke the model."}
              ]
            }
            """;

    static final String VALIDATION_RESPONSE = """
            {
              "object": "aqli.agent_validation",
              "surface": "chat",
              "valid": true,
              "model_invoked": false,
              "trace": {
                "request_id": "req_validate_123",
                "trace_id": "trace_validate_123"
              },
              "normalized": {
                "request_id": "req_validate_123",
                "trace_id": "trace_validate_123",
                "model": "demo-model",
                "message_count": 3,
                "tool_count": 1,
                "tool_contract": {"valid": true, "tool_count": 1, "warning_count": 0},
                "rag": {"injected": true, "items": 1}
              },
              "boundary": {
                "validation_only": true,
                "tool_execution": false,
                "retrieval_execution": false
              }
            }
            """;

    static final String TOOL_VALIDATION_REQUEST = """
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
                }
              ]
            }
            """;

    static final String TOOL_VALIDATION_RESPONSE = """
            {
              "object": "aqli.tool_contract_validation",
              "valid": true,
              "model_invoked": false,
              "tool_count": 1,
              "normalized": [
                {
                  "name": "lookup_context",
                  "type": "function",
                  "strict": true,
                  "parameter_keys": ["properties", "required", "type"],
                  "metadata": {"mcp_server": "knowledge"}
                }
              ],
              "warnings": [
                {"code": "schema_feature_may_be_ignored"}
              ],
              "boundary": {
                "validation_only": true,
                "tool_execution": false,
                "tool_authorization": false
              }
            }
            """;

    static final String PREFLIGHT_REQUEST = """
            {
              "model": "demo-model",
              "surface": "chat",
              "feature_profile": "chat_agent",
              "required_features": ["rag_context", "mcp_tool_discovery"],
              "request": {
                "request_id": "req_preflight_123",
                "trace_id": "trace_preflight_123",
                "messages": [
                  {"role": "system", "content": "Use supplied context."},
                  {"role": "user", "content": "Can this route support an agent?"}
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
                  {"source": "docs/agentic", "text": "Aqli injects RAG context but does not retrieve it."}
                ]
              }
            }
            """;

    static final String PREFLIGHT_RESPONSE = """
            {
              "object": "aqli.agent_preflight",
              "contract_version": "v1",
              "supported_contract_versions": ["v1"],
              "feature_negotiation": {
                "mode": "feature_flags",
                "feature_namespace": "aqli.agent.compatibility",
                "contract_version": "v1",
                "default_feature_profile": "agent_serving",
                "supported_feature_profiles": ["agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"],
                "required_features": [
                  "openai_chat_completions",
                  "openai_responses",
                  "openai_embeddings",
                  "agent_contract",
                  "agent_preflight",
                  "mcp_tool_discovery",
                  "rag_context"
                ]
              },
              "status": "ready",
              "ready": true,
              "surface": "chat",
              "model": "demo-model",
              "feature_profile": "chat_agent",
              "trace": {
                "request_id": "req_preflight_123",
                "trace_id": "trace_preflight_123",
                "metadata": {}
              },
              "boundary": {
                "validation_only": true,
                "model_invoked": false,
                "tool_execution": false,
                "retrieval_execution": false,
                "tool_authorization": false
              },
              "checks": {
                "discover_mcp_tools": true,
                "mcp_discovery_required": true,
                "validate_tools": true,
                "tool_validation_required": true,
                "validate_request": true,
                "request_validation_required": true,
                "openai_tool_compatibility": true,
                "enabled_only": true,
                "feature_profile": "chat_agent",
                "required_contract_version": "v1",
                "required_features": ["rag_context", "mcp_tool_discovery"],
                "optional_features": []
              },
              "check_results": {
                "capabilities": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                },
                "contract": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                },
                "feature_negotiation": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {},
                  "details": {
                    "feature_profile": "chat_agent",
                    "supported_feature_profiles": ["agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"],
                    "feature_profile_supported": true,
                    "required_contract_version": "v1",
                    "supported_contract_versions": ["v1"],
                    "required_features": ["rag_context", "mcp_tool_discovery"],
                    "optional_features": [],
                    "unsupported_required_features": [],
                    "unsupported_optional_features": []
                  }
                },
                "model_route": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                },
                "mcp_discovery": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                },
                "tool_validation": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                },
                "request_validation": {
                  "status": "ready",
                  "ready": true,
                  "requested": true,
                  "blocking_messages": [],
                  "warning_messages": [],
                  "blocking_codes": [],
                  "warning_codes": [],
                  "issue_hints": [],
                  "remediation_plan": [],
                  "blocking_remediation_plan": [],
                  "warning_remediation_plan": [],
                  "remediation_plan_by_code": {}
                }
              },
              "ready_check_count": 7,
              "blocked_check_count": 0,
              "skipped_check_count": 0,
              "checked_areas": [
                "capabilities",
                "contract",
                "feature_negotiation",
                "model_route",
                "mcp_discovery",
                "tool_validation",
                "request_validation"
              ],
              "readiness_report": {
                "object": "aqli.agent_readiness_report",
                "contract_version": "v1",
                "supported_contract_versions": ["v1"],
                "feature_negotiation": {
                  "mode": "feature_flags",
                  "feature_namespace": "aqli.agent.compatibility",
                  "contract_version": "v1",
                  "default_feature_profile": "agent_serving",
                  "supported_feature_profiles": ["agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"],
                  "required_features": [
                    "openai_chat_completions",
                    "openai_responses",
                    "openai_embeddings",
                    "agent_contract",
                    "agent_preflight",
                    "mcp_tool_discovery",
                    "rag_context"
                  ]
                },
                "status": "ready",
                "ready": true,
                "surface": "chat",
                "model": "demo-model",
                "feature_profile": "chat_agent",
                "trace": {
                  "request_id": "req_preflight_123",
                  "trace_id": "trace_preflight_123",
                  "metadata": {}
                },
                "boundary": {
                  "validation_only": true,
                  "model_invoked": false,
                  "tool_execution": false,
                  "retrieval_execution": false,
                  "tool_authorization": false
                },
                "checks": {
                  "capabilities": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  },
                  "contract": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  },
                  "feature_negotiation": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {},
                    "details": {
                      "feature_profile": "chat_agent",
                      "supported_feature_profiles": ["agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"],
                      "feature_profile_supported": true,
                      "required_contract_version": "v1",
                      "supported_contract_versions": ["v1"],
                      "required_features": ["rag_context", "mcp_tool_discovery"],
                      "optional_features": [],
                      "unsupported_required_features": [],
                      "unsupported_optional_features": []
                    }
                  },
                  "model_route": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  },
                  "mcp_discovery": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  },
                  "tool_validation": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  },
                  "request_validation": {
                    "status": "ready",
                    "ready": true,
                    "requested": true,
                    "blocking_messages": [],
                    "warning_messages": [],
                    "blocking_codes": [],
                    "warning_codes": [],
                    "issue_hints": [],
                    "remediation_plan": [],
                    "blocking_remediation_plan": [],
                    "warning_remediation_plan": [],
                    "remediation_plan_by_code": {}
                  }
                },
                "checked_areas": [
                  "capabilities",
                  "contract",
                  "feature_negotiation",
                  "model_route",
                  "mcp_discovery",
                  "tool_validation",
                  "request_validation"
                ],
                "blocking_issue_count": 0,
                "warning_count": 0,
                "ready_check_count": 7,
                "blocked_check_count": 0,
                "skipped_check_count": 0,
                "issues": [],
                "issue_hints": [],
                "issue_hints_by_area": {},
                "issue_hints_by_code": {},
                "remediation_plan": [],
                "blocking_remediation_plan": [],
                "warning_remediation_plan": [],
                "remediation_plan_by_area": {},
                "remediation_plan_by_code": {},
                "issues_by_area": {},
                "issue_codes_by_area": {},
                "blocking_messages": [],
                "warning_messages": [],
                "blocking_codes": [],
                "warning_codes": []
              },
              "blocking_issue_count": 0,
              "warning_count": 0,
              "issues": [],
              "issue_hints": [],
              "issue_hints_by_area": {},
              "issue_hints_by_code": {},
              "remediation_plan": [],
              "blocking_remediation_plan": [],
              "warning_remediation_plan": [],
              "remediation_plan_by_area": {},
              "remediation_plan_by_code": {},
              "issues_by_area": {},
              "issue_codes_by_area": {},
              "blocking_messages": [],
              "warning_messages": [],
              "blocking_codes": [],
              "warning_codes": []
            }
            """;

    static final String AGENT_CONTRACT_RESPONSE = """
            {
              "object": "aqli.agent_contract",
              "version": "v1",
              "contract_version": "v1",
              "supported_contract_versions": ["v1"],
              "service_role": "inference_serving_engine",
              "compatibility": [
                "openai_chat_completions",
                "openai_responses",
                "openai_embeddings",
                "agent_contract",
                "agent_feature_negotiation",
                "agent_preflight",
                "mcp_tool_discovery",
                "rag_context"
              ],
              "feature_negotiation": {
                "mode": "feature_flags",
                "feature_namespace": "aqli.agent.compatibility",
                "contract_version": "v1",
                "supported_contract_versions": ["v1"],
                "default_feature_profile": "agent_serving",
                "supported_feature_profiles": ["agent_serving", "chat_agent", "responses_agent", "embedding_rag", "mcp_tools"],
                "required_features": [
                  "openai_chat_completions",
                  "openai_responses",
                  "openai_embeddings",
                  "agent_contract",
                  "agent_feature_negotiation",
                  "agent_preflight",
                  "mcp_tool_discovery",
                  "rag_context"
                ],
                "unknown_feature_policy": "ignore_for_forward_compatibility"
              },
              "boundary": {
                "tool_execution": false,
                "retrieval_execution": false
              },
              "endpoints": {
                "chat_completions": {"method": "POST", "path": "/v1/chat/completions"},
                "responses": {"method": "POST", "path": "/v1/responses"}
              },
              "readiness_issue_catalog": [
                {
                  "code": "TOOL_DEFINITIONS_INVALID",
                  "area": "tool_validation",
                  "default_severity": "blocking",
                  "summary": "Tool definitions are invalid.",
                  "remediation": "Fix the OpenAI-compatible tool schema before serving the route."
                }
              ],
              "readiness_issue_catalog_by_code": {
                "TOOL_DEFINITIONS_INVALID": {
                  "code": "TOOL_DEFINITIONS_INVALID",
                  "area": "tool_validation",
                  "default_severity": "blocking",
                  "summary": "Tool definitions are invalid.",
                  "remediation": "Fix the OpenAI-compatible tool schema before serving the route."
                }
              }
            }
            """;

    static final String READINESS_ISSUE_CATALOG_RESPONSE = """
            {
              "object": "aqli.agent_readiness_issue_catalog",
              "version": "v1",
              "service_role": "inference_serving_engine",
              "boundary": {
                "validation_only": true,
                "model_invoked": false,
                "tool_execution": false,
                "retrieval_execution": false,
                "tool_authorization": false
              },
              "count": 1,
              "items": [
                {
                  "code": "TOOL_DEFINITIONS_INVALID",
                  "area": "tool_validation",
                  "default_severity": "blocking",
                  "summary": "Tool definitions are invalid.",
                  "remediation": "Fix the OpenAI-compatible tool schema before serving the route."
                }
              ],
              "by_code": {
                "TOOL_DEFINITIONS_INVALID": {
                  "code": "TOOL_DEFINITIONS_INVALID",
                  "area": "tool_validation",
                  "default_severity": "blocking",
                  "summary": "Tool definitions are invalid.",
                  "remediation": "Fix the OpenAI-compatible tool schema before serving the route."
                }
              },
              "by_area": {
                "tool_validation": [
                  {
                    "code": "TOOL_DEFINITIONS_INVALID",
                    "area": "tool_validation",
                    "default_severity": "blocking",
                    "summary": "Tool definitions are invalid.",
                    "remediation": "Fix the OpenAI-compatible tool schema before serving the route."
                  }
                ]
              }
            }
            """;

    static final String MCP_SERVERS_RESPONSE = """
            {
              "available": true,
              "registry_path": "/home/user/.aqli/mcp.json",
              "servers": [
                {"name": "knowledge", "enabled": true}
              ],
              "boundary": {"role": "discovery_only", "tool_execution": false}
            }
            """;

    static final String MCP_TOOLS_RESPONSE = """
            {
              "available": true,
              "compat": "openai",
              "enabled_only": true,
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "mcp_knowledge_search",
                    "description": "Search knowledge",
                    "parameters": {"type": "object"}
                  },
                  "x_aqli": {
                    "mcp_server": "knowledge",
                    "mcp_tool_name": "search",
                    "tool_execution": false
                  }
                }
              ]
            }
            """;

    private AgentOpenApiExamples() {
    }
}
