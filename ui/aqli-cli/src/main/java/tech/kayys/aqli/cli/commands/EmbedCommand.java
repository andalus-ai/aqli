package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tech.kayys.aqli.cli.AqliCommand;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.spi.embedding.EmbeddingRequest;
import tech.kayys.aqli.spi.embedding.EmbeddingResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Dependent
@Unremovable
@Command(name = "embed", description = "Generate text embeddings using a specified model")
public class EmbedCommand implements Runnable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParentCommand
    AqliCommand parentCommand;
    @Inject
    AqliSdk sdk;

    @Option(names = { "-m", "--model" }, description = "Model ID to use for embeddings", required = true)
    public String modelId;

    @Option(names = { "-p", "--prompt", "--text" }, description = "Input text to embed")
    public String text;

    @Option(names = { "--input-file", "--file" }, description = "Text file to embed")
    public String inputFile;

    @Option(names = { "--json" }, description = "Print embedding response as JSON")
    public boolean json;

    @Option(names = { "-o", "--output" }, description = "Write embedding response JSON to a file")
    public String output;

    @Override
    public void run() {
        try {
            if (parentCommand != null) {
                parentCommand.bootstrapInheritedEnvironment();
            }
            List<String> inputs = resolveInputs();
            if (!json && output == null) {
                System.out.println("Generating embedding for model: " + modelId);
            }
            EmbeddingResponse response = sdk.createEmbedding(EmbeddingRequest.builder()
                    .model(modelId)
                    .inputs(inputs)
                    .build());

            if (json || output != null) {
                Map<String, Object> payload = responsePayload(response);
                String serialized = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
                if (output != null && !output.isBlank()) {
                    Path outputPath = Path.of(output);
                    Files.writeString(outputPath, serialized + System.lineSeparator());
                    if (!json) {
                        System.out.println("Embedding response written to: " + outputPath.toAbsolutePath());
                    }
                }
                if (json) {
                    System.out.println(serialized);
                }
                return;
            }

            System.out.println("Embedding Result (Count: " + response.embeddings().size() + "):");
            if (!response.embeddings().isEmpty()) {
                System.out.println("Dimension: " + response.dimension());
            }

        } catch (Exception e) {
            System.err.println("Embedding failed: " + e.getMessage());
        }
    }

    private List<String> resolveInputs() throws Exception {
        List<String> inputs = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            inputs.add(text);
        }
        if (inputFile != null && !inputFile.isBlank()) {
            inputs.add(Files.readString(Path.of(inputFile)));
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Provide --text or --input-file for embedding input.");
        }
        return inputs;
    }

    private Map<String, Object> responsePayload(EmbeddingResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", response.requestId());
        payload.put("model", response.model());
        payload.put("dimension", response.dimension());
        payload.put("metadata", response.metadata());
        payload.put("embeddings", response.embeddings());
        return payload;
    }
}
