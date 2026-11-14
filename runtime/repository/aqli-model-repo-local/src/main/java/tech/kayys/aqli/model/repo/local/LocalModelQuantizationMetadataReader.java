package tech.kayys.aqli.model.repo.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads AQLI's optional local quantization sidecar metadata.
 *
 * <p>This describes metadata recorded by AQLI; it does not inspect model
 * tensors, infer quantization formats, or determine backend support. Those
 * capabilities belong to the model format and inference engine.</p>
 */
public final class LocalModelQuantizationMetadataReader {

    private final ObjectMapper mapper;

    public LocalModelQuantizationMetadataReader() {
        this(new ObjectMapper());
    }

    LocalModelQuantizationMetadataReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Reads {@code aqli_quant.json} from a model directory.
     * Missing, malformed, or structurally invalid sidecars produce an empty result.
     */
    public Optional<Metadata> read(Path modelDirectory) {
        if (modelDirectory == null) {
            return Optional.empty();
        }
        Path sidecar = modelDirectory.resolve("aqli_quant.json");
        if (!Files.isRegularFile(sidecar)) {
            return Optional.empty();
        }
        try {
            JsonNode quant = mapper.readTree(sidecar.toFile()).path("aqli_quant");
            if (!quant.isObject()) {
                return Optional.empty();
            }
            return Optional.of(new Metadata(
                    text(quant, "strategy"),
                    integer(quant, "bits"),
                    integer(quant, "group_size"),
                    text(quant, "source_model")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText() : null;
    }

    private static int integer(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isNumber() && value.canConvertToInt()
                ? value.intValue() : 0;
    }

    public record Metadata(String strategy, int bits, int groupSize, String sourceModel) {
    }
}
