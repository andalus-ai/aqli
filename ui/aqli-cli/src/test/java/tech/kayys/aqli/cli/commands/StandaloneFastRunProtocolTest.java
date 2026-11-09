package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandaloneFastRunProtocolTest {

    @Test
    void distinguishesFallbackFromSuccessAndExecutionFailure() {
        assertTrue(StandaloneFastRunProtocol.isFallbackToFullCliStatus(
                StandaloneFastRunProtocol.FALLBACK_TO_FULL_CLI));
        assertFalse(StandaloneFastRunProtocol.isFallbackToFullCliStatus(0));
        assertFalse(StandaloneFastRunProtocol.isFallbackToFullCliStatus(2));
    }
}
