package tech.kayys.aqli.sdk.core.tool;

import tech.kayys.aljabr.core.tool.ToolCallParseDiagnostic;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.core.tool.ToolCallProtocolDescriptor;
import tech.kayys.aqli.spi.inference.InferenceResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Enriches an inference response with tool calls extracted by an explicitly
 * selected Aljabr protocol parser.
 *
 * <p>The response metadata key {@value #PROTOCOL_METADATA_KEY} identifies
 * the wire protocol. The runner/model adapter is responsible for setting
 * that key from trusted model configuration; this class never guesses from
 * model names or parses arbitrary output without an explicit protocol.</p>
 *
 * <p>Already-structured tool calls take precedence. Parsed calls remain
 * untrusted data: this class does not validate schemas, resolve tools,
 * authorize callers, dispatch, or execute anything.</p>
 */
public final class ToolCallResponseParser {

    /** Response metadata key containing the registered tool-call protocol ID. */
    public static final String PROTOCOL_METADATA_KEY = "tool_call_protocol";

    /** Response metadata key containing parser diagnostics, when present. */
    public static final String DIAGNOSTICS_METADATA_KEY = "tool_call_parse_diagnostics";

    private final ToolCallParserRegistry registry;

    public ToolCallResponseParser(ToolCallParserRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Returns the original response when it already has structured calls or
     * does not declare a protocol. A declared but unregistered protocol is a
     * configuration error and fails explicitly.
     */
    public InferenceResponse apply(InferenceResponse response) {
        Objects.requireNonNull(response, "response");
        if (response.hasToolCalls()) {
            return response;
        }

        Object protocolValue = response.getMetadata().get(PROTOCOL_METADATA_KEY);
        if (!(protocolValue instanceof String protocolId) || protocolId.isBlank()) {
            return response;
        }

        var protocol = ToolCallProtocolDescriptor.of(protocolId);
        var parser = registry.require(protocol);
        ToolCallParseResult result = parser.parseResult(response.getContent());
        if (result.isEmpty()) {
            return response;
        }

        InferenceResponse.Builder builder = response.toBuilder();
        result.calls().forEach(call -> builder.toolCall(
                new InferenceResponse.ToolCall(call.name(), call.arguments())));
        if (result.hasCalls()) {
            builder.finishReason(InferenceResponse.FinishReason.TOOL_CALLS);
        }
        if (!result.diagnostics().isEmpty()) {
            builder.metadata(DIAGNOSTICS_METADATA_KEY, result.diagnostics().stream()
                    .map(ToolCallResponseParser::toDiagnosticMetadata)
                    .toList());
        }
        return builder.build();
    }

    private static Map<String, Object> toDiagnosticMetadata(ToolCallParseDiagnostic diagnostic) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", diagnostic.code());
        value.put("severity", diagnostic.severity().name());
        value.put("message", diagnostic.message());
        value.put("offset", diagnostic.offset());
        return Map.copyOf(value);
    }
}
