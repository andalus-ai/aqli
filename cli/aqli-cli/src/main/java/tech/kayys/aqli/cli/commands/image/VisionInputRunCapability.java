package tech.kayys.aqli.cli.commands.image;

import java.util.List;
import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots vision-input and OCR inference options. */
public final class VisionInputRunCapability
        implements RunCapability, RunCapabilityOptions<VisionInputRunOptions> {

    @Override
    public String id() {
        return "vision-input";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--image", "--input-image", "--vision-input")
                .type(String.class)
                .description("Input image path for vision/OCR models")
                .build());
        commandSpec.addOption(OptionSpec.builder("--images", "--input-images")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description("Comma-separated input image paths for vision/OCR models")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ocr")
                .type(boolean.class)
                .description("Use the default OCR task prompt when --prompt is omitted")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ocr-image-tokens", "--onnx-image-tokens")
                .type(Integer.class)
                .description("Limit image embeddings used by PaddleOCR-VL ONNX generation (default: 0 = all)")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ocr-raw", "--onnx-ocr-raw")
                .type(boolean.class)
                .description("Print raw PaddleOCR-VL generated token text without location postprocessing")
                .build());
    }

    @Override
    public Class<VisionInputRunOptions> optionsType() {
        return VisionInputRunOptions.class;
    }

    @Override
    public VisionInputRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        List<String> images = parseResult.matchedOptionValue("--images", List.of());
        return new VisionInputRunOptions(
                parseResult.matchedOptionValue("--image", (String) null),
                images,
                parseResult.matchedOptionValue("--ocr", false),
                parseResult.matchedOptionValue("--ocr-image-tokens", (Integer) null),
                parseResult.matchedOptionValue("--ocr-raw", false));
    }
}
