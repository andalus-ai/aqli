package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnnxGraphDiagnosticsRendererTest {

    @Test
    void groupsKvCacheTensorNamesAndLimitsDirectTensorDetails() {
        List<OnnxModelDiagnostics.TensorIoInfo> tensors = List.of(
                new OnnxModelDiagnostics.TensorIoInfo("input_ids", "int64", List.of("1", "128")),
                new OnnxModelDiagnostics.TensorIoInfo("attention_mask", "int64", List.of("1", "128")),
                new OnnxModelDiagnostics.TensorIoInfo("past_key_values.0.key", "float16", List.of("1", "8", "128", "64")),
                new OnnxModelDiagnostics.TensorIoInfo("past_key_values.0.value", "float16", List.of("1", "8", "128", "64")),
                new OnnxModelDiagnostics.TensorIoInfo("present.0.key", "float16", List.of("1", "8", "128", "64")));

        assertEquals(
                "input_ids:int64[1,128]; attention_mask:int64[1,128]; "
                        + "past_key_values.* (2 tensors, e.g. past_key_values.0.key:float16[1,8,128,64]); "
                        + "present.* (1 tensors, e.g. present.0.key:float16[1,8,128,64])",
                OnnxGraphDiagnosticsRenderer.formatTensorIoSummary(tensors));
    }

    @Test
    void printsGraphIoStatusAndTensorSummaries() {
        OnnxModelDiagnostics.GraphInfo graph = new OnnxModelDiagnostics.GraphInfo(
                "decoder.onnx", "decoder", 1024L, false, "full",
                List.of(new OnnxModelDiagnostics.TensorIoInfo("input_ids", "int64", List.of("1", "8"))),
                List.of(new OnnxModelDiagnostics.TensorIoInfo("logits", "float32", List.of("1", "8", "32000"))),
                "unavailable: metadata not parsed");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        OnnxGraphDiagnosticsRenderer.printGraphIo(graph,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("io: unavailable: metadata not parsed"));
        assertTrue(output.contains("inputs: input_ids:int64[1,8]"));
        assertTrue(output.contains("outputs: logits:float32[1,8,32000]"));
    }

    @Test
    void printsGenAiMetadataWithoutEmptyOptionalDetails() {
        OnnxModelDiagnostics.GenAiInfo info = new OnnxModelDiagnostics.GenAiInfo(
                "llama", 32, 32, 8, 128, 4096, 8192, 32000,
                "input_ids", "attention_mask", "position_ids",
                "past_key_values.%d.key", "past_key_values.%d.value",
                "logits", "present.%d.key", "present.%d.value");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        OnnxGraphDiagnosticsRenderer.printGenAiInfo(info,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("ORT GenAI: type=llama, layers=32, heads=32, kv_heads=8, head_size=128, ctx=8192, vocab=32000"));
        assertTrue(output.contains("GenAI IO: inputs=input_ids, attention_mask, position_ids"));
    }
}
