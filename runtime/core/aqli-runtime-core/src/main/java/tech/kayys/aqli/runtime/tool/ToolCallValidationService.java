package tech.kayys.aqli.runtime.tool;

import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;
import tech.kayys.aljabr.core.tool.ToolCall;
import tech.kayys.aljabr.core.tool.ToolDeclaration;
import tech.kayys.aljabr.core.tool.ToolSchemaValidator;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates parsed, untrusted tool calls against the declarations on an
 * inference request.
 *
 * <p>AQLI owns request-level tool selection and validation orchestration.
 * Aljabr supplies the model-independent schema validator. This service does
 * not authorize access, resolve implementations, dispatch, or execute calls.</p>
 */
public final class ToolCallValidationService {

    private final ToolSchemaValidator validator;

    public ToolCallValidationService() {
        this(new ToolSchemaValidator());
    }

    public ToolCallValidationService(ToolSchemaValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Validates a parsed call against the tools declared by the inference request.
     *
     * <p>A missing or unknown declaration is reported as a validation error.
     * Duplicate declarations with the same name are rejected as ambiguous
     * request configuration.</p>
     */
    public ToolSchemaValidator.ValidationResult validate(
            InferenceRequest request, ToolCall call) {
        Objects.requireNonNull(request, "request");
        return validate(call, request.getTools());
    }

    /**
     * Validates a parsed call against a request's tool declarations.
     */
    public ToolSchemaValidator.ValidationResult validate(
            ToolCall call, List<ToolDefinition> declarations) {
        Objects.requireNonNull(call, "call");
        if (declarations == null || declarations.isEmpty()) {
            return invalid("$tool", "no tools are declared for this inference request");
        }

        ToolDefinition match = null;
        for (ToolDefinition declaration : declarations) {
            Objects.requireNonNull(declaration, "tool declaration");
            if (!call.name().equals(declaration.getName())) {
                continue;
            }
            if (match != null) {
                throw new IllegalArgumentException(
                        "Ambiguous tool declarations: multiple tools named '" + call.name() + "'");
            }
            match = declaration;
        }

        if (match == null) {
            return invalid("$tool", "tool '" + call.name() + "' is not declared by this inference request");
        }

        ToolDefinition selected = match;
        ToolDeclaration adapter = new ToolDeclaration() {
            @Override
            public String name() {
                return selected.getName();
            }

            @Override
            public String description() {
                return selected.getDescription().orElse("");
            }

            @Override
            public Map<String, Object> parameterSchema() {
                return selected.getParameters();
            }
        };
        return validator.validate(call, adapter);
    }

    private static ToolSchemaValidator.ValidationResult invalid(String path, String message) {
        return new ToolSchemaValidator.ValidationResult(
                List.of(new ToolSchemaValidator.ValidationError(path, message)));
    }
}
