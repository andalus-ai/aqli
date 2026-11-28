package tech.kayys.aqli.runtime.tool;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.Tool;
import tech.kayys.aqli.spi.tool.ToolDefinition;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolCallParseDiagnostic;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.spi.inference.ToolCallProtocolDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallExecutionServiceTest {

    private static final ToolCallProtocolDescriptor PROTOCOL =
            ToolCallProtocolDescriptor.of("test-tools");

    @Test
    void executesCallsSequentiallyAndPreservesOrder() {
        var calls = List.of(
                new ToolCall("lookup", Map.of("id", 1)),
                new ToolCall("lookup", Map.of("id", 2)));
        var parser = parser(new ToolCallParseResult(calls, List.of()));
        var order = new ArrayList<Integer>();
        Tool tool = tool((arguments, context) -> {
            int id = (Integer) arguments.get("id");
            order.add(id);
            return Uni.createFrom().item(Map.of("id", id));
        });

        var result = service(parser, tool).execute(
                request(), "output", Map.of("tenant", "trusted")).await().indefinitely();

        assertEquals(List.of(1, 2), order);
        assertEquals(2, result.executions().size());
        assertEquals(1, result.executions().get(0).result().get("id"));
        assertEquals(2, result.executions().get(1).result().get("id"));
    }

    @Test
    void parseErrorsPreventEveryDispatch() {
        var parsed = new ToolCallParseResult(
                List.of(new ToolCall("lookup", Map.of("id", 1))),
                List.of(new ToolCallParseDiagnostic(
                        "INCOMPLETE_CALL",
                        ToolCallParseDiagnostic.Severity.ERROR,
                        "incomplete tool call",
                        12)));
        var invocations = new AtomicInteger();
        var service = service(parser(parsed), tool((arguments, context) -> {
            invocations.incrementAndGet();
            return Uni.createFrom().item(Map.of("ok", true));
        }));

        var failure = assertThrows(ToolCallExecutionService.ToolCallParseException.class,
                () -> service.execute(request(), "output", Map.of()).await().indefinitely());

        assertTrue(failure.parseResult().hasErrors());
        assertEquals(0, invocations.get());
    }

    @Test
    void warningsArePreservedWhileValidCallsExecute() {
        var parsed = new ToolCallParseResult(
                List.of(new ToolCall("lookup", Map.of("id", 1))),
                List.of(new ToolCallParseDiagnostic(
                        "RECOVERED_SEGMENT",
                        ToolCallParseDiagnostic.Severity.WARNING,
                        "recovered after ignored text",
                        0)));
        var result = service(parser(parsed), tool((arguments, context) ->
                Uni.createFrom().item(Map.of("ok", true))))
                .execute(request(), "output", Map.of()).await().indefinitely();

        assertEquals(1, result.executions().size());
        assertEquals(1, result.diagnostics().size());
        assertEquals(ToolCallParseDiagnostic.Severity.WARNING,
                result.diagnostics().get(0).severity());
    }

    @Test
    void emptyParseProducesEmptySuccessfulResult() {
        var result = service(parser(new ToolCallParseResult(List.of(), List.of())),
                tool((arguments, context) -> Uni.createFrom().item(Map.of())))
                .execute(request(), "ordinary response", Map.of()).await().indefinitely();

        assertTrue(result.executions().isEmpty());
        assertFalse(result.hasToolCalls());
    }

    @Test
    void dispatchFailureStopsLaterCalls() {
        var calls = List.of(
                new ToolCall("lookup", Map.of("id", 1)),
                new ToolCall("lookup", Map.of("id", 2)));
        var order = new ArrayList<Integer>();
        var service = service(parser(new ToolCallParseResult(calls, List.of())),
                tool((arguments, context) -> {
                    int id = (Integer) arguments.get("id");
                    order.add(id);
                    if (id == 1) {
                        return Uni.createFrom().failure(new IllegalStateException("tool failed"));
                    }
                    return Uni.createFrom().item(Map.of("id", id));
                }));

        assertThrows(IllegalStateException.class,
                () -> service.execute(request(), "output", Map.of()).await().indefinitely());
        assertEquals(List.of(1), order);
    }

    private static ToolCallExecutionService service(ToolCallParseResult parsed, Tool tool) {
        return service(parser(parsed), tool);
    }

    private static ToolCallExecutionService service(
            ToolCallParsingService parsing, Tool tool) {
        var validation = new ToolCallValidationService();
        var authorizer = (ToolCallAuthorizer) (request, call) ->
                ToolCallAuthorizer.AuthorizationDecision.allow();
        var authorization = new ToolCallAuthorizationService(validation, authorizer);
        var resolver = (ToolCallResolver) name ->
                "lookup".equals(name) ? Optional.of(tool) : Optional.empty();
        return new ToolCallExecutionService(
                parsing, new ToolCallDispatchService(authorization, resolver));
    }

    private static ToolCallParsingService parser(ToolCallParseResult parsed) {
        var registry = ToolCallParserRegistry.builder()
                .register(PROTOCOL, output -> parsed)
                .build();
        return new ToolCallParsingService(registry);
    }

    private static Tool tool(ToolExecutor executor) {
        return new Tool() {
            @Override public String id() { return "test.lookup"; }
            @Override public String name() { return "lookup"; }
            @Override public String description() { return "test lookup"; }
            @Override public Map<String, Object> inputSchema() {
                return Map.of("type", "OBJECT");
            }
            @Override public Uni<Map<String, Object>> execute(
                    Map<String, Object> arguments, Map<String, Object> context) {
                return executor.execute(arguments, context);
            }
        };
    }

    private static InferenceRequest request() {
        var definition = ToolDefinition.builder()
                .name("lookup")
                .parameters(Map.of(
                        "type", "OBJECT",
                        "required", List.of("id"),
                        "additionalProperties", false,
                        "properties", Map.of("id", Map.of("type", "INTEGER", "minimum", 1))))
                .build();
        return InferenceRequest.builder()
                .model("test-model")
                .message(Message.user("lookup"))
                .tool(definition)
                .toolCallProtocol(PROTOCOL)
                .build();
    }

    @FunctionalInterface
    private interface ToolExecutor {
        Uni<Map<String, Object>> execute(
                Map<String, Object> arguments, Map<String, Object> context);
    }
}
