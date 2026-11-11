package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandaloneRunProtocolTest {

    @Test
    void distinguishesFallbackFromSuccessAndExecutionFailure() {
        assertTrue(StandaloneRunProtocol.isFallbackToFullCliStatus(
                StandaloneRunProtocol.FALLBACK_TO_FULL_CLI));
        assertFalse(StandaloneRunProtocol.isFallbackToFullCliStatus(0));
        assertFalse(StandaloneRunProtocol.isFallbackToFullCliStatus(2));
    }
}
