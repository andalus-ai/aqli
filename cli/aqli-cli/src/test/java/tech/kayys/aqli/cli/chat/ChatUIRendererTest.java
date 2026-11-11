package tech.kayys.aqli.cli.chat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatUIRendererTest {

    @Test
    void printBenchmarksIncludesOnnxProfileBreakdown() {
        ChatUIRenderer.disableColor();
        ChatUIRenderer renderer = new ChatUIRenderer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profile_mode", "onnx");
        metadata.put("profile_backend", "coreml");
        metadata.put("profile_onnx_tokenize_ms", 1.25);
        metadata.put("profile_onnx_input_prepare_ms", 2.5);
        metadata.put("profile_onnx_prefill_run_ms", 10.0);
        metadata.put("profile_onnx_decode_run_ms", 12.0);
        metadata.put("profile_onnx_ort_run_ms", 22.0);
        metadata.put("profile_onnx_logits_select_ms", 0.5);
        metadata.put("profile_onnx_sampling_ms", 0.25);
        metadata.put("profile_onnx_final_decode_ms", 0.75);
        metadata.put("profile_onnx_generation_ms", 25.25);
        metadata.put("profile_onnx_profiled_ms", 26.5);
        metadata.put("profile_onnx_unprofiled_ms", 3.0);
        metadata.put("profile_onnx_primary_stage", "ort_run");
        metadata.put("profile_onnx_primary_value_ms", 22.0);
        metadata.put("profile_onnx_primary_share_percent", 60.3);
        metadata.put("profile_onnx_prefill_steps", 1);
        metadata.put("profile_onnx_decode_steps", 4);
        metadata.put("profile_onnx_workspace", "reused requested=16 capacity=32 evictions=0");
        metadata.put("profile_onnx_input_tensor_cache",
                "hits=12 misses=3 hit_rate=80.0% evictions=1 scalar=8/2 attention=4/1 prefix_ids=0/0 position_ranges=0/0");
        metadata.put("profile_onnx_scalar_tensor_cache", "hits=8 misses=2 hit_rate=80.0%");
        metadata.put("tokens.input", 8);
        metadata.put("tokens.output", 4);
        metadata.put("tokens.decode", 4);
        metadata.put("bench.prefill_tps", 800.0);
        metadata.put("bench.decode_tps", 333.0);

        String output = captureStdout(() -> withoutProfileProperty(() -> renderer.printBenchmarks(metadata, false)));

        assertTrue(output.contains("Performance Metrics:"));
        assertTrue(output.contains("onnx profile:"));
        assertTrue(output.contains("backend       =    coreml"));
        assertTrue(output.contains("tokenize      =      1.25 ms"));
        assertTrue(output.contains("input prep    =      2.50 ms"));
        assertTrue(output.contains("prefill run   =     10.00 ms"));
        assertTrue(output.contains("decode run    =     12.00 ms"));
        assertTrue(output.contains("logits select =      0.50 ms"));
        assertTrue(output.contains("sampling      =      0.25 ms"));
        assertTrue(output.contains("final decode  =      0.75 ms"));
        assertTrue(output.contains("generation    =     25.25 ms"));
        assertTrue(output.contains("profiled      =     26.50 ms"));
        assertTrue(output.contains("unprofiled    =      3.00 ms"));
        assertTrue(output.contains("primary       = ort_run, 22.00 ms (60.3%)"));
        assertTrue(output.contains("steps         = prefill=1 decode=4"));
        assertTrue(output.contains("workspace     = reused requested=16 capacity=32 evictions=0"));
        assertTrue(output.contains(
                "input cache   = hits=12 misses=3 hit_rate=80.0% evictions=1 scalar=8/2 attention=4/1 prefix_ids=0/0 position_ranges=0/0"));
        assertTrue(output.contains("scalar cache  = hits=8 misses=2 hit_rate=80.0%"));
    }

    @Test
    void printBenchmarksIncludesDirectProfileBottleneckAdvice() {
        ChatUIRenderer.disableColor();
        ChatUIRenderer renderer = new ChatUIRenderer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profile_prefill_ms", 18_600.0);
        metadata.put("profile_engine_ttft_ms", 18_620.0);
        metadata.put("profile_attention_ms", 5_500.0);
        metadata.put("profile_ffn_ms", 11_500.0);
        metadata.put("profile_ffn_strategy", "fused_geglu_prefill_over_row_prefill");
        metadata.put("profile_logits_ms", 1_200.0);
        metadata.put("profile_bottleneck_stage", "ffn");
        metadata.put("profile_bottleneck_value_ms", 11_500.0);
        metadata.put("profile_bottleneck_share_percent", 61.8);
        metadata.put("profile_bottleneck_advice",
                "FFN prefill dominates; keep fused GEGLU BF16 enabled and prioritize a batched native FFN kernel");

        String output = captureStdout(() -> withoutProfileProperty(() -> renderer.printBenchmarks(metadata, false)));

        assertTrue(output.contains("profile:"));
        assertTrue(output.contains("ffn           =  11500.00 ms"));
        assertTrue(output.contains("ffn strategy  = fused_geglu_prefill_over_row_prefill"));
        assertTrue(output.contains("bottleneck    = ffn, 11500.00 ms (61.8%)"));
        assertTrue(output.contains(
                "profile advice = FFN prefill dominates; keep fused GEGLU BF16 enabled and prioritize a batched native FFN kernel"));
    }

    @Test
    void printBenchmarksIncludesDirectFfnRowPrefillStrategyDetails() {
        ChatUIRenderer.disableColor();
        ChatUIRenderer renderer = new ChatUIRenderer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profile_ffn_strategy", "row_prefill_matvec_active");
        metadata.put("profile_ffn_strategy_row_prefill_native_rows", 12);
        metadata.put("profile_ffn_strategy_row_prefill_variant", "x4");

        String output = captureStdout(() -> withoutProfileProperty(() -> renderer.printBenchmarks(metadata, false)));

        assertTrue(output.contains("profile:"));
        assertTrue(output.contains("ffn strategy  = row_prefill_matvec_active"));
        assertTrue(output.contains("ffn row path  = native_rows=12 variant=x4"));
    }

    private static String captureStdout(Runnable action) {
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            action.run();
        } finally {
            System.setOut(previous);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void withoutProfileProperty(Runnable action) {
        String previous = System.getProperty("aqli.profile");
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            System.clearProperty("aqli.profile");
            action.run();
        } finally {
            Locale.setDefault(previousLocale);
            if (previous != null) {
                System.setProperty("aqli.profile", previous);
            }
        }
    }
}
