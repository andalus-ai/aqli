package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.spi.batch.BatchInferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Run batch inference from a JSON request configuration file.
 *
 * Usage:
 *   aqli batch --file requests.json
 *   aqli batch --file requests.json --output results.json
 */
@Dependent
@Unremovable
@Command(name = "batch", description = "Run batch inference from a given request configuration")
public class BatchCommand implements Runnable {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";

    @Inject
    AqliSdk sdk;

    @Inject
    ObjectMapper objectMapper;

    @Option(names = { "-f", "--file" }, description = "JSON file containing BatchInferenceRequest", required = true)
    public String filename;

    @Option(names = { "-o", "--output" }, description = "Output file for results (JSON format)")
    public String outputFile;

    @Override
    public void run() {
        try {
            Path path = Path.of(filename);
            if (!Files.exists(path)) {
                System.err.println("Error: File not found: " + filename);
                return;
            }

            System.out.println(BOLD + "Aqli Batch Inference" + RESET);
            System.out.println(DIM + "-".repeat(50) + RESET);
            System.out.printf(BOLD + "Input:  " + RESET + "%s%n", path.toAbsolutePath());

            // Parse JSON request
            long startTime = System.currentTimeMillis();
            String json = Files.readString(path);
            BatchInferenceRequest request = objectMapper.readValue(json, BatchInferenceRequest.class);

            System.out.printf(BOLD + "Items:  " + RESET + CYAN + "%d" + RESET + "%n",
                    request.getRequests() != null ? request.getRequests().size() : 0);
            System.out.println();

            // Execute batch
            System.out.println("Processing batch...");
            List<InferenceResponse> responses = sdk.batchInference(request);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println();
            System.out.printf(GREEN + "✓ Batch processing completed" + RESET + "%n");
            System.out.printf("  Processed: %d items%n", responses.size());
            System.out.printf("  Duration:  %.2fs%n", duration / 1000.0);

            // Output results
            if (outputFile != null && !outputFile.isBlank()) {
                Path outPath = Path.of(outputFile);
                if (outPath.getParent() != null) {
                    Files.createDirectories(outPath.getParent());
                }
                String resultJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(responses);
                Files.writeString(outPath, resultJson);
                System.out.printf("  Output:    %s%n", outPath.toAbsolutePath());
            } else {
                // Print summary of each response
                for (int i = 0; i < responses.size(); i++) {
                    InferenceResponse resp = responses.get(i);
                    String content = resp.getContent();
                    String preview = content != null && content.length() > 80
                            ? content.substring(0, 80) + "..."
                            : (content != null ? content : "(empty)");
                    System.out.printf("  [%d] %s%n", i + 1, preview);
                }
            }

        } catch (Exception e) {
            System.err.println(YELLOW + "Batch inference failed: " + RESET + e.getMessage());
        }
    }
}
