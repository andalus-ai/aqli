package tech.kayys.aqli.runtime.tool;

import io.smallrye.mutiny.Uni;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolCallParseDiagnostic;
import tech.kayys.aljabr.core.tool.ToolCallParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates the complete model-output-to-tool-result flow.
 *
 * <p>Parsing remains protocol-specific and delegated to Aljabr. AQLI then
 * applies request declaration validation, authorization, implementation
 * resolution, and execution through the existing runtime services.</p>
 *
 * <p>Calls execute sequentially in parser order. Any parse error prevents all
 * dispatch, avoiding partial side effects from output known to be malformed.
 * Parse warnings are preserved and do not by themselves prevent valid calls
 * from running.</p>
 */
public final class ToolCallExecutionService {

    private final ToolCallParsingService parsing;
    private final ToolCallDispatchService dispatch;

    public ToolCallExecutionService(
            ToolCallParsingService parsing,
            ToolCallDispatchService dispatch) {
        this.parsing = Objects.requireNonNull(parsing, "parsing");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    /**
     * Parses model output and executes every extracted call in source order.
     *
     * @param request original inference request with explicit protocol and tool declarations
     * @param modelOutput untrusted model output
     * @param trustedExecutionContext host-created execution context
     * @return parse diagnostics and ordered tool results
     */
    public Uni<ToolCallExecutionResult> execute(
            InferenceRequest request,
            String modelOutput,
            Map<String, Object> trustedExecutionContext) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(modelOutput, "modelOutput");
        Objects.requireNonNull(trustedExecutionContext, "trustedExecutionContext");

        ToolCallParseResult parsed = parsing.parse(request, modelOutput);
        if (parsed.hasErrors()) {
            return Uni.createFrom().failure(new ToolCallParseException(parsed));
        }

        Uni<List<ToolCallExecution>> sequence =
                Uni.createFrom().item(new ArrayList<>());
        for (ToolCall call : parsed.calls()) {
            sequence = sequence.flatMap(results ->
                    dispatch.dispatch(request, call, trustedExecutionContext)
                            .map(result -> {
                                results.add(new ToolCallExecution(call, result));
                                return results;
                            }));
        }

        return sequence.map(results ->
                new ToolCallExecutionResult(parsed, results));
    }

    /** Ordered result for one successfully executed call. */
    public record ToolCallExecution(ToolCall call, Map<String, Object> result) {
        public ToolCallExecution {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(result, "result");
            result = Map.copyOf(result);
        }
    }

    /** Complete result for a parsed batch. */
    public record ToolCallExecutionResult(
            ToolCallParseResult parsing,
            List<ToolCallExecution> executions) {
        public ToolCallExecutionResult {
            Objects.requireNonNull(parsing, "parsing");
            executions = List.copyOf(Objects.requireNonNull(executions, "executions"));
            if (parsing.hasErrors() && !executions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Parse errors must prevent all tool execution");
            }
        }

        public List<ToolCallParseDiagnostic> diagnostics() {
            return parsing.diagnostics();
        }

        public boolean hasToolCalls() {
            return !executions.isEmpty();
        }
    }

    /** Raised when the parser reports malformed or incomplete tool-call output. */
    public static final class ToolCallParseException extends RuntimeException {
        private final ToolCallParseResult parseResult;

        public ToolCallParseException(ToolCallParseResult parseResult) {
            super("Tool-call output contains parse errors: "
                    + parseResult.diagnostics().stream()
                            .filter(diagnostic ->
                                    diagnostic.severity() == ToolCallParseDiagnostic.Severity.ERROR)
                            .map(ToolCallParseDiagnostic::message)
                            .toList());
            this.parseResult = Objects.requireNonNull(parseResult, "parseResult");
        }

        public ToolCallParseResult parseResult() {
            return parseResult;
        }
    }
}
