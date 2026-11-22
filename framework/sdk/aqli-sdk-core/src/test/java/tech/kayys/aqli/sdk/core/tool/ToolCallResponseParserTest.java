package tech.kayys.aqli.sdk.core.tool;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolCallParseDiagnostic;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.core.tool.ToolCallProtocolDescriptor;
import tech.kayys.aqli.spi.inference.InferenceResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallResponseParserTest {

    private static final ToolCallProtocolDescriptor PROTOCOL =
            ToolCallProtocolDescriptor.of("test-native-protocol");

    @Test
    void parsesResponseOnlyWhenTrustedProtocolMetadataSelectsParser() {
        var registry = ToolCallParserRegistry.builder()
                .register(PROTOCOL, output -> new ToolCallParseResult(
                        List.of(new ToolCall("lookup", Map.of("query", "weather"))), List.of()))
                .build();
        var response = response("opaque model output", Map.of(
                ToolCallResponseParser.PROTOCOL_METADATA_KEY, PROTOCOL.id()));

        var parsed = new ToolCallResponseParser(registry).apply(response);

        assertNotSame(response, parsed);
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("lookup", parsed.getToolCalls().getFirst().name());
        assertEquals(Map.of("query", "weather"), parsed.getToolCalls().getFirst().arguments());
        assertEquals(InferenceResponse.FinishReason.TOOL_CALLS, parsed.getFinishReason());
        assertEquals("opaque model output", parsed.getContent());
    }

    @Test
    void preservesExistingStructuredToolCallsWithoutReparsing() {
        var parserInvoked = new boolean[1];
        var registry = ToolCallParserRegistry.builder()
                .register(PROTOCOL, output -> {
                    parserInvoked[0] = true;
                    return new ToolCallParseResult(List.of(), List.of());
                })
                .build();
        var response = InferenceResponse.builder()
                .requestId("request-1")
                .content("already parsed")
                .metadata(ToolCallResponseParser.PROTOCOL_METADATA_KEY, PROTOCOL.id())
                .toolCall(new InferenceResponse.ToolCall("existing", Map.of()))
                .build();

        var parsed = new ToolCallResponseParser(registry).apply(response);

        assertSame(response, parsed);
        assertFalse(parserInvoked[0]);
        assertEquals(1, parsed.getToolCalls().size());
    }

    @Test
    void recordsRecoverableParserDiagnosticsAlongsideValidCalls() {
        var registry = ToolCallParserRegistry.builder()
                .register(PROTOCOL, output -> new ToolCallParseResult(
                        List.of(new ToolCall("valid", Map.of())),
                        List.of(new ToolCallParseDiagnostic(
                                "MALFORMED_SEGMENT",
                                ToolCallParseDiagnostic.Severity.ERROR,
                                "One segment could not be parsed",
                                14))))
                .build();
        var response = response("mixed output", Map.of(
                ToolCallResponseParser.PROTOCOL_METADATA_KEY, PROTOCOL.id()));

        var parsed = new ToolCallResponseParser(registry).apply(response);

        assertEquals(1, parsed.getToolCalls().size());
        assertEquals(InferenceResponse.FinishReason.TOOL_CALLS, parsed.getFinishReason());
        assertEquals(List.of(Map.of(
                "code", "MALFORMED_SEGMENT",
                "severity", "ERROR",
                "message", "One segment could not be parsed",
                "offset", 14)),
                parsed.getMetadata().get(ToolCallResponseParser.DIAGNOSTICS_METADATA_KEY));
    }

    @Test
    void doesNothingWhenNoProtocolIsDeclared() {
        var response = response("ordinary text", Map.of());
        var parsed = new ToolCallResponseParser(ToolCallParserRegistry.builder().build()).apply(response);

        assertSame(response, parsed);
        assertTrue(parsed.getToolCalls().isEmpty());
    }

    @Test
    void failsExplicitlyWhenDeclaredProtocolHasNoRegisteredParser() {
        var response = response("model output", Map.of(
                ToolCallResponseParser.PROTOCOL_METADATA_KEY, "unknown-protocol"));

        assertThrows(IllegalArgumentException.class,
                () -> new ToolCallResponseParser(ToolCallParserRegistry.builder().build()).apply(response));
    }

    private static InferenceResponse response(String content, Map<String, Object> metadata) {
        return InferenceResponse.builder()
                .requestId("request-1")
                .content(content)
                .model("test-model")
                .metadata(metadata)
                .build();
    }
}
