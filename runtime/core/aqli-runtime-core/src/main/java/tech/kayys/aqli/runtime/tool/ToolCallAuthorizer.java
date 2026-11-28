package tech.kayys.aqli.runtime.tool;

import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aljabr.core.tool.ToolCall;

/**
 * Application-supplied authorization policy for a validated tool call.
 *
 * <p>Implementations must make their decision using trusted request identity
 * and application policy, never model-generated arguments as identity or
 * authority. This SPI deliberately supplies no permissive default.</p>
 */
@FunctionalInterface
public interface ToolCallAuthorizer {

    AuthorizationDecision authorize(InferenceRequest request, ToolCall call);

    /** Explicit allow/deny result from the configured policy. */
    record AuthorizationDecision(boolean allowed, String reason) {

        public AuthorizationDecision {
            if (!allowed && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("A denial must include a reason");
            }
        }

        public static AuthorizationDecision allow() {
            return new AuthorizationDecision(true, null);
        }

        public static AuthorizationDecision deny(String reason) {
            return new AuthorizationDecision(false, reason);
        }
    }
}
