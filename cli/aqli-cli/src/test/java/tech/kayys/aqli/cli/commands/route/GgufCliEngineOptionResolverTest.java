package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GgufCliEngineOptionResolverTest {

    @Test
    void explicitBooleanFlagsKeepTheirExistingPrecedence() {
        assertEquals("benchmark", GgufCliEngineOptionResolver.resolve(true, true, true, "custom"));
        assertEquals("java", GgufCliEngineOptionResolver.resolve(false, true, true, "custom"));
        assertEquals("llamacpp", GgufCliEngineOptionResolver.resolve(false, false, true, "custom"));
    }

    @Test
    void fallsBackToTrimmedEngineOption() {
        assertEquals("auto", GgufCliEngineOptionResolver.resolve(false, false, false, "  auto  "));
        assertEquals("", GgufCliEngineOptionResolver.resolve(false, false, false, "   "));
    }

    @Test
    void preservesNullWhenNoEngineOptionWasProvided() {
        assertNull(GgufCliEngineOptionResolver.resolve(false, false, false, null));
    }
}
