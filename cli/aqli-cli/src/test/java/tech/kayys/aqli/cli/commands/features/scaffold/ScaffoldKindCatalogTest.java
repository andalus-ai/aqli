package tech.kayys.aqli.cli.commands.features.scaffold;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldKindCatalogTest {

    @Test
    void exposesStableKindsAndJsonFieldNames() {
        var kinds = ScaffoldKindCatalog.all();

        assertEquals(13, kinds.size());
        var pipeline = kinds.stream().filter(kind -> "pipeline".equals(kind.kind())).findFirst().orElseThrow();
        assertEquals("ModelPipeline", pipeline.service());
        assertEquals(List.of("text"), pipeline.defaultInputs());
        assertEquals(List.of("text"), pipeline.defaultOutputs());

        Map<String, Object> json = pipeline.toMap();
        assertEquals(List.of("kind", "service", "default_inputs", "default_outputs",
                "default_targets", "summary", "example"), List.copyOf(json.keySet()));
        assertEquals("pipeline", json.get("kind"));
    }

    @Test
    void returnsImmutableCatalogAndKindMetadata() {
        var kinds = ScaffoldKindCatalog.all();
        assertThrows(UnsupportedOperationException.class, () -> kinds.add(kinds.getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> kinds.getFirst().defaultInputs().add("image"));
    }

    @Test
    void providesCapabilitySpecificDefaultInputsAndOutputs() {
        assertEquals(List.of("dataset", "model"), ScaffoldKindCatalog.defaultAdapterInputs("training"));
        assertEquals(List.of("model", "metrics"), ScaffoldKindCatalog.defaultAdapterOutputs("training"));
        assertEquals(List.of("tensor"), ScaffoldKindCatalog.defaultAdapterInputs("backend"));
        assertEquals(List.of("artifact"), ScaffoldKindCatalog.defaultAdapterOutputs("converter"));
        assertEquals(List.of("model"), ScaffoldKindCatalog.defaultAdapterInputs("unknown"));
    }
}
