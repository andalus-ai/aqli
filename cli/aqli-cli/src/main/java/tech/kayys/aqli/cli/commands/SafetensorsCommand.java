package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.kayys.aqli.sdk.util.AqliHome;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Dependent
@Unremovable
@Command(name = "safetensors", mixinStandardHelpOptions = true, description = "Inspect safetensors metadata")
public class SafetensorsCommand implements Runnable {
    private static final long MAX_HEADER_BYTES = 256L * 1024L * 1024L;

    @Option(names = { "--path" }, description = "Path to .safetensors file")
    String path;

    @Option(names = {
            "--model" }, description = "Model ID under ~/.aqli/models/safetensors or legacy libtorchscript")
    String modelId;

    @Option(names = { "--limit" }, description = "Maximum tensors to print", defaultValue = "30")
    int limit;

    @Override
    public void run() {
        try {
            Path file = resolveInputPath();
            if (file == null) {
                System.err.println("Error: provide either --path or --model.");
                return;
            }
            if (!Files.exists(file)) {
                System.err.println("Error: safetensors file not found: " + file);
                return;
            }

            Map<String, TensorMetadata> metadata = parse(file);

            long totalBytes = metadata.values().stream().mapToLong(TensorMetadata::length).sum();
            System.out.println("File: " + file);
            System.out.println("Tensors: " + metadata.size());
            System.out.printf("Payload: %.2f MB%n", totalBytes / (1024.0 * 1024.0));

            int toPrint = Math.max(0, Math.min(limit, metadata.size()));
            if (toPrint == 0) {
                return;
            }

            System.out.println("----");
            metadata.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .limit(toPrint)
                    .forEach(entry -> {
                        TensorMetadata info = entry.getValue();
                        System.out.printf("%s | dtype=%s | shape=%s | bytes=%d%n",
                                entry.getKey(),
                                info.dtype(),
                                java.util.Arrays.toString(info.shape()),
                                info.length());
                    });

            if (metadata.size() > toPrint) {
                System.out.printf("... (%d more tensors)%n", metadata.size() - toPrint);
            }
        } catch (Exception e) {
            System.err.println("Failed to inspect safetensors: " + e.getMessage());
        }
    }

    private Path resolveInputPath() {
        if (path != null && !path.isBlank()) {
            return Path.of(path).toAbsolutePath().normalize();
        }
        if (modelId == null || modelId.isBlank()) {
            return null;
        }

        Path fallback = null;
        for (Path base : new Path[] {
                AqliHome.path("models", "safetensors"),
                AqliHome.path("models", "libtorchscript")
        }) {
            Path direct = base.resolve(modelId + ".safetensors");
            if (fallback == null) {
                fallback = direct;
            }
            if (Files.exists(direct)) {
                return direct;
            }

            Path nested = base.resolve(modelId).resolve("model.safetensors");
            if (Files.exists(nested)) {
                return nested;
            }

            String normalized = modelId.replace("/", "_");
            Path normalizedPath = base.resolve(normalized + ".safetensors");
            if (Files.exists(normalizedPath)) {
                return normalizedPath;
            }

            Path modelDir = base.resolve(modelId);
            if (Files.isDirectory(modelDir)) {
                try (var stream = Files.walk(modelDir, 2)) {
                    Path found = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".safetensors"))
                            .findFirst()
                            .orElse(null);
                    if (found != null) {
                        return found;
                    }
                } catch (Exception ignored) {
                    // Continue with the next cache layout.
                }
            }
        }
        return fallback;
    }

    private Map<String, TensorMetadata> parse(Path path) throws Exception {
        long fileSize = Files.size(path);
        if (fileSize < Long.BYTES) {
            throw new IllegalArgumentException("File too small to be safetensors");
        }

        String headerJson;
        long headerLength;
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            ByteBuffer prefix = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, prefix);
            prefix.flip();
            headerLength = prefix.getLong();
            if (headerLength <= 0 || headerLength > fileSize - Long.BYTES || headerLength > MAX_HEADER_BYTES) {
                throw new IllegalArgumentException("Invalid safetensors header length: " + headerLength);
            }
            if (headerLength > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Safetensors header too large for CLI inspection: " + headerLength);
            }

            ByteBuffer header = ByteBuffer.allocate((int) headerLength);
            readFully(channel, header);
            header.flip();
            headerJson = StandardCharsets.UTF_8.decode(header).toString();
        }

        long baseOffset = Long.BYTES + headerLength;

        Map<String, TensorMetadata> out = new HashMap<>();
        try (JsonReader reader = Json.createReader(new StringReader(headerJson))) {
            JsonObject root = reader.readObject();
            for (String key : root.keySet()) {
                if ("__metadata__".equals(key)) {
                    continue;
                }
                JsonObject entry = root.getJsonObject(key);
                String dtype = entry.getString("dtype");
                long[] shape = entry.getJsonArray("shape")
                        .stream()
                        .mapToLong(v -> ((jakarta.json.JsonNumber) v).longValue())
                        .toArray();
                var offsets = entry.getJsonArray("data_offsets");
                long start = ((jakarta.json.JsonNumber) offsets.get(0)).longValue();
                long end = ((jakarta.json.JsonNumber) offsets.get(1)).longValue();
                out.put(key, new TensorMetadata(dtype, shape, baseOffset + start, end - start));
            }
        }
        return out;
    }

    private static void readFully(SeekableByteChannel channel, ByteBuffer buffer) throws java.io.IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new java.io.EOFException("Unexpected end of safetensors file");
            }
        }
    }

    private record TensorMetadata(String dtype, long[] shape, long absoluteStart, long length) {
    }
}
