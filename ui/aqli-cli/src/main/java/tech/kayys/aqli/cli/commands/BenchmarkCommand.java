package tech.kayys.aqli.cli.commands;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import tech.kayys.aqli.cli.AqliCommand;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.spi.Message;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;

@Command(name = "benchmark", description = "Benchmark a local model through the selected inference provider")
public class BenchmarkCommand implements Runnable {

    @ParentCommand
    AqliCommand parentCommand;

    @Inject
    AqliSdk sdk;

    @Parameters(index = "0", description = "Model path or ID")
    String modelPath;

    @Option(names = {"--provider", "-p"},
            description = "Inference provider ID (e.g. llamacpp, safetensor, litert, onnx)",
            defaultValue = "")
    String provider;

    @Override
    public void run() {
        String selectedProvider = resolveProvider();
        if (!sdk.listAvailableProviders().stream().anyMatch(
                available -> available.equalsIgnoreCase(selectedProvider))) {
            System.err.println("Inference provider is not available: " + selectedProvider);
            System.err.println("Available providers: " + sdk.listAvailableProviders());
            return;
        }

        String prompt = "Who are you and what can you do?";
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
        try {
            InferenceResponse response = sdk.createCompletion(request);
            long elapsedMs = Math.max(1L, Duration.between(started, Instant.now()).toMillis());
            String output = response.getContent() == null ? "" : response.getContent();
            int outputTokens = output.isBlank() ? 0 : output.trim().split("\\s+").length;
            double tokensPerSecond = outputTokens * 1000.0 / elapsedMs;

            System.out.println("\nResult:\n" + output);
            System.out.println("--------------------------------------------------");
            System.out.println("Total Time : " + elapsedMs + " ms");
            System.out.printf("Throughput : %.2f tokens / sec%n", tokensPerSecond);
            System.out.println("Provider   : " + selectedProvider);
            System.out.println("--------------------------------------------------");
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

        String lower = modelPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gguf")) {
            return "llamacpp";
        }
        if (lower.endsWith(".safetensors") || lower.endsWith(".safetensor")) {
            return "safetensor";
        }
        if (lower.endsWith(".litertlm") || lower.endsWith(".task") || lower.endsWith(".tflite")) {
            return "litert";
        }
        if (lower.endsWith(".onnx")) {
            return "onnx";
        }
        throw new IllegalArgumentException("Cannot infer provider from model path: " + modelPath
                + ". Specify --provider explicitly.");
    }
}
