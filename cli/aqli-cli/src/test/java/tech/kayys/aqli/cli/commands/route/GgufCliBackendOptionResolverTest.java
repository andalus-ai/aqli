package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GgufCliBackendOptionResolverTest {

    @Test
    void explicitBackendOverridesInheritedDefaults() {
        assertEquals("metal", GgufCliBackendOptionResolver.resolve(" metal ", true, "vulkan"));
    }

    @Test
    void cpuDefaultPrecedesInheritedPlatform() {
        assertEquals("cpu", GgufCliBackendOptionResolver.resolve(null, true, "metal"));
    }

    @Test
    void usesTrimmedInheritedPlatformWhenCpuIsNotRequested() {
        assertEquals("metal", GgufCliBackendOptionResolver.resolve(null, false, " metal "));
    }

    @Test
    void returnsNullWhenNoBackendCanBeResolved() {
        assertNull(GgufCliBackendOptionResolver.resolve(null, false, null));
        assertNull(GgufCliBackendOptionResolver.resolve("  ", false, " "));
    }
}
