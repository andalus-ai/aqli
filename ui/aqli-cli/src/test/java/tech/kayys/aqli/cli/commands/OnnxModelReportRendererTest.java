package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnnxModelReportRendererTest {

    @Test
    void rendersModelMetadataGraphsSidecarsWarningsAndRecommendations() {
        OnnxModelDiagnostics.GraphInfo graph = new OnnxModelDiagnostics.GraphInfo(
                "decoder.onnx", "decoder", 2048L, true, "q4",
                List.of(new OnnxModelDiagnostics.TensorIoInfo("input_ids", "int64", List.of("1", "8"))),
                List.of(new OnnxModelDiagnostics.TensorIoInfo("logits", "float32", List.of("1", "8", "32000"))),
                "parsed");
        OnnxModelDiagnostics.Report report = new OnnxModelDiagnostics.Report(
                Path.of("/models/demo"), Path.of("/models/demo"), "text-generation", "llama",
                List.of("LlamaForCausalLM"), null, List.of(graph), List.of("tokenizer.json"),
                List.of("text-generation"), List.of("external data detected"), List.of("use a runtime probe"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        int exitCode = OnnxModelReportRenderer.print(
                report, "org/demo", "/models/demo", Path.of("/models/demo"),
                Path::toString, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("Requested model: org/demo"));
        assertTrue(output.contains("Architectures: LlamaForCausalLM"));
        assertTrue(output.contains("decoder.onnx [decoder, q4, external-data, 2.0 KiB]"));
        assertTrue(output.contains("Sidecars"));
        assertTrue(output.contains("tokenizer.json"));
        assertTrue(output.contains("Warnings"));
        assertTrue(output.contains("Next steps"));
    }

    @Test
    void reportsAttentionNeededWhenNoGraphsWereFound() {
        OnnxModelDiagnostics.Report report = new OnnxModelDiagnostics.Report(
                Path.of("/models/empty"), Path.of("/models/empty"), null, null,
                List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        int exitCode = OnnxModelReportRenderer.print(
                report, null, null, Path.of("/models/empty"),
                Path::toString, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        assertEquals(1, exitCode);
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Graphs"));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("(none)"));
    }
}
