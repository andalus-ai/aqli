package tech.kayys.aqli.cli.commands.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureAdapterKindsTest {

    @Test
    void exposesStableExtensionKindIdentifiers() {
        assertEquals("pipeline", FeatureAdapterKinds.PIPELINE);
        assertEquals("training", FeatureAdapterKinds.TRAINING);
        assertEquals("backend", FeatureAdapterKinds.BACKEND);
        assertEquals("quantization", FeatureAdapterKinds.QUANTIZATION);
        assertEquals("capability", FeatureAdapterKinds.CAPABILITY);
    }

    @Test
    void preservesExistingNormalizationSemantics() {
        assertEquals("custom-kind", FeatureAdapterKinds.normalize("custom-kind"));
        assertEquals(" custom-kind ", FeatureAdapterKinds.normalize(" custom-kind "));
        assertEquals(null, FeatureAdapterKinds.normalize(null));
    }
}
