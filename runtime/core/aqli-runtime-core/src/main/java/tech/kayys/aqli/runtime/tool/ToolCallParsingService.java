package tech.kayys.aqli.runtime.tool;

import tech.kayys.aljabr.core.tool.ToolCallParseResult;
import tech.kayys.aljabr.core.tool.ToolCallParserRegistry;
import tech.kayys.aljabr.core.tool.ToolCallProtocolDescriptor;

import java.util.Objects;

/**
 * Runtime entry point for parsing model output that may contain tool calls.
 *
 * <p>The caller supplies the protocol descriptor resolved from the selected
 * model/response configuration. The service delegates parsing to Aljabr's
 * registered protocol adapter and preserves its recoverable diagnostics.</p>
 *
 * <p>This service does not validate tool arguments, authorize tool access,
 * resolve tool implementations, dispatch calls, or execute them. Those
 * operations belong to AQLI's tool execution boundary.</p>
 */
public final class ToolCallParsingService {

    private final ToolCallParserRegistry parsers;

    public ToolCallParsingService(ToolCallParserRegistry parsers) {
        this.parsers = Objects.requireNonNull(parsers, "parsers");
    }

    /**
     * Parses output using the parser registered for the selected protocol.
     *
     * @param protocol protocol declared by the model/response configuration
     * @param modelOutput untrusted model output
     * @return parsed calls and diagnostics
     * @throws IllegalArgumentException if no parser is registered for the protocol
     */
    public ToolCallParseResult parse(
            ToolCallProtocolDescriptor protocol,
            String modelOutput) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(modelOutput, "modelOutput");
        return parsers.require(protocol).parseResult(modelOutput);
    }
}
