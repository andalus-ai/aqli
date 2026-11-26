package tech.kayys.aqli.runtime.tool;

import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.Tool;
import tech.kayys.aljabr.core.tool.ToolCall;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates, authorizes, resolves, and invokes one model-originated tool call.
 *
 * <p>The ordering is intentional: no tool lookup or execution occurs until
 * request-level validation and the configured authorization policy both
 * succeed. The resolver and tool implementation are application supplied;
 * this service does not create a registry or a default policy.</p>
 *
 * <p>The execution context must be assembled by the trusted host application.
 * Model-generated arguments are passed only as tool input and must not be
 * used by callers as identity or authorization context.</p>
 */
public final class ToolCallDispatchService {

    private final ToolCallAuthorizationService authorization;
    private final ToolCallResolver resolver;

    public ToolCallDispatchService(
            ToolCallAuthorizationService authorization,
            ToolCallResolver resolver) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Dispatches a call only after successful validation and explicit authorization.
     *
     * <p>Validation/authorization and resolution failures are surfaced before
     * execution. An authorization denial or missing implementation returns a
     * failed Uni and never invokes {@link Tool#execute(Map, Map)}.</p>
     *
     * @param request inference request containing the trusted tool declarations
     * @param call parsed, untrusted model-originated tool call
     * @param executionContext trusted context supplied by the host application
     * @return asynchronous tool result
     */
    public Uni<Map<String, Object>> dispatch(
            InferenceRequest request,
            ToolCall call,
            Map<String, Object> executionContext) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(executionContext, "executionContext");

        ToolCallAuthorizationService.AuthorizationResult result =
                authorization.authorize(request, call);
        if (!result.authorized()) {
            return Uni.createFrom().failure(
                    new ToolCallDispatchException("Tool call is not authorized: " + result.denialReason()));
        }

        Tool tool = resolver.resolve(call.name())
                .orElseThrow(() -> new ToolCallDispatchException(
                        "No implementation is registered for declared tool '" + call.name() + "'"));
        Map<String, Object> trustedContext =
                Collections.unmodifiableMap(new HashMap<>(executionContext));
        return Objects.requireNonNull(
                tool.execute(call.arguments(), trustedContext),
                "tool execution returned null");
    }

    /** Failure raised when a call cannot cross the dispatch boundary. */
    public static final class ToolCallDispatchException extends RuntimeException {
        public ToolCallDispatchException(String message) {
            super(message);
        }
    }
}
