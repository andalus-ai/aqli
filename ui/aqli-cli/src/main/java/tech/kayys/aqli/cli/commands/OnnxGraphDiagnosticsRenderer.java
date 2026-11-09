package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders ONNX graph metadata for CLI diagnostics.
 *
 * <p>This class deliberately owns presentation only. Model inspection and
 * ONNX execution probes remain in their respective runner components.</p>
 */
final class OnnxGraphDiagnosticsRenderer {

    private OnnxGraphDiagnosticsRenderer() {
    }

    static void printGenAiInfo(OnnxModelDiagnostics.GenAiInfo info, PrintStream output) {
        List<String> details = new ArrayList<>();
        if (info.type() != null && !info.type().isBlank()) {
            details.add("type=" + info.type());
        }
        if (info.layers() > 0) {
            details.add("layers=" + info.layers());
        }
        if (info.attentionHeads() > 0) {
            details.add("heads=" + info.attentionHeads());
        }
        if (info.keyValueHeads() > 0) {
            details.add("kv_heads=" + info.keyValueHeads());
        }
        if (info.headSize() > 0) {
            details.add("head_size=" + info.headSize());
        }
        if (info.contextLength() > 0) {
            details.add("ctx=" + info.contextLength());
        }
        if (info.vocabSize() > 0) {
            details.add("vocab=" + info.vocabSize());
        }
        if (!details.isEmpty()) {
            output.println("ORT GenAI: " + String.join(", ", details));
        }
        output.println("GenAI IO: inputs="
                + info.inputIdsName() + ", " + info.attentionMaskName() + ", " + info.positionIdsName()
                + "; kv=" + info.pastKeyNameTemplate() + " / " + info.pastValueNameTemplate()
                + "; outputs=" + info.logitsName()
                + ", " + info.presentKeyNameTemplate() + " / " + info.presentValueNameTemplate());
    }

    static void printGraphIo(OnnxModelDiagnostics.GraphInfo graph, PrintStream output) {
        String ioStatus = graph.ioStatus();
        if (ioStatus != null && !ioStatus.isBlank() && !"parsed".equalsIgnoreCase(ioStatus)) {
            output.println("  io: " + ioStatus);
        }
        if (!graph.inputs().isEmpty()) {
            output.println("  inputs: " + formatTensorIoSummary(graph.inputs()));
        }
        if (!graph.outputs().isEmpty()) {
            output.println("  outputs: " + formatTensorIoSummary(graph.outputs()));
        }
    }

    static String formatTensorIoSummary(List<OnnxModelDiagnostics.TensorIoInfo> tensors) {
        Map<String, List<OnnxModelDiagnostics.TensorIoInfo>> grouped = new LinkedHashMap<>();
        List<OnnxModelDiagnostics.TensorIoInfo> direct = new ArrayList<>();
        for (OnnxModelDiagnostics.TensorIoInfo tensor : tensors) {
            String group = tensorIoGroup(tensor.name());
            if (group == null) {
                direct.add(tensor);
            } else {
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(tensor);
            }
        }

        List<String> parts = new ArrayList<>();
        int maxDirect = grouped.isEmpty() ? 10 : 6;
        int directLimit = Math.min(direct.size(), maxDirect);
        for (int i = 0; i < directLimit; i++) {
            parts.add(formatTensorIo(direct.get(i)));
        }
        if (direct.size() > directLimit) {
            parts.add("+" + (direct.size() - directLimit) + " more tensors");
        }
        for (Map.Entry<String, List<OnnxModelDiagnostics.TensorIoInfo>> entry : grouped.entrySet()) {
            List<OnnxModelDiagnostics.TensorIoInfo> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }
            parts.add(entry.getKey()
                    + " (" + values.size()
                    + " tensors, e.g. " + formatTensorIo(values.get(0)) + ")");
        }
        return parts.isEmpty() ? "(none)" : String.join("; ", parts);
    }

    private static String tensorIoGroup(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith("past_key_values.")) {
            return "past_key_values.*";
        }
        if (name.startsWith("present.")) {
            return "present.*";
        }
        if (name.startsWith("past.")) {
            return "past.*";
        }
        return null;
    }

    private static String formatTensorIo(OnnxModelDiagnostics.TensorIoInfo tensor) {
        String shape = tensor.shape().isEmpty() ? "[]" : "[" + String.join(",", tensor.shape()) + "]";
        return tensor.name() + ":" + tensor.elementType() + shape;
    }
}
