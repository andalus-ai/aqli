package tech.kayys.aqli.runtime.tool;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;
import tech.kayys.aljabr.core.tool.ToolCall;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallValidationServiceTest {

    private final ToolCallValidationService service = new ToolCallValidationService();

    @Test
    void validatesArgumentsAgainstTheMatchingRequestDeclaration() {
        var request = request(tool("lookup", Map.of(
                "type", "OBJECT",
                "required", List.of("id"),
                "additionalProperties", false,
                "properties", Map.of("id", Map.of("type", "INTEGER", "minimum", 1)))));

        var result = service.validate(request, new ToolCall("lookup", Map.of("id", 42)));

        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void reportsInvalidArgumentsWithSchemaPaths() {
        var request = request(tool("lookup", Map.of(
                "type", "OBJECT",
                "required", List.of("id"),
                "additionalProperties", false,
                "properties", Map.of("id", Map.of("type", "INTEGER", "minimum", 1)))));

        var result = service.validate(request, new ToolCall("lookup", Map.of("id", "wrong", "extra", true)));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.path().equals("$.id")));
        assertTrue(result.errors().stream().anyMatch(error -> error.path().equals("$.extra")));
    }

    @Test
    void rejectsCallsForToolsNotDeclaredOnTheRequest() {
        var result = service.validate(request(tool("lookup", Map.of("type", "OBJECT"))),
                new ToolCall("delete", Map.of()));

        assertFalse(result.valid());
        assertEquals("$tool", result.errors().getFirst().path());
    }

    @Test
    void reportsWhenRequestDeclaresNoTools() {
        var result = service.validate(request(), new ToolCall("lookup", Map.of()));

        assertFalse(result.valid());
        assertTrue(result.errors().getFirst().message().contains("no tools are declared"));
    }

    @Test
    void rejectsAmbiguousDuplicateDeclarations() {
        var declaration = tool("lookup", Map.of("type", "OBJECT"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(new ToolCall("lookup", Map.of()),
                        List.of(declaration, declaration)));
    }

    @Test
    void rejectsNullDependenciesAndInputs() {
        assertThrows(NullPointerException.class, () -> new ToolCallValidationService(null));
        assertThrows(NullPointerException.class, () -> service.validate((InferenceRequest) null,
                new ToolCall("lookup", Map.of())));
        assertThrows(NullPointerException.class, () -> service.validate(
                (ToolCall) null, List.of(tool("lookup", Map.of("type", "OBJECT")))));
    }

    private static InferenceRequest request(ToolDefinition... tools) {
        var builder = InferenceRequest.builder().model("test-model").message(
                new Message(Message.Role.USER, "test"));
        for (ToolDefinition tool : tools) {
            builder.tool(tool);
        }
        return builder.build();
    }

    private static ToolDefinition tool(String name, Map<String, Object> schema) {
        return ToolDefinition.builder().name(name).parameters(schema).build();
    }
}
