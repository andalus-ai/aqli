package tech.kayys.aqli.cli.commands.image;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps image-generation CLI options onto the internal inference execution request.
 *
 * <p>Generated-image output settings are kept separate from vision input-image
 * and OCR settings. This is a CLI adapter, not the public SDK capability contract.</p>
 */
public final class ImageGenerationRunRequestParameterMapper {

    private ImageGenerationRunRequestParameterMapper() {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and image-generation options are required");
        }

        if (options.featurePipelineId() != null && !options.featurePipelineId().isBlank()) {
            String pipelineId = options.featurePipelineId().trim();
            request.parameter("pipeline", pipelineId);
            request.parameter("feature", pipelineId);
            request.parameter("feature_pipeline", true);
        }
        if (options.guidanceScale() != null) request.parameter("guidance_scale", options.guidanceScale());
        if (options.outputPath() != null) request.parameter("output_path", options.outputPath());
        if (options.width() != null) request.parameter("width", options.width());
        if (options.height() != null) request.parameter("height", options.height());
    }

    public record Options(
            String featurePipelineId,
            Double guidanceScale,
            String outputPath,
            Integer width,
            Integer height) {
    }
}
