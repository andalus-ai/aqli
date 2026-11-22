package tech.kayys.aqli.runtime.tool;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolCallParseDiagnostic;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.core.tool.ToolCallProtocolDescriptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallParsingServiceTest {

    private static final ToolCallProtocolDescriptor PROTOCOL =
            ToolCallProtocolDescriptor.of("example-native-calls");

    @Test
    void selectsParserByExplicitResponseProtocolAndPreservesPartialResult() {
        var expected = new ToolCallParseResult(
                List.of(new ToolCall("lookup", Map.of("id", 42))),
                List.of(new ToolCallParseDiagnostic(
                        "RECOVERED_SEGMENT",
                        ToolCallParseDiagnostic.Severity.WARNING,
                        "A malformed segment was skipped",
                        17)));
        var registry = ToolCallParserRegistry.builder()
                .register(PROTOCOL, output -> {
                    assertEquals("assistant output", output);
                    return expected;
                })
                .build();
        var service = new ToolCallParsingService(registry);

        var actual = service.parse(PROTOCOL, "assistant output");

        assertSame(expected, actual);
        assertEquals("lookup", actual.calls().getFirst().name());
        assertEquals(ToolCallParseDiagnostic.Severity.WARNING,
                actual.diagnostics().getFirst().severity());
    }

    @Test
    void failsClearlyWhenProtocolHasNoRegisteredParser() {
        var service = new ToolCallParsingService(ToolCallParserRegistry.builder().build());

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.parse(PROTOCOL, "assistant output"));

        assertTrue(error.getMessage().contains(PROTOCOL.id()));
    }

    @Test
    void rejectsNullDependenciesAndInputs() {
        assertThrows(NullPointerException.class, () -> new ToolCallParsingService(null));
        var service = new ToolCallParsingService(ToolCallParserRegistry.builder().build());
        assertThrows(NullPointerException.class, () -> service.parse(null, "output"));
        assertThrows(NullPointerException.class, () -> service.parse(PROTOCOL, null));
    }
}
