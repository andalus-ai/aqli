package tech.kayys.aqli.cli.commands.ocr;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots OCR result presentation options. */
public final class OcrOutputRunCapability
        implements RunCapability, RunCapabilityOptions<OcrOutputRunOptions> {

    @Override
    public String id() {
        return "ocr-output";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--ocr-json", "--onnx-ocr-json")
                .type(boolean.class)
                .description("Print PaddleOCR-VL OCR result as structured JSON")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ocr-json-output", "--ocr-metadata-output")
                .type(String.class)
                .description("Write PaddleOCR-VL OCR result JSON to this path")
                .build());
        commandSpec.addOption(OptionSpec.builder("--ocr-overlay-output", "--ocr-boxes-output")
                .type(String.class)
                .description("Write a PNG preview with PaddleOCR-VL OCR boxes drawn over the input image")
                .build());
    }

    @Override
    public Class<OcrOutputRunOptions> optionsType() {
        return OcrOutputRunOptions.class;
    }

    @Override
    public OcrOutputRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new OcrOutputRunOptions(
                parseResult.matchedOptionValue("--ocr-json", false),
                parseResult.matchedOptionValue("--ocr-json-output", (String) null),
                parseResult.matchedOptionValue("--ocr-overlay-output", (String) null));
    }
}
