package tech.kayys.aqli.cli.commands.request;

import tech.kayys.aqli.spi.inference.InferenceRequest;

import java.util.UUID;

/**
 * Creates the common request envelope for the CLI run command.
 *
 * <p>This adapter owns only request identity and base model/prompt execution
 * fields. Capability-specific options and runtime routing remain in their
 * dedicated mappers. The transport-neutral SDK/application request contract
 * is the intended shared source of truth for CLI, REST, and gRPC.</p>
 */
public final class RunRequestBaseAssembler {

    private RunRequestBaseAssembler() {
    }

    public record Options(
            String modelTarget,
            String prompt,
            String pluginId,
            boolean streaming,
            boolean customModelPathUsed,
            String localModelPath,
            String requestedModelRef) {
    }

    public static InferenceRequest.Builder create(Options options) {
        if (options == null) {
            throw new IllegalArgumentException("run request options are required");
        }

        InferenceRequest.Builder request = InferenceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .model(options.modelTarget())
                .prompt(options.prompt())
                .plugin(options.pluginId())
                .streaming(options.streaming());

        if (options.customModelPathUsed() && options.localModelPath() != null) {
            request.parameter("model_path", options.localModelPath());
        }
        if (options.requestedModelRef() != null && !options.requestedModelRef().isBlank()) {
            request.parameter("requested_model_ref", options.requestedModelRef());
        }
        return request;
    }
}
