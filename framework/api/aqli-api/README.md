# Aqli API Server

Aqli API is the serving boundary for the Aqli inference engine. It should
stay focused on model serving, provider routing, system prompts, tool-call
schemas, embeddings, and RAG context injection. Agent frameworks can plug into
it for inference while keeping planning, memory policy, authorization, and tool
execution loops in their own orchestrator.

## Agentic AI integration

The API supports both native Aqli endpoints and OpenAI-compatible endpoints
so agent projects can integrate without becoming tightly coupled to Aqli.

| Capability | Endpoint |
| --- | --- |
| Agent capability discovery | `GET /v1/agent/capabilities` |
| Agent integration contract | `GET /v1/agent/contract` |
| Agent request validation | `POST /v1/agent/validate?surface=chat` |
| Agent tool contract validation | `POST /v1/agent/tools/validate` |
| OpenAI-compatible chat completions | `POST /v1/chat/completions` |
| OpenAI-compatible chat streaming | `POST /v1/chat/completions` with `stream: true` |
| OpenAI-compatible responses | `POST /v1/responses` |
| OpenAI-compatible responses streaming | `POST /v1/responses` with `stream: true` |
| OpenAI-compatible embeddings | `POST /v1/embeddings` with `input` |
| OpenAI-compatible models | `GET /v1/models?compat=openai` |
| Model capability matrix | `GET /v1/models/{id}/capabilities` |
| MCP server discovery | `GET /v1/mcp/servers` |
| MCP tool discovery | `GET /v1/mcp/tools?compat=openai` |
| Native Aqli completions | `POST /v1/completions` |
| Native Aqli streaming | `POST /v1/completions/stream` |
| Native Aqli embeddings | `POST /v1/embeddings` with `inputs` |
| Models | `GET /v1/models` |
| Providers | `GET /v1/providers` |

Auth accepts either `X-API-Key: <key>` or `Authorization: Bearer <key>`.

Agent-facing endpoints accept `X-Aqli-Request-Id`, `X-Aqli-Trace-Id`,
`X-Aqli-Session-Id`, and `X-Aqli-User-Id` headers, with equivalent body
fields such as `request_id`, `trace_id`, `session_id`, and `user`. Responses
echo the correlation headers and include `metadata.aqli_trace`; errors include
the same ids inside the `error` object.

`GET /v1/agent/contract` returns a machine-readable contract for agent
frameworks. It includes endpoint paths, request schema hints, auth options, and
traceability rules plus the serving/orchestrator boundary for chat, Responses,
tools, MCP discovery, embeddings, and RAG context.

Streaming chat and Responses requests accept `stream_options`. Set
`include_usage: true` when the caller wants provider token usage on final stream
events. `include_trace` and `include_stream_metadata` default to true, adding
`metadata.aqli_trace` and `metadata.aqli_stream` so agent clients can stitch
SSE chunks to request/session traces. Streams terminate with `data:[DONE]`.

The OpenAPI document includes examples for the agent contract, chat completions,
Responses, embeddings, and MCP discovery endpoints. In dev mode it is available
from `GET /q/openapi`.

Agent-facing endpoints use OpenAI-style errors:
`{"error":{"message":"...","type":"invalid_request_error"}}`.

`POST /v1/agent/validate?surface=chat|responses|embeddings` validates and
normalizes a request without invoking a model. The response redacts prompt text
into counts, roles, and metadata so orchestrators can test mapping safely.

`POST /v1/agent/tools/validate` validates and normalizes OpenAI-compatible
function, MCP tool, code interpreter, and file search definitions before
inference. It returns portability warnings for JSON Schema features that some
agent clients may ignore. Tool authorization and execution are still owned by
the calling agent orchestrator.

MCP discovery endpoints are intentionally read-only. `GET /v1/mcp/tools?compat=openai`
returns OpenAI-compatible function tool definitions with `x_aqli` metadata that
identifies the MCP server/tool, while tool authorization, execution, and result
looping remain owned by the calling agent orchestrator.

RAG context can be supplied as `rag_context`, `retrieval_context`, or
`context_documents` using a string, array of strings, or structured chunks with
`text`/`content`, `source`, `title`, `id`, `score`, and `metadata`. Aqli
injects that context into the model request and preserves source metadata, but
retrieval policy and vector store ownership stay outside the serving engine.

Example agent request:

```json
{
  "model": "my-model",
  "messages": [
    {"role": "system", "content": "Answer using the supplied tools when needed."},
    {"role": "user", "content": "Find the latest context and summarize it."}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "search_context",
        "description": "Search the external knowledge base",
        "parameters": {"type": "object"}
      },
      "x_aqli": {"mcp_server": "knowledge"}
    }
  ],
  "tool_choice": "auto",
  "rag_enabled": true
}
```

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/aqli-server-runtime-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
