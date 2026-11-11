package tech.kayys.aqli.server.api.v1;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

final class AgentErrorMapper {

    static final String INVALID_REQUEST = "invalid_request_error";
    static final String SERVER_ERROR = "server_error";
    static final String UNSUPPORTED_STREAMING_ACCEPT = "unsupported_streaming_accept";

    private AgentErrorMapper() {
    }

    static Response badRequest(Exception error) {
        return badRequest(error, null);
    }

    static Response badRequest(Exception error, AgentTraceContext traceContext) {
        return badRequest(message(error, "Invalid request."), INVALID_REQUEST, traceContext);
    }

    static Response badRequest(String message, String type) {
        return badRequest(message, type, null);
    }

    static Response badRequest(String message, String type, AgentTraceContext traceContext) {
        return response(Response.Status.BAD_REQUEST, error(message, type, traceContext), traceContext);
    }

    static WebApplicationException badRequestException(Exception error) {
        return badRequestException(error, null);
    }

    static WebApplicationException badRequestException(Exception error, AgentTraceContext traceContext) {
        return new WebApplicationException(badRequest(error, traceContext));
    }

    static Response serverError(Exception error) {
        return serverError(error, null);
    }

    static Response serverError(Exception error, AgentTraceContext traceContext) {
        return response(
                Response.Status.INTERNAL_SERVER_ERROR,
                error(message(error, "Server error."), SERVER_ERROR, traceContext),
                traceContext);
    }

    static Map<String, Object> error(String message, String type) {
        return error(message, type, null);
    }

    static Map<String, Object> error(String message, String type, AgentTraceContext traceContext) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorObject(message, type, traceContext));
        return body;
    }

    static Map<String, Object> errorObject(String message, String type, AgentTraceContext traceContext) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", isBlank(message) ? "Request failed." : message);
        error.put("type", isBlank(type) ? SERVER_ERROR : type);
        if (traceContext != null) {
            error.putAll(traceContext.asMap());
        }
        return error;
    }

    private static Response response(
            Response.Status status, Map<String, Object> entity, AgentTraceContext traceContext) {
        Response.ResponseBuilder builder = Response.status(status).entity(entity);
        if (traceContext != null) {
            traceContext.applyHeaders(builder);
        }
        return builder.build();
    }

    private static String message(Exception error, String fallback) {
        return error == null || isBlank(error.getMessage()) ? fallback : error.getMessage();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
