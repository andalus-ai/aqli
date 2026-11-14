package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;
import tech.kayys.aqli.onnx.runner.PaddleOcrVlOnnxPlanner;
import tech.kayys.aqli.onnx.runner.PaddleOcrVlOnnxProbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Handles ONNX and PaddleOCR-VL model diagnostics, preflight, and pipeline probing.
 */
final class OnnxDiagnosticsCliHandler {

    private final String prompt;
    private final String onnxVariant;
    private final Integer onnxProbeImageTokens;
    private final Integer onnxProbeDecodeTokens;

    OnnxDiagnosticsCliHandler(
            String prompt,
            String onnxVariant,
            Integer onnxProbeImageTokens,
            Integer onnxProbeDecodeTokens) {
        this.prompt = prompt;
        this.onnxVariant = onnxVariant;
        this.onnxProbeImageTokens = onnxProbeImageTokens;
        this.onnxProbeDecodeTokens = onnxProbeDecodeTokens;
    }

    int printOnnxDiagnostics(String modelId, String finalLocalPath) {
        Path diagnosticPath = resolveOnnxDiagnosticPath(modelId, finalLocalPath);
        if (diagnosticPath == null) {
            System.out.println("ONNX diagnostics");
            System.out.println("================");
            System.out.println("Requested model: " + nullToDash(modelId));
            System.out.println("Resolved path: " + nullToDash(finalLocalPath));
            System.err.println();
            System.err.println("Error: no local ONNX path was resolved for diagnostics.");
            System.err.println("Pull the model first or pass --modelDir /path/to/repository.");
            return 1;
        }

        OnnxModelDiagnostics.Report report = OnnxModelDiagnostics.inspect(diagnosticPath);
        int exitCode = OnnxModelReportRenderer.print(
                report, modelId, finalLocalPath, diagnosticPath, CliPathUtils::displayPath, System.out);
        if (report.isPaddleOcrVl()) {
            exitCode = Math.max(exitCode, printPaddleOcrVlPreflight(report, List.of()));
        }
        System.out.println();
        System.out.println(exitCode == 0 ? "Status: inspected" : "Status: attention needed");
        return exitCode;
    }

    int printOnnxVisionProbe(
            String modelId,
            String finalLocalPath,
            List<String> normalizedImages,
            boolean includePipelineGraphs) {
        System.out.println();
        System.out.println(includePipelineGraphs ? "PaddleOCR-VL pipeline probe" : "PaddleOCR-VL vision probe");
        System.out.println(includePipelineGraphs ? "--------------------------" : "-------------------------");

        Path diagnosticPath = resolveOnnxDiagnosticPath(modelId, finalLocalPath);
        if (diagnosticPath == null) {
            System.err.println("Error: no local ONNX path was resolved for vision probe.");
            return 1;
        }
        OnnxModelDiagnostics.Report report = OnnxModelDiagnostics.inspect(diagnosticPath);
        if (!report.isPaddleOcrVl() || report.modelDir() == null) {
            System.err.println("Error: --onnx-vision-probe is only available for local PaddleOCR-VL ONNX repositories.");
            System.err.println("Resolved path: " + CliPathUtils.displayPath(diagnosticPath));
            System.err.println("Detected pipeline: " + nullToDash(report.pipelineType()));
            return 1;
        }

        if (!validateInputImages(normalizedImages)) {
            return 1;
        }
        if (normalizedImages.isEmpty()) {
            System.err.println("Error: --onnx-vision-probe requires --image /path/to/page.png.");
            return 1;
        }

        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        try {
            if (includePipelineGraphs) {
                printOnnxPipelineProbeDetails(report, Path.of(normalizedImages.get(0)), threads);
                return 0;
            }
            PaddleOcrVlOnnxProbe.VisionEncoderProbeResult result =
                    PaddleOcrVlOnnxProbe.runVisionEncoder(
                            report.modelDir(),
                            Path.of(normalizedImages.get(0)),
                            onnxVariant,
                            threads);
            PaddleOcrVlOnnxPlanner.ImageTensor tensor = result.imageTensor();
            PaddleOcrVlOnnxPlanner.ImagePlan image = tensor.image();
            System.out.println("Graph variant: " + result.plan().graphs().variant()
                    + (onnxVariant == null || onnxVariant.isBlank() ? " (auto)" : " (requested " + onnxVariant + ")"));
            System.out.println("Vision graph: " + relativeOrDisplay(result.plan().modelDir(), result.graph()));
            System.out.println("Threads: " + threads);
            System.out.println("Image: " + CliPathUtils.displayPath(image.path())
                    + " " + image.originalWidth() + "x" + image.originalHeight()
                    + " -> " + image.resizedWidth() + "x" + image.resizedHeight()
                    + ", grid=[" + image.gridT() + "," + image.gridH() + "," + image.gridW() + "]"
                    + ", patches=" + image.patchCount());
            System.out.println("Input tensor: pixel_values" + formatShape(tensor.pixelValuesShape())
                    + ", image_grid_thw" + formatShape(tensor.imageGridThwShape())
                    + "=" + java.util.Arrays.toString(tensor.imageGridThw())
                    + ", checksum=" + tensor.checksum()
                    + ", range=[" + formatDiagnosticNumber(tensor.min())
                    + "," + formatDiagnosticNumber(tensor.max())
                    + "], mean=" + formatDiagnosticNumber(tensor.mean()));
            System.out.println("Session load: " + formatDuration(result.loadDuration())
                    + ", run: " + formatDuration(result.runDuration()));

            printProbeIo("Inputs", result.inputs());
            printProbeIo("Declared outputs", result.declaredOutputs());
            printProbeIo("Actual outputs", result.outputs());

            System.out.println("Status: vision_encoder executed");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: PaddleOCR-VL "
                    + (includePipelineGraphs ? "pipeline" : "vision")
                    + " probe failed: " + e.getMessage());
            return 1;
        }
    }

    private void printOnnxPipelineProbeDetails(OnnxModelDiagnostics.Report report, Path imagePath, int threads) {
        int imageTokenLimit = onnxProbeImageTokens == null ? 64 : Math.max(0, onnxProbeImageTokens);
        int decodeTokens = onnxProbeDecodeTokens == null ? 8 : Math.max(0, onnxProbeDecodeTokens);
        String probePrompt = prompt == null || prompt.isBlank()
                ? "Extract all text from the image."
                : prompt;
        PaddleOcrVlOnnxProbe.PromptDecodeProbeResult result =
                PaddleOcrVlOnnxProbe.runPromptDecode(
                        report.modelDir(),
                        imagePath,
                        probePrompt,
                        onnxVariant,
                        imageTokenLimit,
                        decodeTokens,
                        threads);
        PaddleOcrVlOnnxPlanner.ImageTensor tensor = result.imageTensor();
        PaddleOcrVlOnnxPlanner.ImagePlan image = tensor.image();
        System.out.println("Graph variant: " + result.plan().graphs().variant()
                + (onnxVariant == null || onnxVariant.isBlank() ? " (auto)" : " (requested " + onnxVariant + ")"));
        System.out.println("Vision graph: " + relativeOrDisplay(result.plan().modelDir(), result.visionGraph()));
        System.out.println("Embedding graph: " + relativeOrDisplay(result.plan().modelDir(), result.embeddingGraph()));
        System.out.println("Decoder graph: " + relativeOrDisplay(result.plan().modelDir(), result.decoderGraph()));
        System.out.println("Threads: " + threads);
        System.out.println("Image: " + CliPathUtils.displayPath(image.path())
                + " " + image.originalWidth() + "x" + image.originalHeight()
                + " -> " + image.resizedWidth() + "x" + image.resizedHeight()
                + ", grid=[" + image.gridT() + "," + image.gridH() + "," + image.gridW() + "]"
                + ", patches=" + image.patchCount());
        System.out.println("Input tensor: pixel_values" + formatShape(tensor.pixelValuesShape())
                + ", image_grid_thw" + formatShape(tensor.imageGridThwShape())
                + "=" + java.util.Arrays.toString(tensor.imageGridThw())
                + ", checksum=" + tensor.checksum()
                + ", range=[" + formatDiagnosticNumber(tensor.min())
                + "," + formatDiagnosticNumber(tensor.max())
                + "], mean=" + formatDiagnosticNumber(tensor.mean()));
        System.out.println("Vision output: image_embeds[" + result.imageTokens()
                + "," + result.hiddenSize() + "]");
        System.out.println("Decoder prefill: sequence=" + result.sequenceLength()
                + ", image embeddings used=" + result.usedImageTokens() + "/" + result.imageTokens()
                + ", prompt_embeds" + formatShape(result.promptEmbedsShape()));
        System.out.println("Prompt tokens: input_ids=" + result.inputIds().length
                + ", image_token_id=" + result.imageTokenId()
                + ", image token positions=" + result.imageTokenPositions());
        System.out.println("Prompt preview: "
                + truncateDisplay(result.promptText().replaceAll("\\s+", " "), 120));
        if (imageTokenLimit > 0 && result.usedImageTokens() < result.imageTokens()) {
            System.out.println("Decoder prefill limit: " + imageTokenLimit
                    + " image embeddings (pass --onnx-probe-image-tokens 0 to use all)");
        }
        printDecoderLogitsSummary(result.decoderLogits());
        System.out.println("Durations: vision load=" + formatDuration(result.visionLoadDuration())
                + ", vision run=" + formatDuration(result.visionRunDuration())
                + ", embedding load=" + formatDuration(result.embeddingLoadDuration())
                + ", embedding run=" + formatDuration(result.embeddingRunDuration())
                + ", decoder load=" + formatDuration(result.decoderLoadDuration())
                + ", decoder prefill=" + formatDuration(result.decoderPrefillDuration())
                + ", decode loop=" + formatDuration(result.decoderDecodeDuration()));
        if (decodeTokens > 0) {
            System.out.println("Generated token ids: " + java.util.Arrays.toString(result.generatedTokenIds()));
            System.out.println("Decoded preview: "
                    + (result.decodedText() == null || result.decodedText().isBlank()
                            ? "(empty)"
                            : truncateDisplay(result.decodedText().replaceAll("\\s+", " "), 160)));
            PaddleOcrVlOnnxProbe.OcrPostProcessResult postProcess =
                    PaddleOcrVlOnnxProbe.postProcessOcrText(result.decodedText(), image);
            if (!postProcess.locations().isEmpty()) {
                System.out.println("Postprocessed OCR preview: "
                        + truncateDisplay(postProcess.displayText().replaceAll("\\s+", " "), 180));
            }
            System.out.println("Decode finish: " + result.finishReason()
                    + ", generated=" + result.generatedTokenIds().length + "/" + decodeTokens);
        }
        printProbeIo("Decoder inputs", result.decoderInputs(), 10);
        printProbeIo("Decoder declared outputs", result.decoderDeclaredOutputs(), 10);
        printProbeIo("Decoder actual outputs", result.decoderOutputs(), 12, true);
        System.out.println("Status: vision_encoder, tokenizer/chat prompt, embedding, decoder prefill, and greedy decode executed");
    }

    private void printDecoderLogitsSummary(PaddleOcrVlOnnxProbe.DecoderLogitsSummary summary) {
        if (summary == null) {
            return;
        }
        StringBuilder top = new StringBuilder();
        int[] tokenIds = summary.topTokenIds();
        float[] logits = summary.topLogits();
        if (tokenIds != null && logits != null) {
            for (int i = 0; i < Math.min(tokenIds.length, logits.length); i++) {
                if (tokenIds[i] < 0 || !Float.isFinite(logits[i])) {
                    continue;
                }
                if (top.length() > 0) {
                    top.append(", ");
                }
                top.append(tokenIds[i]).append('=').append(formatDiagnosticNumber(logits[i]));
            }
        }
        System.out.println("Decoder logits: " + summary.name()
                + " " + summary.type() + formatShape(summary.shape())
                + ", last token index=" + summary.sequenceIndex()
                + ", vocab=" + summary.vocabSize()
                + (top.length() > 0 ? ", top ids: " + top : ""));
    }

    int printPaddleOcrVlPreflight(OnnxModelDiagnostics.Report report, List<String> normalizedImages) {
        System.out.println();
        System.out.println("PaddleOCR-VL preflight");
        System.out.println("----------------------");
        if (report.modelDir() == null) {
            System.out.println("Model directory: missing");
            return 1;
        }

        if (!validateInputImages(normalizedImages)) {
            return 1;
        }

        try {
            List<Path> imagePaths = normalizedImages.stream().map(Path::of).toList();
            PaddleOcrVlOnnxPlanner.Plan plan =
                    PaddleOcrVlOnnxPlanner.plan(report.modelDir(), imagePaths, onnxVariant);
            PaddleOcrVlOnnxPlanner.ProcessorConfig processor = plan.processor();
            System.out.println("Graph variant: " + plan.graphs().variant()
                    + (onnxVariant == null || onnxVariant.isBlank() ? " (auto)" : " (requested " + onnxVariant + ")"));
            System.out.println("Processor: patch=" + processor.patchSize()
                    + ", merge=" + processor.mergeSize()
                    + ", temporal=" + processor.temporalPatchSize()
                    + ", pixels=" + processor.minPixels() + ".." + processor.maxPixels());
            System.out.println("Selected graphs:");
            printSelectedPaddleGraph("vision_encoder", plan.graphs().visionEncoder(), plan.modelDir());
            printSelectedPaddleGraph("embedding", plan.graphs().embedding(), plan.modelDir());
            printSelectedPaddleGraph("decoder", plan.graphs().decoder(), plan.modelDir());
            if (!plan.graphs().warnings().isEmpty()) {
                for (String warning : plan.graphs().warnings()) {
                    System.out.println("- Warning: " + warning);
                }
            }

            if (plan.images().isEmpty()) {
                System.out.println("Images: none provided (pass --image /path/to/page.png for OCR image-grid preflight)");
            } else {
                System.out.println("Images:");
                for (PaddleOcrVlOnnxPlanner.ImagePlan image : plan.images()) {
                    System.out.println("- " + CliPathUtils.displayPath(image.path())
                            + ": " + image.originalWidth() + "x" + image.originalHeight()
                            + " -> " + image.resizedWidth() + "x" + image.resizedHeight()
                            + ", grid=[" + image.gridT() + "," + image.gridH() + "," + image.gridW() + "]"
                            + ", patches=" + image.patchCount()
                            + ", prompt image tokens=" + image.promptImageTokens());
                    PaddleOcrVlOnnxPlanner.ImageTensor tensor =
                            PaddleOcrVlOnnxPlanner.preprocessImage(image.path(), processor);
                    System.out.println("  tensor: pixel_values" + formatShape(tensor.pixelValuesShape())
                            + ", image_grid_thw" + formatShape(tensor.imageGridThwShape())
                            + "=" + java.util.Arrays.toString(tensor.imageGridThw())
                            + ", " + formatBytes(tensor.byteSize())
                            + ", checksum=" + tensor.checksum()
                            + ", range=[" + formatDiagnosticNumber(tensor.min())
                            + "," + formatDiagnosticNumber(tensor.max())
                            + "], mean=" + formatDiagnosticNumber(tensor.mean()));
                }
                System.out.println("Total prompt image tokens: " + plan.totalPromptImageTokens());
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: PaddleOCR-VL preflight failed: " + e.getMessage());
            return 1;
        }
    }

    private void printSelectedPaddleGraph(String label, Path graph, Path modelDir) {
        String path = graph == null ? "-" : relativeOrDisplay(modelDir, graph);
        String status = graph != null && Files.isRegularFile(graph) ? "ok" : "missing";
        long bytes = graph == null ? 0L : onnxGraphBundleSize(graph);
        System.out.println("- " + label + ": " + path
                + " [" + status + (bytes > 0 ? ", " + formatBytes(bytes) : "") + "]");
    }

    private String relativeOrDisplay(Path base, Path path) {
        if (path == null) {
            return "-";
        }
        try {
            return base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (Exception ignored) {
            return CliPathUtils.displayPath(path);
        }
    }

    private long onnxGraphBundleSize(Path graph) {
        if (graph == null) {
            return 0L;
        }
        long bytes = 0L;
        try {
            bytes += Files.size(graph);
        } catch (Exception ignored) {
            // Best-effort diagnostic.
        }
        Path parent = graph.getParent();
        String name = graph.getFileName() == null ? "" : graph.getFileName().toString();
        if (parent == null || name.isBlank()) {
            return bytes;
        }
        for (Path candidate : List.of(
                parent.resolve(name + ".data"),
                parent.resolve(name.replaceFirst("\\.onnx$", ".onnx.data")),
                parent.resolve(name.replaceFirst("\\.onnx$", ".data")))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                bytes += Files.size(candidate);
            } catch (Exception ignored) {
                // Best-effort diagnostic.
            }
        }
        return bytes;
    }

    private void printProbeIo(String title, List<PaddleOcrVlOnnxProbe.IoInfo> values) {
        printProbeIo(title, values, Integer.MAX_VALUE);
    }

    private void printProbeIo(String title, List<PaddleOcrVlOnnxProbe.IoInfo> values, int maxEntries) {
        printProbeIo(title, values, maxEntries, false);
    }

    private void printProbeIo(
            String title,
            List<PaddleOcrVlOnnxProbe.IoInfo> values,
            int maxEntries,
            boolean includeLogits) {
        System.out.println(title + ":");
        if (values == null || values.isEmpty()) {
            System.out.println("- (none)");
            return;
        }
        int limit = Math.min(values.size(), Math.max(0, maxEntries));
        for (int i = 0; i < limit; i++) {
            printProbeIoValue(values.get(i));
        }
        int highlighted = 0;
        if (includeLogits && values.size() > limit) {
            for (int i = limit; i < values.size(); i++) {
                if (isLogitsIo(values.get(i))) {
                    printProbeIoValue(values.get(i));
                    highlighted++;
                }
            }
        }
        int omitted = values.size() - limit - highlighted;
        if (omitted > 0) {
            System.out.println("- ... " + omitted + " more");
        }
    }

    private void printProbeIoValue(PaddleOcrVlOnnxProbe.IoInfo value) {
        System.out.println("- " + value.name()
                + ": " + value.kind()
                + ", " + value.type()
                + formatShape(value.shape())
                + formatElementCount(value.elements()));
    }

    private boolean isLogitsIo(PaddleOcrVlOnnxProbe.IoInfo value) {
        return value != null
                && value.name() != null
                && value.name().toLowerCase(Locale.ROOT).contains("logit");
    }

    private Path resolveOnnxDiagnosticPath(String modelId, String finalLocalPath) {
        return OnnxDiagnosticModelPathResolver.resolve(finalLocalPath, modelId, CliPathUtils::existingCliPath);
    }

    private boolean validateInputImages(List<String> images) {
        boolean ok = true;
        for (String image : images) {
            Path path = Path.of(image);
            if (!Files.isRegularFile(path)) {
                System.err.println("Error: input image not found: " + image);
                ok = false;
            }
        }
        return ok;
    }

    private static String formatShape(long[] shape) {
        return shape == null ? "[]" : java.util.Arrays.toString(shape).replace(" ", "");
    }

    private static String formatDiagnosticNumber(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return "-";
        }
        double millis = duration.toNanos() / 1_000_000.0;
        if (millis < 1000.0) {
            return String.format(Locale.ROOT, "%.1f ms", millis);
        }
        return String.format(Locale.ROOT, "%.2f s", millis / 1000.0);
    }

    private static String formatElementCount(long elements) {
        return elements < 0 ? "" : ", elements=" + elements;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "B", "KiB", "MiB", "GiB", "TiB" };
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String truncateDisplay(String value, int width) {
        if (value == null) {
            return "";
        }
        if (value.length() <= width) {
            return value;
        }
        return value.substring(0, Math.max(0, width - 3)) + "...";
    }
}
