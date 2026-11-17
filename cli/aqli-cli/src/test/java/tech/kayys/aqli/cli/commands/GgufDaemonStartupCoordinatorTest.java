package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GgufDaemonStartupCoordinatorTest {

    @Test
    void launchesAndWaitsUntilDaemonPublishesItsPort() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();
        GgufDaemonStartupCoordinator coordinator = new GgufDaemonStartupCoordinator(
                (command, mode, label, log, mac, debug, nativePaths) -> {
                    assertEquals(List.of("java", "__daemon"), command);
                    assertEquals("nohup", mode);
                    assertEquals("daemon-label", label);
                    assertEquals(Path.of("/tmp/daemon.log"), log);
                    return true;
                },
                () -> probes.incrementAndGet() >= 3 ? OptionalInt.of(4321) : OptionalInt.empty(),
                () -> 1_000L,
                now::get,
                millis -> {
                    sleeps.incrementAndGet();
                    now.addAndGet(millis * 1_000_000L);
                },
                100L);

        assertTrue(coordinator.start(
                List.of("java", "__daemon"), "nohup", "daemon-label",
                Path.of("/tmp/daemon.log"), false, false, ""));

        assertEquals(2, sleeps.get());
        assertEquals(3, probes.get());
    }

    @Test
    void doesNotPollWhenProcessLauncherRejectsStartup() throws Exception {
        AtomicInteger probes = new AtomicInteger();
        GgufDaemonStartupCoordinator coordinator = new GgufDaemonStartupCoordinator(
                (command, mode, label, log, mac, debug, nativePaths) -> false,
                () -> {
                    probes.incrementAndGet();
                    return OptionalInt.empty();
                },
                () -> 1_000L,
                () -> 0L,
                millis -> { },
                100L);

        assertFalse(coordinator.start(
                List.of("java", "__daemon"), "auto", "daemon-label",
                Path.of("/tmp/daemon.log"), true, false, ""));

        assertEquals(0, probes.get());
    }

    @Test
    void reportsChildStartupFailureWithoutWaitingForTimeout() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger probes = new AtomicInteger();
        List<String> failures = new java.util.ArrayList<>();
        GgufDaemonStartupCoordinator coordinator = new GgufDaemonStartupCoordinator(
                (command, mode, label, log, mac, debug, nativePaths) -> true,
                () -> {
                    probes.incrementAndGet();
                    return OptionalInt.empty();
                },
                () -> 20_000L,
                now::get,
                millis -> now.addAndGet(millis * 1_000_000L),
                100L,
                () -> Optional.of("Unable to bind daemon socket"),
                failures::add);

        assertFalse(coordinator.start(
                List.of("java", "__daemon"), "nohup", "daemon-label",
                Path.of("/tmp/daemon.log"), false, true, ""));

        assertEquals(List.of("daemon process failed during startup: Unable to bind daemon socket"), failures);
        assertEquals(1, probes.get());
        assertEquals(0L, now.get());
    }

    @Test
    void returnsFalseWhenReadinessTimeoutExpires() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger probes = new AtomicInteger();
        List<String> failures = new java.util.ArrayList<>();
        GgufDaemonStartupCoordinator coordinator = new GgufDaemonStartupCoordinator(
                (command, mode, label, log, mac, debug, nativePaths) -> true,
                () -> {
                    probes.incrementAndGet();
                    return OptionalInt.empty();
                },
                () -> 200L,
                now::get,
                millis -> now.addAndGet(millis * 1_000_000L),
                100L,
                Optional::empty,
                failures::add);

        assertFalse(coordinator.start(
                List.of("java", "__daemon"), "nohup", "daemon-label",
                Path.of("/tmp/daemon.log"), false, true, "/tmp/libs"));

        assertEquals(3, probes.get());
        assertEquals(List.of("daemon did not publish its port within 200 ms; inspect the daemon log for details"),
                failures);
    }
}
