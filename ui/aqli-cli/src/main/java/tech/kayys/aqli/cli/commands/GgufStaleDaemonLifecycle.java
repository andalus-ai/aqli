package tech.kayys.aqli.cli.commands;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

/**
 * Applies the lifecycle policy for a daemon described by the persisted daemon
 * metadata. Metadata serialization remains owned by {@link GgufDaemonInfoStore}.
 */
final class GgufStaleDaemonLifecycle {
    interface ProcessController {
        boolean isAlive(long pid);
        void terminate(long pid, boolean force, long waitMillis);
    }

    private final GgufDaemonInfoStore infoStore;
    private final ProcessController processes;
    private final Runnable removeLaunchctlDaemon;

    GgufStaleDaemonLifecycle(
            GgufDaemonInfoStore infoStore,
            ProcessController processes,
            Runnable removeLaunchctlDaemon) {
        this.infoStore = infoStore;
        this.processes = processes;
        this.removeLaunchctlDaemon = removeLaunchctlDaemon;
    }

    OptionalInt resolvePort(Optional<GgufDaemonInfoStore.DaemonInfo> stored, String runtimeKey, boolean strictKey, boolean forceKill, long waitMillis) {
        if (stored.isEmpty()) {
            return OptionalInt.empty();
        }
        GgufDaemonInfoStore.DaemonInfo info = stored.get();
        if (!processes.isAlive(info.pid()) || (strictKey && !runtimeKey.equals(info.key()))) {
            discard(info, forceKill, waitMillis);
            return OptionalInt.empty();
        }
        return OptionalInt.of(info.port());
    }

    void discard(GgufDaemonInfoStore.DaemonInfo info, boolean forceKill, long waitMillis) {
        infoStore.delete();
        try {
            processes.terminate(info.pid(), forceKill, Math.max(100L, waitMillis));
        } catch (Exception ignored) {
            // Stale daemon cleanup has historically been best-effort.
        } finally {
            try {
                removeLaunchctlDaemon.run();
            } catch (Exception ignored) {
                // launchctl may be unavailable or the job may already be gone.
            }
        }
    }

    static ProcessController systemProcesses() {
        return new ProcessController() {
            @Override
            public boolean isAlive(long pid) {
                if (pid <= 0 || pid == ProcessHandle.current().pid()) {
                    return false;
                }
                try {
                    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
                } catch (Exception ignored) {
                    return false;
                }
            }

            @Override
            public void terminate(long pid, boolean force, long waitMillis) {
                if (!isAlive(pid)) {
                    return;
                }
                try {
                    ProcessHandle process = ProcessHandle.of(pid).orElse(null);
                    if (process == null || !process.isAlive()) {
                        return;
                    }
                    if (force) {
                        process.destroyForcibly();
                    } else {
                        process.destroy();
                    }
                    process.onExit().get(waitMillis, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                }
            }
        };
    }

}
