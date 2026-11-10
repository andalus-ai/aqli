package tech.kayys.aqli.cli.chat;

import jakarta.enterprise.context.Dependent;
import java.util.List;
import java.util.Map;

/**
 * Handles terminal output rendering and colors.
 * Respects the NO_COLOR env var (https://no-color.org) and --no-color flag.
 */
@Dependent
public class ChatUIRenderer {

    // Populated at init time; empty strings when color is disabled.
    public static String RESET = "\u001B[0m";
    public static String BOLD = "\u001B[1m";
    public static String YELLOW = "\u001B[33m";
    public static String CYAN = "\u001B[36m";
    public static String GREEN = "\u001B[32m";
    public static String RED = "\u001B[31m";
    public static String DIM = "\u001B[2m";

    static {
        if (System.getenv("NO_COLOR") != null)
            disableColor();
    }

    public static void disableColor() {
        RESET = BOLD = YELLOW = CYAN = GREEN = RED = DIM = "";
    }

    private boolean jsonMode = false;

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

    public void printBanner() {
        if (jsonMode)
            return;
        System.out.println(BOLD + YELLOW + "  _____       _  _      _    " + RESET);
        System.out.println(BOLD + YELLOW + " / ____|     | || |    | |   " + RESET);
        System.out.println(BOLD + YELLOW + "| |  __  ___ | || | ___| | __" + RESET);
        System.out.println(BOLD + YELLOW + "| | |_ |/ _ \\| || |/ _ \\ |/ /" + RESET);
        System.out.println(BOLD + YELLOW + "| |__| | (_) | || |  __/   < " + RESET);
        System.out.println(BOLD + YELLOW + " \\_____|\\___/|_||_|\\___|_|\\_\\" + RESET);
        System.out.println();
    }

    public void printModelInfo(String modelId, String providerId, String format, String outputFile,
            boolean isInteractive) {
        if (jsonMode)
            return;
        System.out.printf(BOLD + "Model: " + RESET + CYAN + "%s" + RESET + "%n", modelId);
        String provStr = providerId != null ? providerId : "auto-select";
        if (format != null && !format.isBlank()) {
            System.out.printf(BOLD + "Provider: " + RESET + YELLOW + "%s, format=%s" + RESET + "%n", provStr,
                    format.toLowerCase());
        } else {
            System.out.printf(BOLD + "Provider: " + RESET + YELLOW + "%s" + RESET + "%n", provStr);
        }
        if (outputFile != null) {
            System.out.printf(BOLD + "Output: " + RESET + YELLOW + "%s" + RESET + "%n", outputFile);
        }
        if (isInteractive) {
            System.out.println(
                    DIM + "Commands: 'exit' to quit, '/reset' to clear history, '/retry' to rerun the last request."
                            + RESET);
        }
        System.out.println(DIM + "-".repeat(50) + RESET);
    }

    public void printAssistantPrefix(boolean quiet, boolean streaming) {
        if (jsonMode)
            return;
        if (!quiet) {
            if (!streaming)
                System.out.print("\n");
            System.out.print(BOLD + GREEN + "Assistant: " + RESET);
            System.out.flush();
        }
    }

    public void printStats(int tokens, double duration, double tps, boolean quiet) {
        printStats(tokens, duration, tps, null, quiet);
    }

    public void printStats(int tokens, double duration, double tps, Double ttftMs, boolean quiet) {
        if (jsonMode)
            return;
        if (!quiet) {
            if (ttftMs != null) {
                System.out.printf(DIM + "\n[Stream updates: %d, Duration: %.2fs, Speed: %.2f t/s, TTFT: %.2f ms]"
                                + RESET + "%n",
                        tokens, duration, tps, ttftMs);
            } else {
                System.out.printf(DIM + "\n[Stream updates: %d, Duration: %.2fs, Speed: %.2f t/s]" + RESET + "%n",
                        tokens, duration, tps);
            }
        }
    }

    public void printBenchmarks(Map<String, Object> metadata, boolean quiet) {
        if (jsonMode || quiet || metadata == null || metadata.isEmpty())
            return;

        System.out.println(DIM + "Performance Metrics:" + RESET);

        Double loadMs = metaDouble(metadata, "bench.load_ms");
        Double prefillTps = metaDouble(metadata, "bench.prefill_tps");
        Double prefillMsPerToken = metaDouble(metadata, "bench.prefill_ms_per_token");
        Double genTps = metaDouble(metadata, "bench.generation_tps");
        Double decodeTps = metaDouble(metadata, "bench.decode_tps");
        Double ttftMs = metaDouble(metadata, "bench.ttft_ms");
        Double engineTtftMs = metaDouble(metadata, "bench.engine_ttft_ms");
        Double tpotMs = metaDouble(metadata, "bench.tpot_ms");
        Integer inputTokens = metaInt(metadata, "tokens.input");
        Integer outputTokens = metaInt(metadata, "tokens.output");
        Integer decodeTokens = metaInt(metadata, "tokens.decode");

        if (loadMs != null)
            System.out.printf(DIM + "  load time      = %9.2f ms" + RESET + "%n", loadMs);
        if (prefillTps != null && inputTokens != null) {
            if (prefillMsPerToken != null) {
                System.out.printf(DIM + "  prompt eval    = %9.2f t/s (%d tokens, %.2f ms/tok)" + RESET + "%n",
                        prefillTps, inputTokens, prefillMsPerToken);
            } else {
                System.out.printf(DIM + "  prompt eval    = %9.2f t/s (%d tokens)" + RESET + "%n", prefillTps, inputTokens);
            }
        }
        if (genTps != null && outputTokens != null) {
            System.out.printf(DIM + "  generation     = %9.2f t/s (%d tokens)" + RESET + "%n", genTps, outputTokens);
        }
        if (decodeTps != null && decodeTokens != null) {
            System.out.printf(DIM + "  decode         = %9.2f t/s (%d steps)" + RESET + "%n", decodeTps, decodeTokens);
        }
        if (ttftMs != null)
            System.out.printf(DIM + "  latency (ttft) = %9.2f ms" + RESET + "%n", ttftMs);
        if (engineTtftMs != null && ttftMs != null && Math.abs(ttftMs - engineTtftMs) > 0.5d) {
            System.out.printf(DIM + "  engine ttft    = %9.2f ms (model-ready)" + RESET + "%n", engineTtftMs);
        }
        if (tpotMs != null)
            System.out.printf(DIM + "  token latency  = %9.2f ms/token" + RESET + "%n", tpotMs);
        if (Boolean.getBoolean("aqli.profile") || hasProfileBreakdown(metadata)) {
            printProfileBreakdown(metadata);
        } else {
            printHostProfile(metadata);
        }
        System.out.println();
    }

    private void printProfileBreakdown(Map<String, Object> metadata) {
        if (isOnnxProfile(metadata)) {
            printOnnxProfileBreakdown(metadata);
            printHostProfile(metadata);
            return;
        }

        Double prefillMs = metaDouble(metadata, "profile_prefill_ms");
        Double decodeMs = metaDouble(metadata, "profile_decode_ms");
        Double samplingMs = metaDouble(metadata, "profile_sampling_ms");
        Double engineTtftMs = metaDouble(metadata, "profile_engine_ttft_ms");
        Double engineExcludedMs = metaDouble(metadata, "profile_engine_ttft_excluded_ms");
        Double attentionMs = metaDouble(metadata, "profile_attention_ms");
        Double ffnMs = metaDouble(metadata, "profile_ffn_ms");
        String ffnStrategy = metaString(metadata, "profile_ffn_strategy");
        Integer ffnRowPrefillNativeRows = metaInt(metadata, "profile_ffn_strategy_row_prefill_native_rows");
        String ffnRowPrefillVariant = metaString(metadata, "profile_ffn_strategy_row_prefill_variant");
        Double logitsMs = metaDouble(metadata, "profile_logits_ms");
        Double logitsCopyMs = metaDouble(metadata, "profile_logits_materialization_ms");
        String bottleneckStage = metaString(metadata, "profile_bottleneck_stage");
        Double bottleneckValueMs = metaDouble(metadata, "profile_bottleneck_value_ms");
        Double bottleneckSharePercent = metaDouble(metadata, "profile_bottleneck_share_percent");
        String bottleneckAdvice = metaString(metadata, "profile_bottleneck_advice");

        boolean hasBreakdown = prefillMs != null || decodeMs != null || samplingMs != null
                || attentionMs != null || ffnMs != null || ffnStrategy != null
                || ffnRowPrefillNativeRows != null || ffnRowPrefillVariant != null
                || logitsMs != null || logitsCopyMs != null;
        if (!hasBreakdown) {
            return;
        }

        System.out.println(DIM + "  profile:" + RESET);
        if (prefillMs != null)
            System.out.printf(DIM + "    prefill       = %9.2f ms" + RESET + "%n", prefillMs);
        if (decodeMs != null)
            System.out.printf(DIM + "    decode        = %9.2f ms" + RESET + "%n", decodeMs);
        if (samplingMs != null)
            System.out.printf(DIM + "    sampling      = %9.2f ms" + RESET + "%n", samplingMs);
        if (engineTtftMs != null) {
            if (engineExcludedMs != null && engineExcludedMs > 0.5d) {
                System.out.printf(DIM + "    engine ttft   = %9.2f ms (excluded cold setup %.2f ms)" + RESET + "%n",
                        engineTtftMs, engineExcludedMs);
            } else {
                System.out.printf(DIM + "    engine ttft   = %9.2f ms" + RESET + "%n", engineTtftMs);
            }
        }
        if (attentionMs != null)
            System.out.printf(DIM + "    attention     = %9.2f ms" + RESET + "%n", attentionMs);
        if (ffnMs != null)
            System.out.printf(DIM + "    ffn           = %9.2f ms" + RESET + "%n", ffnMs);
        if (ffnStrategy != null && !ffnStrategy.isBlank())
            System.out.printf(DIM + "    ffn strategy  = %9s" + RESET + "%n", ffnStrategy);
        if (ffnRowPrefillNativeRows != null || (ffnRowPrefillVariant != null && !ffnRowPrefillVariant.isBlank()))
            System.out.printf(DIM + "    ffn row path  = %9s" + RESET + "%n",
                    ffnRowPrefillSummary(ffnRowPrefillNativeRows, ffnRowPrefillVariant));
        if (logitsMs != null)
            System.out.printf(DIM + "    logits        = %9.2f ms" + RESET + "%n", logitsMs);
        if (logitsCopyMs != null)
            System.out.printf(DIM + "    logits copy   = %9.2f ms" + RESET + "%n", logitsCopyMs);
        if (bottleneckStage != null && !bottleneckStage.isBlank() && bottleneckValueMs != null) {
            if (bottleneckSharePercent != null) {
                System.out.printf(DIM + "    bottleneck    = %s, %.2f ms (%.1f%%)" + RESET + "%n",
                        bottleneckStage, bottleneckValueMs, bottleneckSharePercent);
            } else {
                System.out.printf(DIM + "    bottleneck    = %s, %.2f ms" + RESET + "%n",
                        bottleneckStage, bottleneckValueMs);
            }
        }
        if (bottleneckAdvice != null && !bottleneckAdvice.isBlank()) {
            System.out.println(YELLOW + "    profile advice = " + bottleneckAdvice + RESET);
        }
        printHostProfile(metadata);
    }

    private void printOnnxProfileBreakdown(Map<String, Object> metadata) {
        Double tokenizeMs = metaDouble(metadata, "profile_onnx_tokenize_ms");
        Double inputPrepareMs = metaDouble(metadata, "profile_onnx_input_prepare_ms");
        Double prefillRunMs = metaDouble(metadata, "profile_onnx_prefill_run_ms");
        Double decodeRunMs = metaDouble(metadata, "profile_onnx_decode_run_ms");
        Double ortRunMs = metaDouble(metadata, "profile_onnx_ort_run_ms");
        Double logitsSelectMs = metaDouble(metadata, "profile_onnx_logits_select_ms");
        Double samplingMs = metaDouble(metadata, "profile_onnx_sampling_ms");
        Double finalDecodeMs = metaDouble(metadata, "profile_onnx_final_decode_ms");
        Double generationMs = metaDouble(metadata, "profile_onnx_generation_ms");
        Double profiledMs = metaDouble(metadata, "profile_onnx_profiled_ms");
        Double unprofiledMs = metaDouble(metadata, "profile_onnx_unprofiled_ms");
        String primaryStage = metaString(metadata, "profile_onnx_primary_stage");
        Double primaryValueMs = metaDouble(metadata, "profile_onnx_primary_value_ms");
        Double primarySharePercent = metaDouble(metadata, "profile_onnx_primary_share_percent");
        Integer prefillSteps = metaInt(metadata, "profile_onnx_prefill_steps");
        Integer decodeSteps = metaInt(metadata, "profile_onnx_decode_steps");
        String backend = metaString(metadata, "profile_backend");
        String workspace = metaString(metadata, "profile_onnx_workspace");
        String inputTensorCache = metaString(metadata, "profile_onnx_input_tensor_cache");
        String scalarTensorCache = metaString(metadata, "profile_onnx_scalar_tensor_cache");

        boolean hasBreakdown = tokenizeMs != null || inputPrepareMs != null || prefillRunMs != null
                || decodeRunMs != null || ortRunMs != null || logitsSelectMs != null || samplingMs != null
                || finalDecodeMs != null;
        if (!hasBreakdown) {
            return;
        }

        System.out.println(DIM + "  onnx profile:" + RESET);
        if (backend != null && !backend.isBlank()) {
            System.out.printf(DIM + "    backend       = %9s" + RESET + "%n", backend);
        }
        if (tokenizeMs != null)
            System.out.printf(DIM + "    tokenize      = %9.2f ms" + RESET + "%n", tokenizeMs);
        if (inputPrepareMs != null)
            System.out.printf(DIM + "    input prep    = %9.2f ms" + RESET + "%n", inputPrepareMs);
        if (prefillRunMs != null)
            System.out.printf(DIM + "    prefill run   = %9.2f ms" + RESET + "%n", prefillRunMs);
        if (decodeRunMs != null)
            System.out.printf(DIM + "    decode run    = %9.2f ms" + RESET + "%n", decodeRunMs);
        if (ortRunMs != null)
            System.out.printf(DIM + "    ort run       = %9.2f ms" + RESET + "%n", ortRunMs);
        if (logitsSelectMs != null)
            System.out.printf(DIM + "    logits select = %9.2f ms" + RESET + "%n", logitsSelectMs);
        if (samplingMs != null)
            System.out.printf(DIM + "    sampling      = %9.2f ms" + RESET + "%n", samplingMs);
        if (finalDecodeMs != null)
            System.out.printf(DIM + "    final decode  = %9.2f ms" + RESET + "%n", finalDecodeMs);
        if (generationMs != null)
            System.out.printf(DIM + "    generation    = %9.2f ms" + RESET + "%n", generationMs);
        if (profiledMs != null)
            System.out.printf(DIM + "    profiled      = %9.2f ms" + RESET + "%n", profiledMs);
        if (unprofiledMs != null && unprofiledMs > 0.5d)
            System.out.printf(DIM + "    unprofiled    = %9.2f ms" + RESET + "%n", unprofiledMs);
        if (primaryStage != null && !primaryStage.isBlank() && primaryValueMs != null) {
            if (primarySharePercent != null) {
                System.out.printf(DIM + "    primary       = %s, %.2f ms (%.1f%%)" + RESET + "%n",
                        primaryStage, primaryValueMs, primarySharePercent);
            } else {
                System.out.printf(DIM + "    primary       = %s, %.2f ms" + RESET + "%n",
                        primaryStage, primaryValueMs);
            }
        }
        if (prefillSteps != null || decodeSteps != null) {
            System.out.printf(DIM + "    steps         = %9s" + RESET + "%n",
                    profileStepSummary(prefillSteps, decodeSteps));
        }
        if (workspace != null && !workspace.isBlank()) {
            System.out.printf(DIM + "    workspace     = %9s" + RESET + "%n", workspace);
        }
        if (inputTensorCache != null && !inputTensorCache.isBlank()) {
            System.out.printf(DIM + "    input cache   = %9s" + RESET + "%n", inputTensorCache);
        }
        if (scalarTensorCache != null && !scalarTensorCache.isBlank()) {
            System.out.printf(DIM + "    scalar cache  = %9s" + RESET + "%n", scalarTensorCache);
        }
    }

    private static boolean isOnnxProfile(Map<String, Object> metadata) {
        return "onnx".equals(metaString(metadata, "profile_mode"))
                || metadata.containsKey("profile_onnx_ort_run_ms")
                || metadata.containsKey("profile_onnx_prefill_run_ms")
                || metadata.containsKey("profile_onnx_decode_run_ms");
    }

    private static boolean hasProfileBreakdown(Map<String, Object> metadata) {
        return isOnnxProfile(metadata)
                || metadata.containsKey("profile_prefill_ms")
                || metadata.containsKey("profile_decode_ms")
                || metadata.containsKey("profile_sampling_ms")
                || metadata.containsKey("profile_attention_ms")
                || metadata.containsKey("profile_ffn_ms")
                || metadata.containsKey("profile_ffn_strategy")
                || metadata.containsKey("profile_ffn_strategy_row_prefill_native_rows")
                || metadata.containsKey("profile_ffn_strategy_row_prefill_variant")
                || metadata.containsKey("profile_logits_ms")
                || metadata.containsKey("profile_logits_materialization_ms")
                || metadata.containsKey("profile_bottleneck_stage");
    }

    private static String ffnRowPrefillSummary(Integer nativeRows, String variant) {
        StringBuilder summary = new StringBuilder();
        if (nativeRows != null) {
            summary.append("native_rows=").append(Math.max(0, nativeRows));
        }
        if (variant != null && !variant.isBlank()) {
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append("variant=").append(variant);
        }
        return summary.toString();
    }

    private static String profileStepSummary(Integer prefillSteps, Integer decodeSteps) {
        int prefill = prefillSteps == null ? 0 : Math.max(0, prefillSteps);
        int decode = decodeSteps == null ? 0 : Math.max(0, decodeSteps);
        return String.format(java.util.Locale.ROOT, "prefill=%d decode=%d", prefill, decode);
    }

    private void printHostProfile(Map<String, Object> metadata) {
        String pressure = metaString(metadata, "host.pressure");
        Integer processors = metaInt(metadata, "host.available_processors");
        Double loadStart = metaDouble(metadata, "host.load_average_per_cpu_start");
        Double loadEnd = metaDouble(metadata, "host.load_average_per_cpu_end");
        Double cpuLoad = metaDouble(metadata, "host.cpu_load_percent");
        Double processCpuLoad = metaDouble(metadata, "host.process_cpu_load_percent");
        Double memoryFree = metaDouble(metadata, "host.memory_free_percent");
        Boolean pressureWarning = metaBoolean(metadata, "host.pressure_warning");
        if (pressure == null && loadEnd == null && cpuLoad == null) {
            return;
        }

        System.out.printf(DIM + "    host load     = %9s" + RESET + "%n",
                formatHostLoad(processors, loadStart, loadEnd, cpuLoad, processCpuLoad, memoryFree, pressure));
        if (Boolean.TRUE.equals(pressureWarning)) {
            System.out.println(YELLOW
                    + "    benchmark note = host pressure is " + pressure
                    + "; timings may be inflated by other local workloads"
                    + RESET);
            String advice = benchmarkAdvice(loadEnd, cpuLoad, memoryFree);
            if (advice != null) {
                System.out.println(YELLOW + "    benchmark advice = " + advice + RESET);
            }
        }
    }

    private static Double metaDouble(Map<String, Object> metadata, String key) {
        Object v = metadata.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer metaInt(Map<String, Object> metadata, String key) {
        Object v = metadata.get(key);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static Boolean metaBoolean(Map<String, Object> metadata, String key) {
        Object v = metadata.get(key);
        return v instanceof Boolean b ? b : null;
    }

    private static String metaString(Map<String, Object> metadata, String key) {
        Object v = metadata.get(key);
        return v instanceof String s ? s : null;
    }

    private static String formatHostLoad(Integer processors,
                                         Double loadStart,
                                         Double loadEnd,
                                         Double cpuLoad,
                                         Double processCpuLoad,
                                         Double memoryFree,
                                         String pressure) {
        StringBuilder sb = new StringBuilder();
        if (loadStart != null || loadEnd != null) {
            sb.append(String.format(java.util.Locale.ROOT, "%.2f -> %.2f xCPU",
                    loadStart != null ? loadStart : 0.0,
                    loadEnd != null ? loadEnd : 0.0));
            if (processors != null && processors > 0) {
                sb.append(" (").append(processors).append(" cores)");
            }
        }
        if (cpuLoad != null) {
            appendHostPart(sb, String.format(java.util.Locale.ROOT, "cpu %.0f%%", cpuLoad));
        }
        if (processCpuLoad != null) {
            appendHostPart(sb, String.format(java.util.Locale.ROOT, "proc %.0f%%", processCpuLoad));
        }
        if (memoryFree != null) {
            appendHostPart(sb, String.format(java.util.Locale.ROOT, "mem free %.0f%%", memoryFree));
        }
        if (pressure != null) {
            appendHostPart(sb, "pressure " + pressure);
        }
        return sb.length() == 0 ? "unavailable" : sb.toString();
    }

    private static void appendHostPart(StringBuilder sb, String value) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(value);
    }

    private static String benchmarkAdvice(Double loadEnd, Double cpuLoad, Double memoryFree) {
        if (memoryFree != null && memoryFree <= 10.0d) {
            return "free memory or wait for swap pressure to settle before comparing t/s";
        }
        if ((loadEnd != null && loadEnd >= 1.25d) || (cpuLoad != null && cpuLoad >= 90.0d)) {
            return "wait for background CPU work to finish before comparing t/s";
        }
        if ((loadEnd != null && loadEnd >= 0.85d) || (cpuLoad != null && cpuLoad >= 70.0d)) {
            return "rerun once background load calms down for a fairer speed sample";
        }
        return "rerun after host pressure returns to normal for a fairer speed sample";
    }

    public String getPrompt(boolean quiet) {
        return quiet ? "\n>>> " : "\n" + BOLD + CYAN + ">>> " + RESET;
    }

    public String getSecondaryPrompt(boolean quiet) {
        return quiet ? "... " : DIM + "... " + RESET;
    }

    public void printGoodbye(boolean quiet) {
        if (!quiet)
            System.out.println("\n" + YELLOW + "Goodbye!" + RESET);
    }

    public void printError(String message, boolean quiet) {
        System.err.println("\n" + RED + "Error: " + RESET + message);
    }

    public void printWarning(String message, boolean quiet) {
        if (!quiet)
            System.out.println(YELLOW + "Warning: " + RESET + message);
    }

    public void printInfo(String message, boolean quiet) {
        if (!quiet)
            System.out.println(DIM + message + RESET);
    }

    public void printSuccess(String message, boolean quiet) {
        if (!quiet)
            System.out.println(GREEN + message + RESET);
    }

    public void printHardwareInfo(String hardware, boolean quiet) {
        if (!quiet) {
            System.out.printf(DIM + "[Running on %s]" + RESET + "%n", hardware);
        }
    }
}
