package tech.kayys.aqli.cli.commands.image;

import java.util.List;

import tech.kayys.aqli.spi.inference.InferenceRequest;

/**
 * Maps image, vision, and OCR CLI options onto the inference SPI request.
 *
 * <p>This class is a transport adapter only. Image capability semantics and
 * public request types should ultimately live in the transport-neutral SDK
 * capability API, shared by CLI, REST, and gRPC.</p>
 */
public final class ImageRunRequestParameterMapper {

    private ImageRunRequestParameterMapper() {
    }

    public record Options(
            Long seed,
            Integer steps,
            String format,
            Float guidanceScale,
            String outputPath,
            Integer width,
            Integer height,
            List<String> inputImages,
            boolean ocrMode,
            Integer ocrImageTokens,
            boolean ocrRawOutput,
            String onnxVariant,
            boolean raw,
            boolean debugPrompt) {
        public Options {
            inputImages = inputImages == null ? List.of() : List.copyOf(inputImages);
        }
    }

    public static void apply(InferenceRequest.Builder request, Options options) {
        if (request == null || options == null) {
            throw new IllegalArgumentException("request and image options are required");
        }

        add(request, "seed", options.seed());
        add(request, "steps", options.steps());
        addText(request, "format", options.format());
        add(request, "guidance_scale", options.guidanceScale());
        addText(request, "output_path", options.outputPath());
        add(request, "width", options.width());
        add(request, "height", options.height());

        if (!options.inputImages().isEmpty()) {
            String firstImage = options.inputImages().get(0);
            request.parameter("image_path", firstImage);
            request.parameter("input_image", firstImage);
            request.parameter("vision_input_path", firstImage);
            request.parameter("image_paths", options.inputImages());
            request.parameter("vision_input_paths", options.inputImages());
        }

        if (options.ocrMode()) {
            request.parameter("task", "ocr");
            request.parameter("ocr", true);
        } else if (!options.inputImages().isEmpty()) {
            request.parameter("task", "vision");
        }

        if (options.ocrImageTokens() != null) {
            request.parameter("ocr_image_tokens", Math.max(0, options.ocrImageTokens()));
        }
        if (options.ocrRawOutput()) {
            request.parameter("ocr_raw_output", true);
        }
        addText(request, "onnx_variant", options.onnxVariant());

        request.parameter("raw", options.raw());
        request.parameter("debug_prompt", options.debugPrompt());
    }

    private static void addText(InferenceRequest.Builder request, String key, String value) {
        if (value != null && !value.isBlank()) request.parameter(key, value);
    }

    private static void add(InferenceRequest.Builder request, String key, Object value) {
        if (value != null) request.parameter(key, value);
    }
}
