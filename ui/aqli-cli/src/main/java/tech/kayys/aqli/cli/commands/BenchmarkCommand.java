package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import tech.kayys.aqli.cli.AqliCommand;
import picocli.CommandLine.ParentCommand;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Command(name = "benchmark", description = "Benchmark a local model (Safetensor, GGUF, LiteRT)")
public class BenchmarkCommand implements Runnable {

    @ParentCommand
    AqliCommand parentCommand;

    @Inject
    jakarta.enterprise.inject.Instance<tech.kayys.aqli.spi.inference.LocalInferenceEngine> engines;

    @Parameters(index = "0", description = "Model path or ID")
    String modelPath;

    @Option(names = {"--provider", "-p"}, description = "Provider (gguf, safetensor, litert, onnx)", defaultValue = "")
    String provider;

    @Override
    public void run() {
        System.out.println("== Seamless Model Orchestration Benchmark ==");
        System.out.println("Target Model Path: " + modelPath);

        if (provider == null || provider.isBlank()) {
            String lower = modelPath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gguf")) provider = "gguf";
            else if (lower.endsWith(".safetensors") || lower.endsWith(".st")) provider = "safetensor";
            else if (lower.endsWith(".tflite") || lower.endsWith(".task")) provider = "litert";
            else if (lower.endsWith(".onnx")) provider = "onnx";
            else provider = "safetensor"; // Default
            System.out.println("Auto-detected provider: " + provider);
        } else {
            System.out.println("Manually selected provider: " + provider);
        }
        
        String prompt = "Who are you and what can you do?";
        System.out.println("\nPrompt: " + prompt);
        System.out.println("Generating...");
        
        InferenceRequest request = InferenceRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .model(modelPath)
                .parameter("model_path", modelPath)
                .preferredProvider(provider)
                .temperature(0.2)
                .maxTokens(100)
                .message(tech.kayys.aqli.spi.Message.user(prompt))
                .build();

        Instant started = Instant.now();
        String outputContent = "";
        try {
            if ("gguf".equalsIgnoreCase(provider)) {
                System.out.println("INFO: Executing Java-native GGUF backend...");
                tech.kayys.aqli.cli.commands.GgufFastRun.FastArgs parsed =
                        tech.kayys.aqli.cli.commands.GgufFastRun.FastArgs.parse(
                                new String[]{"run", "--model", modelPath});
                java.util.Optional<java.nio.file.Path> resolved =
                        tech.kayys.aqli.cli.commands.GgufFastRun.resolveGgufModel(parsed);
                if (resolved.isEmpty()) {
                    throw new RuntimeException("Could not resolve GGUF model path for: " + modelPath);
                }

                // Route through LlamaCppRunnerPlugin with explicit java backend selection.
                tech.kayys.aqli.plugin.runner.llamacpp.LlamaCppRunnerPlugin plugin =
                        new tech.kayys.aqli.plugin.runner.llamacpp.LlamaCppRunnerPlugin();
                tech.kayys.aqli.plugin.runner.RunnerContext ctx =
                        tech.kayys.aqli.plugin.runner.RunnerContext.builder()
                                .metadata(java.util.Map.of("gguf.backend", "java"))
                                .build();
                plugin.initialize(ctx);

                tech.kayys.aqli.plugin.runner.ModelLoadRequest loadRequest =
                        new tech.kayys.aqli.plugin.runner.ModelLoadRequest(
                                resolved.get().toString(),
                                "gguf",
                                tech.kayys.aqli.plugin.runner.RunnerConfig.defaultConfig(),
                                java.util.Map.of("gguf.backend", "java"));
                tech.kayys.aqli.plugin.runner.ModelHandle handle = plugin.loadModel(loadRequest, ctx);

                tech.kayys.aqli.plugin.runner.RunnerRequest rr =
                        tech.kayys.aqli.plugin.runner.RunnerRequest.builder()
                                .type(tech.kayys.aqli.plugin.runner.RequestType.INFER)
                                .inferenceRequest(request)
                                .build();
                tech.kayys.aqli.plugin.runner.RunnerResult<?> result = plugin.execute(rr, ctx);
                plugin.unloadModel(handle, ctx);
                plugin.shutdown();

                if (result.isSuccess()) {
                    Object data = result.getData();
                    if (data instanceof tech.kayys.aqli.spi.inference.InferenceResponse resp) {
                        outputContent = resp.getContent() != null ? resp.getContent() : "";
                    } else if (data != null) {
                        outputContent = data.toString();
                    }
                } else {
                    throw new RuntimeException(result.getErrorMessage().orElse("GGUF execution failed"));
                }
            } else {
                System.out.println("DEBUG: Executing Safetensor via LocalInferenceEngine...");
                tech.kayys.aqli.spi.inference.LocalInferenceEngine safetensorEngine = null;
                System.out.println("DEBUG: Available engines in Instance<LocalInferenceEngine>:");
                for (tech.kayys.aqli.spi.inference.LocalInferenceEngine engine : engines) {
                    String className = engine.getClass().getName();
                    System.out.println("  - " + className);
                    if (className.contains("SafetensorEngine") || className.contains("DirectSafetensorBackend")) {
                        safetensorEngine = engine;
                    }
                }
                if (safetensorEngine == null) {
                    System.out.println("DEBUG: SafetensorEngine not found, falling back to first available engine");
                    safetensorEngine = engines.iterator().next();
                }
                InferenceResponse response = safetensorEngine.infer(request).await().indefinitely();
                if (response != null) {
                    outputContent = response.getContent();
                } else {
                    throw new RuntimeException("Safetensor engine returned null");
                }
            }
        } catch (Exception e) {
            System.err.println("Error running benchmark: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        long elapsedMs = Math.max(1L, Duration.between(started, Instant.now()).toMillis());

        System.out.println("\nResult:\n" + outputContent);
        System.out.println("--------------------------------------------------");
        System.out.println("Metrics:");
        
        int outputTokens = outputContent.split("\\s+").length; // Rough fallback
        double tokensPerSec = (outputTokens / (double) elapsedMs) * 1000.0;
        
        System.out.println("Total Time : " + elapsedMs + " ms");
        System.out.printf("Throughput : %.2f tokens / sec\n", tokensPerSec);
        System.out.println("Provider   : " + provider);
        System.out.println("--------------------------------------------------");
    }
}
