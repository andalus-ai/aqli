package tech.kayys.aqli.server.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class AgentTraceContext {
    static final String REQUEST_ID_HEADER = "X-Aqli-Request-Id";
    static final String TRACE_ID_HEADER = "X-Aqli-Trace-Id";
    static final String SESSION_ID_HEADER = "X-Aqli-Session-Id";
    static final String USER_ID_HEADER = "X-Aqli-User-Id";

    private final String requestId;
    private final String traceId;
    private final String sessionId;
    private final String userId;

    private AgentTraceContext(String requestId, String traceId, String sessionId, String userId) {
        this.requestId = requireId(requestId);
        this.traceId = firstNonBlank(traceId, this.requestId);
        this.sessionId = clean(sessionId);
        this.userId = clean(userId);
    }

    static AgentTraceContext from(HttpHeaders headers, JsonNode payload) {
        String requestId = firstNonBlank(
                text(payload, "request_id"),
                header(headers, REQUEST_ID_HEADER, "X-Request-Id", "X-Request-ID",
                        "X-Correlation-Id", "X-Correlation-ID"),
                UUID.randomUUID().toString());
        String traceId = firstNonBlank(
                text(payload, "trace_id"),
                text(payload, "trace"),
                header(headers, TRACE_ID_HEADER, "X-Trace-Id", "X-Trace-ID"),
                traceParent(header(headers, "traceparent")),
                header(headers, "X-Correlation-Id", "X-Correlation-ID"),
                requestId);
        String sessionId = firstNonBlank(
                text(payload, "session_id"),
                text(payload, "conversation"),
                text(payload, "previous_response_id"),
                header(headers, SESSION_ID_HEADER, "X-Session-Id", "X-Conversation-Id"));
        String userId = firstNonBlank(
                text(payload, "user"),
                text(payload, "user_id"),
                header(headers, USER_ID_HEADER, "X-User-Id"));
        return new AgentTraceContext(requestId, traceId, sessionId, userId);
    }

    static AgentTraceContext fromPayload(JsonNode payload) {
        return from(null, payload);
    }

    String requestId() {
        return requestId;
    }

    void apply(InferenceRequest.Builder builder) {
        builder.requestId(requestId);
        builder.traceId(traceId);
        if (!isBlank(sessionId)) {
            builder.sessionId(sessionId);
        }
        if (!isBlank(userId)) {
            builder.userId(userId);
        }
        builder.metadata("aqli_trace", asMap());
    }

    void applyToParameters(Map<String, Object> parameters) {
        parameters.put("aqli_trace", asMap());
    }

    Response.ResponseBuilder applyHeaders(Response.ResponseBuilder builder) {
        builder.header(REQUEST_ID_HEADER, requestId);
        builder.header(TRACE_ID_HEADER, traceId);
        if (!isBlank(sessionId)) {
            builder.header(SESSION_ID_HEADER, sessionId);
        }
        if (!isBlank(userId)) {
            builder.header(USER_ID_HEADER, userId);
        }
        return builder;
    }

    Map<String, Object> withTraceMetadata(Map<String, Object> metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metadata != null) {
            out.putAll(metadata);
        }
        out.put("aqli_trace", asMap());
        return out;
    }

    Map<String, Object> asMap() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("request_id", requestId);
        trace.put("trace_id", traceId);
        if (!isBlank(sessionId)) {
            trace.put("session_id", sessionId);
        }
        if (!isBlank(userId)) {
            trace.put("user_id", userId);
        }
        return trace;
    }

    private static String requireId(String value) {
        String cleaned = clean(value);
        return isBlank(cleaned) ? UUID.randomUUID().toString() : cleaned;
    }

    private static String header(HttpHeaders headers, String... names) {
        if (headers == null || names == null) {
            return null;
        }
        for (String name : names) {
            String value = clean(headers.getHeaderString(name));
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : clean(value.asText());
    }

    private static String traceParent(String value) {
        String cleaned = clean(value);
        if (isBlank(cleaned)) {
            return null;
        }
        String[] parts = cleaned.split("-");
        if (parts.length >= 2 && parts[1].matches("[0-9a-fA-F]{32}")) {
            return parts[1].toLowerCase(java.util.Locale.ROOT);
        }
        return cleaned;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String cleaned = clean(value);
            if (!isBlank(cleaned)) {
                return cleaned;
            }
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
