package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LocalModelIndexMetadataInferenceTest {

    @Test
    void returnsNullWhenEntryOrFormatIsMissing() {
        assertNull(LocalModelIndexMetadataInference.providerFor(null));
        assertNull(LocalModelIndexMetadataInference.formatFor(null));

        LocalModelIndex.Entry entry = new LocalModelIndex.Entry();
        assertNull(LocalModelIndexMetadataInference.providerFor(entry));
        assertNull(LocalModelIndexMetadataInference.formatFor(entry));

        entry.format = "  ";
        assertEquals(null, LocalModelIndexMetadataInference.formatFor(entry));
    }

    @Test
    void infersProviderAndCanonicalFormatFromIndexFormat() {
        LocalModelIndex.Entry entry = new LocalModelIndex.Entry();
        entry.format = "GGUF";

        assertEquals(ModelFormatResolver.providerForFormat("GGUF"),
                LocalModelIndexMetadataInference.providerFor(entry));
        assertEquals(ModelFormatResolver.canonicalFormat("GGUF"),
                LocalModelIndexMetadataInference.formatFor(entry));
    }
}
