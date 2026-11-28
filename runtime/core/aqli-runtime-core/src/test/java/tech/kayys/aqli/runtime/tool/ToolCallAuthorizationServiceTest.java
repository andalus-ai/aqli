package tech.kayys.aqli.runtime.tool;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;
import tech.kayys.aljabr.core.tool.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallAuthorizationServiceTest {

    private static final ToolCall CALL = new ToolCall("lookup", Map.of("id", 7));

    @Test
    void authorizesOnlyAfterSuccessfulArgumentValidation() {
        var invocations = new AtomicInteger();
        var service = service((request, call) -> {
            invocations.incrementAndGet();
            return ToolCallAuthorizer.AuthorizationDecision.allow();
        });

        var result = service.authorize(request(), CALL);

        assertTrue(result.validation().valid());
        assertTrue(result.authorizationAttempted());
        assertTrue(result.authorized());
        assertEquals(1, invocations.get());
    }

    @Test
    void invalidCallNeverReachesTheAuthorizer() {
        var invocations = new AtomicInteger();
        var service = service((request, call) -> {
            invocations.incrementAndGet();
            return ToolCallAuthorizer.AuthorizationDecision.allow();
        });

        var result = service.authorize(request(), new ToolCall("lookup", Map.of("id", "not-an-integer")));

        assertFalse(result.validation().valid());
        assertFalse(result.authorizationAttempted());
        assertFalse(result.authorized());
        assertEquals(0, invocations.get());
    }

    @Test
    void explicitPolicyDenialIsPreserved() {
        var service = service((request, call) ->
                ToolCallAuthorizer.AuthorizationDecision.deny("user lacks lookup permission"));

        var result = service.authorize(request(), CALL);

        assertTrue(result.validation().valid());
        assertTrue(result.authorizationAttempted());
        assertFalse(result.authorized());
        assertEquals("user lacks lookup permission", result.denialReason());
    }

    @Test
    void authorizerFailureDoesNotBecomeAnAllowDecision() {
        var service = service((request, call) -> {
            throw new IllegalStateException("policy service unavailable");
        });

        assertThrows(IllegalStateException.class, () -> service.authorize(request(), CALL));
    }

    @Test
    void missingAuthorizerAndNullDecisionsFailClosed() {
        assertThrows(NullPointerException.class,
                () -> new ToolCallAuthorizationService(new ToolCallValidationService(), null));

        var service = service((request, call) -> null);
        assertThrows(NullPointerException.class, () -> service.authorize(request(), CALL));
    }

    @Test
    void denialRequiresAnExplanation() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolCallAuthorizer.AuthorizationDecision(false, " "));
    }

    private static ToolCallAuthorizationService service(ToolCallAuthorizer authorizer) {
        return new ToolCallAuthorizationService(new ToolCallValidationService(), authorizer);
    }

    private static InferenceRequest request() {
        var tool = ToolDefinition.builder()
                .name("lookup")
                .parameters(Map.of(
                        "type", "OBJECT",
                        "required", List.of("id"),
                        "additionalProperties", false,
                        "properties", Map.of("id", Map.of("type", "INTEGER", "minimum", 1))))
                .build();
        return InferenceRequest.builder()
                .model("test-model")
                .message(new Message(Message.Role.USER, "lookup"))
                .tool(tool)
                .build();
    }
}
