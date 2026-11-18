package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LocalModelIndexInputResolverTest {

    @Test
    void leavesBlankModelReferenceForNormalRuntimeResolution() {
        LocalModelIndexInputResolver.Result result = LocalModelIndexInputResolver.resolve(
                new LocalModelIndexInputResolver.Options(
                        "  ", null, "auto", false, false, false, false),
                entry -> {
                    throw new AssertionError("provider inference must not run without an index entry");
                },
                entry -> {
                    throw new AssertionError("format inference must not run without an index entry");
                });

        assertFalse(result.resolved());
        assertFalse(result.disabled());
        assertEquals("  ", result.modelId());
        assertEquals("auto", result.providerId());
        assertEquals(null, result.localPath());
        assertEquals(null, result.format());
        assertEquals(0, result.messages().size());
    }
}
