package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders the inspected ONNX model report for CLI diagnostics.
 *
 * <p>Model inspection, path resolution, preflight, and execution probes are
 * deliberately kept outside this presentation component.</p>
 */
final class OnnxModelReportRenderer {

    private OnnxModelReportRenderer() {
    }

    static int print(
            OnnxModelDiagnostics.Report report,
            String modelId,
            String resolvedPath,
            Path diagnosticPath,
            Function<Path, String> displayPath,
            PrintStream output) {
        output.println("ONNX diagnostics");
        output.println("================");
        output.println("Requested model: " + nullToDash(modelId));
        output.println("Resolved path: " + nullToDash(resolvedPath));
        output.println("Diagnostic path: " + displayPath.apply(diagnosticPath));
        output.println("Model directory: "
                + (report.modelDir() == null ? "-" : displayPath.apply(report.modelDir())));
        output.println("Pipeline: " + nullToDash(report.pipelineType()));
        output.println("Model type: " + nullToDash(report.modelType()));
        if (!report.architectures().isEmpty()) {
            output.println("Architectures: " + String.join(", ", report.architectures()));
        }
        if (!report.capabilities().isEmpty()) {
            output.println("Capabilities: " + String.join(", ", report.capabilities()));
        }
        if (report.genAiInfo() != null) {
            OnnxGraphDiagnosticsRenderer.printGenAiInfo(report.genAiInfo(), output);
        }

        output.println();
        output.println("Graphs");
        output.println("------");
        if (report.graphs().isEmpty()) {
            output.println("(none)");
        } else {
            for (OnnxModelDiagnostics.GraphInfo graph : report.graphs()) {
                List<String> tags = new ArrayList<>();
                tags.add(graph.role());
                tags.add(graph.quantization());
                if (graph.externalData()) {
                    tags.add("external-data");
                }
                output.println("- " + graph.relativePath()
                        + " [" + String.join(", ", tags) + ", " + formatBytes(graph.sizeBytes()) + "]");
                OnnxGraphDiagnosticsRenderer.printGraphIo(graph, output);
            }
        }

        output.println();
        output.println("Sidecars");
        output.println("--------");
        output.println(report.sidecars().isEmpty()
                ? "(none)"
                : formatDiagnosticList(report.sidecars(), 12));
        printSection(output, "Warnings", report.warnings());
        printSection(output, "Next steps", report.recommendations());
        return report.graphs().isEmpty() ? 1 : 0;
    }

    private static void printSection(PrintStream output, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        output.println();
        output.println(title);
        output.println("-".repeat(title.length()));
        for (String value : values) {
            output.println("- " + value);
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String formatDiagnosticList(List<String> values, int limit) {
        int safeLimit = Math.max(1, limit);
        if (values.size() <= safeLimit) {
            return String.join(", ", values);
        }
        return String.join(", ", values.subList(0, safeLimit))
                + ", ... +" + (values.size() - safeLimit) + " more";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
