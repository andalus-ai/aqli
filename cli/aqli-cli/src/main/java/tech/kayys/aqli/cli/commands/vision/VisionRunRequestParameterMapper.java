package tech.kayys.aqli.cli.commands.vision;

import java.util.List;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps CLI vision and OCR options onto the internal inference execution request.
 *
 * <p>Legacy parameter aliases are intentionally retained here because existing
 * runners consume more than one spelling of the input-image fields.</p>
 */
public final class VisionRunRequestParameterMapper {

    private VisionRunRequestParameterMapper() {
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null) {
            throw new IllegalArgumentException("inference request builder is required");
        }
        if (options == null) {
            throw new IllegalArgumentException("vision request options are required");
        }

        List<String> imagePaths = options.imagePaths() == null ? List.of() : options.imagePaths();
        if (!imagePaths.isEmpty()) {
            String primaryImage = imagePaths.get(0);
            request.parameter("image_path", primaryImage);
            request.parameter("input_image", primaryImage);
            request.parameter("vision_input_path", primaryImage);
            request.parameter("image_paths", imagePaths);
            request.parameter("vision_input_paths", imagePaths);
        }

        if (options.ocrMode()) {
            request.parameter("task", "ocr");
            request.parameter("ocr", true);
        } else if (!imagePaths.isEmpty()) {
            request.parameter("task", "vision");
        }

        if (options.ocrImageTokens() != null) {
            request.parameter("ocr_image_tokens", Math.max(0, options.ocrImageTokens()));
        }
        if (options.ocrRawOutput()) {
            request.parameter("ocr_raw_output", true);
        }
    }

    public record Options(
            List<String> imagePaths,
            boolean ocrMode,
            Integer ocrImageTokens,
            boolean ocrRawOutput) {
    }
}
