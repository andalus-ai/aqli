package tech.kayys.aqli.cli.commands.image;

import java.util.List;

/** Immutable snapshot of vision input and OCR inference settings. */
public record VisionInputRunOptions(
        String inputImagePath,
        List<String> inputImagePaths,
        boolean ocrMode,
        Integer ocrImageTokens,
        boolean ocrRawOutput) {
    public VisionInputRunOptions {
        inputImagePaths = inputImagePaths == null ? List.of() : List.copyOf(inputImagePaths);
    }
}
