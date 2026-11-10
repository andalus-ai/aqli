package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class StandaloneLiteRtRunExecutorTest {

    @Test
    void fallbackStatusLeavesFullCliInControl() {
        AtomicInteger successCalls = new AtomicInteger();
        AtomicInteger failureCalls = new AtomicInteger();

        boolean handled = StandaloneLiteRtRunExecutor.execute(
                new String[] {"run", "--model", "model"},
                args -> StandaloneRunProtocol.FALLBACK_TO_FULL_CLI,
                successCalls::incrementAndGet,
                status -> failureCalls.incrementAndGet());

        assertFalse(handled);
        assertEquals(0, successCalls.get());
        assertEquals(0, failureCalls.get());
    }

    @Test
    void successfulRunInvokesSuccessExitAndIsHandled() {
        AtomicInteger successCalls = new AtomicInteger();
        AtomicInteger failureCalls = new AtomicInteger();

        boolean handled = StandaloneLiteRtRunExecutor.execute(
                new String[] {"run"},
                args -> 0,
                successCalls::incrementAndGet,
                status -> failureCalls.incrementAndGet());

        assertTrue(handled);
        assertEquals(1, successCalls.get());
        assertEquals(0, failureCalls.get());
    }

    @Test
    void executionFailurePropagatesItsExitStatusWithoutFallback() {
        AtomicInteger successCalls = new AtomicInteger();
        AtomicReference<Integer> failureStatus = new AtomicReference<>();

        boolean handled = StandaloneLiteRtRunExecutor.execute(
                new String[] {"run"},
                args -> 7,
                successCalls::incrementAndGet,
                failureStatus::set);

        assertTrue(handled);
        assertEquals(0, successCalls.get());
        assertEquals(7, failureStatus.get());
    }

    @Test
    void forwardsTheOriginalArgumentsToRunner() {
        String[] expected = {"run", "--modelFile", "/models/model.litertlm"};
        AtomicReference<String[]> actual = new AtomicReference<>();

        StandaloneLiteRtRunExecutor.execute(
                expected,
                args -> {
                    actual.set(args);
                    return StandaloneRunProtocol.FALLBACK_TO_FULL_CLI;
                },
                () -> {},
                status -> {});

        assertTrue(actual.get() == expected);
    }
}
