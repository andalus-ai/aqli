package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aqli.spi.tool.ToolCall;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenticApiMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsResponsesToolLoopItems() throws Exception {
        InferenceRequest request = AgenticApiMapper.toResponsesInferenceRequest(mapper.readTree("""
                {
                  "model": "demo-model",
                  "previous_response_id": "resp_previous",
                  "max_output_tokens": 32,
                  "input": [
                    {
                      "type": "message",
                      "role": "user",
                      "content": [
                        {"type": "input_text", "text": "Need lookup"}
                      ]
                    },
                    {
                      "type": "function_call",
                      "call_id": "call_lookup",
                      "name": "lookup",
                      "arguments": "{\\"query\\":\\"aqli\\"}"
                    },
                    {
                      "type": "function_call_output",
                      "call_id": "call_lookup",
                      "output": [
                        {"type": "input_text", "text": "Aqli is a serving engine."}
                      ]
                    },
                    {
                      "type": "message",
                      "role": "user",
                      "content": "Summarize it"
                    }
                  ]
                }
                """), "community");

        assertEquals("resp_previous", request.getSessionId().orElseThrow());
        assertEquals(32, ((Number) request.getParameters().get("max_tokens")).intValue());
        assertEquals(4, request.getMessages().size());

        assertEquals(Message.Role.USER, request.getMessages().get(0).getRole());
        assertEquals("Need lookup", request.getMessages().get(0).getContent());

        Message assistantToolCall = request.getMessages().get(1);
        assertEquals(Message.Role.ASSISTANT, assistantToolCall.getRole());
        assertEquals(1, assistantToolCall.getToolCalls().size());
        ToolCall toolCall = assistantToolCall.getToolCalls().get(0);
        assertEquals("call_lookup", toolCall.getId());
        assertEquals("lookup", toolCall.getFunction().getName());
        assertEquals("aqli", toolCall.getArguments().get("query"));

        assertEquals(Message.Role.TOOL, request.getMessages().get(2).getRole());
        assertEquals("call_lookup", request.getMessages().get(2).getToolCallId());
        assertEquals("Aqli is a serving engine.", request.getMessages().get(2).getContent());

        assertEquals(Message.Role.USER, request.getMessages().get(3).getRole());
        assertEquals("Summarize it", request.getMessages().get(3).getContent());
    }

    @Test
    void mapsChatContentArrayToText() throws Exception {
        InferenceRequest request = AgenticApiMapper.toInferenceRequest(mapper.readTree("""
                {
                  "model": "demo-model",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {"type": "text", "text": "Hello content array"}
                      ]
                    }
                  ]
                }
                """), "community");

        assertEquals(1, request.getMessages().size());
        assertEquals(Message.Role.USER, request.getMessages().get(0).getRole());
        assertEquals("Hello content array", request.getMessages().get(0).getContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void injectsStructuredRagContextAfterSystemMessages() throws Exception {
        InferenceRequest request = AgenticApiMapper.toInferenceRequest(mapper.readTree("""
                {
                  "model": "demo-model",
                  "messages": [
                    {"role": "system", "content": "Answer with grounded context."},
                    {"role": "user", "content": "What changed for agentic support?"}
                  ],
                  "rag_context": [
                    {
                      "id": "chunk-1",
                      "title": "Aqli API",
                      "source": "docs/api",
                      "score": 0.92,
                      "text": "Aqli exposes MCP tool discovery for agent orchestrators."
                    },
                    {
                      "chunk_id": "chunk-2",
                      "metadata": {"source": "docs/rag", "score": 0.87},
                      "content": "RAG retrieval remains owned by the calling agent."
                    }
                  ]
                }
                """), "community");

        assertEquals(3, request.getMessages().size());
        assertEquals(Message.Role.SYSTEM, request.getMessages().get(0).getRole());
        assertEquals("Answer with grounded context.", request.getMessages().get(0).getContent());

        Message context = request.getMessages().get(1);
        assertEquals(Message.Role.SYSTEM, context.getRole());
        assertTrue(context.getContent().contains("Retrieved context supplied by the caller."));
        assertTrue(context.getContent().contains("[1] Aqli API, source: docs/api, score: 0.92"));
        assertTrue(context.getContent().contains("Aqli exposes MCP tool discovery for agent orchestrators."));
        assertTrue(context.getContent().contains("[2] source: docs/rag, score: 0.87"));

        assertEquals(Message.Role.USER, request.getMessages().get(2).getRole());
        assertEquals("What changed for agentic support?", request.getMessages().get(2).getContent());

        List<Map<String, Object>> sources = (List<Map<String, Object>>) request.getParameters().get("rag_sources");
        assertEquals(2, sources.size());
        assertEquals("chunk-1", sources.get(0).get("id"));
        assertEquals("docs/api", sources.get(0).get("source"));
        assertEquals("chunk-2", sources.get(1).get("id"));
        assertEquals("docs/rag", sources.get(1).get("source"));
        assertEquals(true, request.getMetadata().get("rag_context_injected"));
        assertEquals(2, request.getMetadata().get("rag_context_items"));
    }

    @Test
    void mapsResponsesRetrievalContextAlias() throws Exception {
        InferenceRequest request = AgenticApiMapper.toResponsesInferenceRequest(mapper.readTree("""
                {
                  "model": "demo-model",
                  "instructions": "Use provided context when relevant.",
                  "retrieval_context": {
                    "documents": [
                      {
                        "content": "The serving API accepts retrieval context aliases.",
                        "metadata": {"source": "kb/agentic"}
                      }
                    ]
                  },
                  "input": "Summarize the integration point."
                }
                """), "community");

        assertEquals(3, request.getMessages().size());
        assertEquals(Message.Role.SYSTEM, request.getMessages().get(0).getRole());
        assertEquals(Message.Role.SYSTEM, request.getMessages().get(1).getRole());
        assertTrue(request.getMessages().get(1).getContent().contains("source: kb/agentic"));
        assertEquals(Message.Role.USER, request.getMessages().get(2).getRole());
        assertEquals("Summarize the integration point.", request.getMessages().get(2).getContent());

        assertTrue(request.getParameters().containsKey("rag_context"));
        assertEquals("retrieval_context", request.getMetadata().get("rag_context_alias"));
        assertEquals(true, request.getMetadata().get("rag_context_injected"));
    }

    @Test
    void emitsChatStreamUsageOnlyWhenRequested() throws Exception {
        AgentTraceContext trace = AgentTraceContext.fromPayload(mapper.readTree("""
                {
                  "request_id": "req-stream",
                  "trace_id": "trace-stream"
                }
                """));
        StreamingInferenceChunk finalChunk = StreamingInferenceChunk.finalTextChunk(
                "req-stream",
                1,
                "",
                new StreamingInferenceChunk.ChunkUsage(2, 3, 10));

        String defaultJson = AgenticApiMapper.toOpenAiChatStreamJson(finalChunk, "demo-model", trace);
        assertFalse(defaultJson.contains("\"usage\""));

        String usageJson = AgenticApiMapper.toOpenAiChatStreamJson(
                finalChunk,
                "demo-model",
                trace,
                new AgentStreamOptions(true, true, true));
        assertTrue(usageJson.contains("\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}"));
        assertTrue(usageJson.contains("\"aqli_trace\""));
        assertTrue(usageJson.contains("\"aqli_stream\""));
        assertTrue(usageJson.contains("\"include_usage\":true"));
    }

    @Test
    void emitsResponsesStreamUsageAndHonorsTraceOption() throws Exception {
        AgentTraceContext trace = AgentTraceContext.fromPayload(mapper.readTree("""
                {
                  "request_id": "req-response-stream",
                  "trace_id": "trace-response-stream"
                }
                """));
        StreamingInferenceChunk finalChunk = StreamingInferenceChunk.finalTextChunk(
                "req-response-stream",
                1,
                "",
                new StreamingInferenceChunk.ChunkUsage(4, 5, 20));

        List<String> events = AgenticApiMapper.toOpenAiResponseStreamJson(
                finalChunk,
                "demo-model",
                "done",
                trace,
                new AgentStreamOptions(true, false, true));

        String completed = events.get(events.size() - 1);
        assertTrue(completed.contains("\"type\":\"response.completed\""));
        assertTrue(completed.contains("\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":5,\"total_tokens\":9}"));
        assertTrue(completed.contains("\"aqli_stream\""));
        assertFalse(completed.contains("\"aqli_trace\""));
        assertFalse(completed.contains("\"trace\""));
    }
}
