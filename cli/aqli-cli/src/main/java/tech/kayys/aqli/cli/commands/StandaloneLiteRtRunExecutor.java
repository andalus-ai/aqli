package tech.kayys.aqli.cli.commands;

import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

/**
 * Executes the LiteRT standalone runner and translates its process status
 * into the full CLI's continuation/termination contract.
 *
 * <p>Eligibility, route selection, and argument construction remain in
 * {@link RunCommand}; this class owns only the standalone invocation boundary.</p>
 */
final class StandaloneLiteRtRunExecutor {

    private StandaloneLiteRtRunExecutor() {
    }

    static boolean execute(
            String[] args,
            ToIntFunction<String[]> runner,
            Runnable onSuccess,
            IntConsumer onFailure) {
        int status = runner.applyAsInt(args);
        if (StandaloneRunProtocol.isFallbackToFullCliStatus(status)) {
            return false;
        }
        if (status == 0) {
            onSuccess.run();
        } else {
            onFailure.accept(status);
        }
        return true;
    }
}
