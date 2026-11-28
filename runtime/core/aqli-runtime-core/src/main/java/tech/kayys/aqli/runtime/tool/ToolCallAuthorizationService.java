package tech.kayys.aqli.runtime.tool;

import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolSchemaValidator;

import java.util.Objects;

/**
 * Fail-closed authorization gate for model-originated tool calls.
 *
 * <p>Arguments are validated against the request's declarations before the
 * configured application authorizer is consulted. Invalid calls never reach
 * the authorizer. This service does not resolve or execute tools.</p>
 */
public final class ToolCallAuthorizationService {

    private final ToolCallValidationService validation;
    private final ToolCallAuthorizer authorizer;

    public ToolCallAuthorizationService(
            ToolCallValidationService validation,
            ToolCallAuthorizer authorizer) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    /**
     * Validates and then authorizes a tool call.
     *
     * <p>If validation fails, the policy is not invoked and the returned
     * result is unauthorized. Authorizer exceptions propagate to the caller;
     * callers must treat them as denial and must not dispatch.</p>
     */
    public AuthorizationResult authorize(InferenceRequest request, ToolCall call) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(call, "call");

        ToolSchemaValidator.ValidationResult validationResult = validation.validate(request, call);
        if (!validationResult.valid()) {
            return new AuthorizationResult(validationResult, null);
        }

        ToolCallAuthorizer.AuthorizationDecision decision =
                Objects.requireNonNull(authorizer.authorize(request, call),
                        "authorizer decision");
        return new AuthorizationResult(validationResult, decision);
    }

    /** Immutable outcome; authorized is true only after successful validation and explicit allow. */
    public record AuthorizationResult(
            ToolSchemaValidator.ValidationResult validation,
            ToolCallAuthorizer.AuthorizationDecision decision) {

        public AuthorizationResult {
            Objects.requireNonNull(validation, "validation");
            if (!validation.valid() && decision != null) {
                throw new IllegalArgumentException(
                        "Authorization must not be attempted for an invalid tool call");
            }
        }

        public boolean authorizationAttempted() {
            return decision != null;
        }

        public boolean authorized() {
            return validation.valid() && decision != null && decision.allowed();
        }

        public String denialReason() {
            if (!validation.valid()) {
                return "tool call failed argument validation";
            }
            return decision == null ? "authorization was not completed" : decision.reason();
        }
    }
}
