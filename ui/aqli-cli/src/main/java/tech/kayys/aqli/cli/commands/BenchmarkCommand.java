package tech.kayys.aqli.cli.commands;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import tech.kayys.aqli.cli.AqliCommand;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.LocalInferenceEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Command(name = "benchmark", description = "Benchmark a local model (Safetensor, GGUF, LiteRT)")
public class BenchmarkCommand implements Runnable {

    @ParentCommand
    AqliCommand parentCommand;

    @Inject
    Instance<LocalInferenceEngine> engines;

    @Parameters(index = "0", description = "Model path or ID")
    String modelPath;

    @Option(names = {"--provider", "-p"},
            description = "Inference provider ID (e.g. llamacpp, safetensor, litert, onnx)",
            defaultValue = "")
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
            else provider = "safetensor";
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
                .preferredProvider(selectedProvider)
                .temperature(0.2)
                .maxTokens(100)
                .message(Message.user(prompt))
                .build();

        System.out.println("== Model Inference Benchmark ==");
        System.out.println("Model    : " + modelPath);
        System.out.println("Provider : " + selectedProvider);
        System.out.println("Prompt   : " + prompt);
        System.out.println("Generating...");

        Instant started = Instant.now();
        String outputContent;
        try {
            if ("gguf".equalsIgnoreCase(provider)) {
                System.out.println("INFO: Executing Java-native GGUF backend...");
                GgufFastRun.FastArgs parsed =
                        GgufFastRun.FastArgs.parse(new String[]{"run", "--model", modelPath});
                java.util.Optional<java.nio.file.Path> resolved = GgufFastRun.resolveGgufModel(parsed);
                if (resolved.isEmpty()) {
                    throw new IllegalStateException("Could not resolve GGUF model path for: " + modelPath);
                }

                // Use the canonical Aljabr request/backend contract. The CLI benchmark
                // must not depend on the optional llama.cpp RunnerPlugin.
                tech.kayys.aljabr.spi.inference.InferenceRequest ggufRequest =
                        tech.kayys.aljabr.spi.inference.InferenceRequest.builder()
                                .model(resolved.get().getFileName().toString())
                                .prompt(prompt)
                                .message(tech.kayys.aljabr.spi.Message.user(prompt))
                                .temperature(0.2)
                                .maxTokens(100)
                                .build();
                tech.kayys.aljabr.gguf.api.GgufBackendProvider javaProvider =
                        new tech.kayys.aljabr.gguf.javabackend.JavaGgufBackendProvider();
                try (tech.kayys.aljabr.gguf.api.GgufBackend backend =
                             javaProvider.create(resolved.get(), java.util.Map.of("backend", "java"))) {
                    tech.kayys.aljabr.spi.inference.InferenceResponse response = backend.execute(ggufRequest);
                    outputContent = response != null && response.getContent() != null
                            ? response.getContent()
                            : "";
                }
            } else {
                System.out.println("DEBUG: Executing local inference provider...");
                LocalInferenceEngine selected = null;
                for (LocalInferenceEngine engine : engines) {
                    String className = engine.getClass().getName();
                    System.out.println("  - " + className);
                    if ("safetensor".equalsIgnoreCase(provider)
                            && (className.contains("SafetensorEngine")
                                    || className.contains("DirectSafetensorBackend"))) {
                        selected = engine;
                        break;
                    }
                }
                if (selected == null) {
                    selected = engines.stream().findFirst().orElse(null);
                }
                if (selected == null) {
                    throw new IllegalStateException("No local inference engine is available");
                }
                InferenceResponse response = selected.infer(request).await().indefinitely();
                if (response == null) {
                    throw new IllegalStateException("Inference engine returned null");
                }
                outputContent = response.getContent() == null ? "" : response.getContent();
            }
        } catch (Exception e) {
            System.err.println("Benchmark inference failed: " + e.getMessage());
        }
    }

    private String resolveProvider() {
        if (provider != null && !provider.isBlank()) {
            String requested = provider.trim();
            // Keep the CLI's historical format alias while routing by the real SPI provider ID.
            return "gguf".equalsIgnoreCase(requested) ? "llamacpp" : requested;
        }

        long elapsedMs = Math.max(1L, Duration.between(started, Instant.now()).toMillis());
        System.out.println("\nResult:\n" + outputContent);
        System.out.println("--------------------------------------------------");
        System.out.println("Metrics:");

        int outputTokens = outputContent.isBlank() ? 0 : outputContent.trim().split("\\s+").length;
        double tokensPerSec = (outputTokens / (double) elapsedMs) * 1000.0;

        System.out.println("Total Time : " + elapsedMs + " ms");
        System.out.printf("Throughput : %.2f tokens / sec%n", tokensPerSec);
        System.out.println("Provider   : " + provider);
        System.out.println("--------------------------------------------------");
    }
}
