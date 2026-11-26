package tech.kayys.aqli.runtime.tool;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.Tool;
import tech.kayys.aqli.spi.tool.ToolDefinition;
import tech.kayys.aljabr.core.tool.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallDispatchServiceTest {

    private static final ToolCall CALL = new ToolCall("lookup", Map.of("id", 7));

    @Test
    void executesResolvedToolOnlyAfterExplicitAuthorization() {
        var resolutions = new AtomicInteger();
        var executions = new AtomicInteger();
        Tool tool = tool(executions);
        var service = service(
                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.allow(),
                name -> {
                    resolutions.incrementAndGet();
                    assertEquals("lookup", name);
                    return Optional.of(tool);
                });

        Map<String, Object> result = service.dispatch(request(), CALL, Map.of("tenant", "trusted"))
                .await().indefinitely();

        assertEquals(Map.of("found", true), result);
        assertEquals(1, resolutions.get());
        assertEquals(1, executions.get());
    }

    @Test
    void deniedCallNeverResolvesOrExecutesTool() {
        var resolutions = new AtomicInteger();
        var executions = new AtomicInteger();
        var service = service(
                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.deny("policy denied"),
                name -> {
                    resolutions.incrementAndGet();
                    return Optional.of(tool(executions));
                });

        var failure = assertThrows(RuntimeException.class,
                () -> service.dispatch(request(), CALL, Map.of()).await().indefinitely());

        assertTrue(rootMessage(failure).contains("policy denied"));
        assertEquals(0, resolutions.get());
        assertEquals(0, executions.get());
    }

    @Test
    void invalidCallNeverResolvesOrExecutesTool() {
        var resolutions = new AtomicInteger();
        var executions = new AtomicInteger();
        var service = service(
                (request, call) -> {
                    fail("authorizer must not see invalid arguments");
                    return ToolCallAuthorizer.AuthorizationDecision.allow();
                },
                name -> {
                    resolutions.incrementAndGet();
                    return Optional.of(tool(executions));
                });

        assertThrows(RuntimeException.class,
                () -> service.dispatch(request(),
                        new ToolCall("lookup", Map.of("id", "not-an-integer")), Map.of())
                        .await().indefinitely());

        assertEquals(0, resolutions.get());
        assertEquals(0, executions.get());
    }

    @Test
    void missingImplementationDoesNotExecuteAnything() {
        var executions = new AtomicInteger();
        var service = service(
                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.allow(),
                name -> Optional.empty());

        var failure = assertThrows(ToolCallDispatchService.ToolCallDispatchException.class,
                () -> service.dispatch(request(), CALL, Map.of()).await().indefinitely());

        assertTrue(failure.getMessage().contains("No implementation"));
        assertEquals(0, executions.get());
    }

    @Test
    void resolverFailureDoesNotExecuteTool() {
        var executions = new AtomicInteger();
        var service = service(
                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.allow(),
                name -> {
                    throw new IllegalStateException("registry unavailable");
                });

        assertThrows(IllegalStateException.class,
                () -> service.dispatch(request(), CALL, Map.of()));
        assertEquals(0, executions.get());
    }

    @Test
    void missingDependenciesAndInputsFailFast() {
        assertThrows(NullPointerException.class,
                () -> new ToolCallDispatchService(null, name -> Optional.empty()));
        assertThrows(NullPointerException.class,
                () -> new ToolCallDispatchService(
                        new ToolCallAuthorizationService(new ToolCallValidationService(),
                                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.allow()),
                        null));
        var service = service(
                (request, call) -> ToolCallAuthorizer.AuthorizationDecision.allow(),
                name -> Optional.of(tool(new AtomicInteger())));
        assertThrows(NullPointerException.class, () -> service.dispatch(null, CALL, Map.of()));
        assertThrows(NullPointerException.class, () -> service.dispatch(request(), null, Map.of()));
        assertThrows(NullPointerException.class, () -> service.dispatch(request(), CALL, null));
    }

    private static ToolCallDispatchService service(
            ToolCallAuthorizer authorizer,
            ToolCallResolver resolver) {
        return new ToolCallDispatchService(
                new ToolCallAuthorizationService(new ToolCallValidationService(), authorizer),
                resolver);
    }

    private static Tool tool(AtomicInteger executions) {
        return new Tool() {
            @Override
            public String id() {
                return "lookup-tool";
            }

            @Override
            public String name() {
                return "lookup";
            }

            @Override
            public String description() {
                return "Lookup a record";
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of();
            }

            @Override
            public Uni<Map<String, Object>> execute(
                    Map<String, Object> arguments,
                    Map<String, Object> context) {
                executions.incrementAndGet();
                assertEquals(Map.of("id", 7), arguments);
                assertEquals("trusted", context.get("tenant"));
                assertThrows(UnsupportedOperationException.class, () -> context.put("tenant", "changed"));
                return Uni.createFrom().item(Map.of("found", true));
            }
        };
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

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
