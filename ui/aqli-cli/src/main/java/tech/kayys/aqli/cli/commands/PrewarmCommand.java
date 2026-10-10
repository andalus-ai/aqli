package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tech.kayys.aqli.cli.AqliCommand;
import tech.kayys.aqli.cli.runtime.CliMetalRuntime;
import tech.kayys.aqli.safetensor.engine.generation.DirectInferenceEngine;
import tech.kayys.aqli.safetensor.generation.GenerationConfig;
import tech.kayys.aqli.spi.inference.InferenceResponse;

@Command(name = "prewarm",
        aliases = {"warmup"},
        description = "Prewarm a local GGUF/LiteRT daemon or safetensor direct path")
@Dependent
@Unremovable
public class PrewarmCommand implements Callable<Integer> {
    @ParentCommand
    AqliCommand parentCommand;

    @Inject
    DirectInferenceEngine directInferenceEngine;

    @Option(names = {"-m", "--model"}, description = "Model short id, id, or name")
    String model;

    @Option(names = {"--model-file", "--model-path"}, description = "Explicit local model file")
    String modelFile;

    @Option(names = {"-p", "--prompt"}, description = "Prompt used to build the initial prompt cache")
    String prompt;

    @Option(names = {"--max-tokens"}, description = "Context-sizing token budget for prewarm", defaultValue = "10")
    int maxTokens;

    @Option(names = {"--provider"}, description = "Provider to prewarm (auto, gguf, litert, safetensor)", defaultValue = "auto")
    String provider = "auto";

    @Option(names = {"--engine", "--gguf-engine"}, description = "GGUF engine mode", defaultValue = "auto")
    String engine;

    @Option(names = {"--backend"}, description = "Backend to prewarm (metal or cpu). Defaults to inherited --platform when provided.")
    String backend;

    @Override
    public Integer call() {
        if (parentCommand != null) {
            parentCommand.bootstrapInheritedEnvironment();
        }
        if (isBlank(model) && isBlank(modelFile)) {
            System.err.println("Error: prewarm requires --model or --model-file");
            return 2;
        }
        List<String> args = new ArrayList<>();
        addOption(args, "--model", model);
        addOption(args, "--model-file", modelFile);
        addOption(args, "--prompt", prompt);
        addOption(args, "--max-tokens", Integer.toString(maxTokens));
        String normalizedProvider = effectiveProvider(provider, model, modelFile);
        addOption(args, "--provider", normalizedProvider);
        if (isGgufProvider(normalizedProvider)) {
            addOption(args, "--engine", engine);
        }
        addOption(args, "--backend", effectiveBackend());
        if (parentCommand != null && parentCommand.isUseCpu()) {
            args.add("--use-cpu");
        }
        if (isLiteRtProvider(normalizedProvider)) {
            return LiteRtLmFastRun.prewarm(args.toArray(String[]::new));
        }
        if (isSafetensorProvider(normalizedProvider)) {
            return prewarmSafetensor();
        }
        return GgufFastRun.prewarm(args.toArray(String[]::new));
    }

    private static boolean isLiteRtProvider(String provider) {
        return "litert".equals(provider)
                || "litertlm".equals(provider)
                || "tflite".equals(provider)
                || "task".equals(provider);
    }

    private static boolean isGgufProvider(String provider) {
        return provider == null || provider.isBlank() || "gguf".equals(provider);
    }

    private static boolean isSafetensorProvider(String provider) {
        return "safetensor".equals(provider)
                || "safetensors".equals(provider)
                || "safe-tensor".equals(provider)
                || "safe-tensors".equals(provider);
    }

    static String effectiveProvider(String requestedProvider, String model, String modelFile) {
        String normalized = requestedProvider == null ? "auto" : requestedProvider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isBlank() && !"auto".equals(normalized)) {
            return normalized;
        }
        String inferred = inferProvider(modelFile);
        if (inferred != null) {
            return inferred;
        }
        inferred = inferProvider(model);
        if (inferred != null) {
            return inferred;
        }
        if (!isBlank(model)) {
            try {
                var indexed = LocalModelIndex.find(model);
                if (indexed.isPresent()) {
                    inferred = providerForFormat(indexed.get().format);
                    if (inferred != null) {
                        return inferred;
                    }
                }
            } catch (Throwable ignored) {
                // Keep the historical GGUF default if the lightweight index is unavailable.
            }
        }
        return "gguf";
    }

    private static String inferProvider(String pathOrRef) {
        if (isBlank(pathOrRef)) {
            return null;
        }
        String normalized = pathOrRef.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".gguf")) {
            return "gguf";
        }
        if (normalized.endsWith(".safetensors")
                || normalized.endsWith(".safetensor")
                || normalized.contains("/safetensors/")) {
            return "safetensor";
        }
        if (normalized.endsWith(".litertlm") || normalized.endsWith(".task") || normalized.endsWith(".tflite")) {
            return "litert";
        }
        return null;
    }

    private static String providerForFormat(String format) {
        if (isBlank(format)) {
            return null;
        }
        return switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "gguf" -> "gguf";
            case "litert", "litertlm", "task", "tflite" -> "litert";
            case "safetensor", "safetensors", "safe-tensor", "safe-tensors" -> "safetensor";
            default -> null;
        };
    }

    private Integer prewarmSafetensor() {
        if (!prepareSafetensorAccelerator()) {
            return 1;
        }

        Path resolved = resolveSafetensorPath();
        if (resolved == null) {
            System.err.println("Error: safetensor prewarm could not resolve a local model path.");
            return 2;
        }
        if (!Files.exists(resolved)) {
            System.err.println("Error: safetensor model path does not exist: " + resolved);
            return 2;
        }

        DirectInferenceEngine engine = directEngine();
        if (engine == null) {
            System.err.println("Error: safetensor direct engine is not available in this runtime.");
            return 2;
        }

        String warmPrompt = !isBlank(prompt)
                ? prompt
                : System.getProperty("aqli.safetensor.prewarm.prompt", "where is jakarta");
        int generatedTokens = Math.max(1, Integer.getInteger("aqli.safetensor.prewarm.tokens", 1));
        int kvTokens = Math.max(64, Math.max(maxTokens, generatedTokens) + 64);
        GenerationConfig cfg = GenerationConfig.builder()
                .maxNewTokens(generatedTokens)
                .temperature(0.0f)
                .topK(1)
                .topP(1.0f)
                .repetitionPenalty(1.0f)
                .maxKvCacheTokens(kvTokens)
                .build();

        long start = System.nanoTime();
        try {
            engine.loadModel(resolved);
            InferenceResponse response = engine.generate(warmPrompt, resolved, cfg)
                    .await().atMost(Duration.ofMinutes(10));
            double totalMs = (System.nanoTime() - start) / 1_000_000.0;
            Map<String, Object> metadata = response.getMetadata();
            Object profile = metadata != null ? metadata.get("profile_summary") : null;
            System.out.printf(Locale.ROOT,
                    "Safetensor direct warmup complete: %s (%.2f ms, outputTokens=%d, totalTokens=%d, processLocal=true)%n",
                    resolved.getFileName(), totalMs, response.getOutputTokens(), response.getTokensUsed());
            if (profile != null) {
                System.out.println("Profile: " + profile);
            }
            System.out.println("Note: safetensor warmup does not start GGUF/LiteRT daemons.");
            return 0;
        } catch (Exception e) {
            System.err.println("Safetensor direct warmup failed: " + e.getMessage());
            return 1;
        }
    }

    private boolean prepareSafetensorAccelerator() {
        String selectedBackend = effectiveBackend();
        boolean useCpu = parentCommand != null && parentCommand.isUseCpu();
        if (!shouldInitializeMetalForSafetensor(selectedBackend, useCpu)) {
            return true;
        }
        CliMetalRuntime.initialize();
        if (isMetalBackend(selectedBackend)
                && !CliMetalRuntime.isNativeActive()
                && !CliMetalRuntime.allowCpuFallbackWhenMetalRequested()) {
            System.err.println("Error: Metal backend selected but native Metal runtime is not active.");
            System.err.println("Refusing CPU fallback for safetensor prewarm so performance behavior stays explicit.");
            System.err.println("Set AQLI_ALLOW_CPU_FALLBACK=true to override.");
            return false;
        }
        return true;
    }

    static boolean shouldInitializeMetalForSafetensor(String selectedBackend, boolean useCpu) {
        if (useCpu) {
            return false;
        }
        return isBlank(selectedBackend) || isMetalBackend(selectedBackend);
    }

    private static boolean isMetalBackend(String selectedBackend) {
        return selectedBackend != null
                && "metal".equals(selectedBackend.trim().toLowerCase(Locale.ROOT));
    }

    private Path resolveSafetensorPath() {
        if (!isBlank(modelFile)) {
            return Path.of(modelFile).toAbsolutePath().normalize();
        }
        if (isBlank(model)) {
            return null;
        }
        try {
            Path direct = Path.of(model).toAbsolutePath().normalize();
            if (Files.exists(direct)) {
                return direct;
            }
        } catch (Exception ignored) {
        }
        try {
            var indexed = LocalModelIndex.find(model);
            if (indexed.isPresent() && !isBlank(indexed.get().path)) {
                return Path.of(indexed.get().path).toAbsolutePath().normalize();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private DirectInferenceEngine directEngine() {
        if (directInferenceEngine != null) {
            return directInferenceEngine;
        }
        try {
            if (Arc.container() != null) {
                var instance = Arc.container().instance(DirectInferenceEngine.class);
                if (instance.isAvailable()) {
                    return instance.get();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String effectiveBackend() {
        if (!isBlank(backend)) {
            return backend;
        }
        if (parentCommand == null || isBlank(parentCommand.platform())) {
            return null;
        }
        return parentCommand.platform();
    }

    private static void addOption(List<String> args, String name, String value) {
        if (!isBlank(value)) {
            args.add(name);
            args.add(value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
