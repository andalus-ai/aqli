package tech.kayys.aqli.cli.commands.ocr;

/** Immutable snapshot of OCR result presentation and file-output settings. */
public record OcrOutputRunOptions(
        boolean structuredJson,
        String jsonOutputPath,
        String overlayOutputPath) {
}
