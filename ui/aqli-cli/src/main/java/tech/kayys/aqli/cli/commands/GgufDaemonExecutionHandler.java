package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.inference.gguf.GgufDaemonRequestDecoder;
import tech.kayys.aqli.inference.gguf.GgufRunArguments;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Executes a decoded daemon request through the standalone runner's model and
 * inference operations. Protocol framing and socket lifecycle remain outside
 * this class.
 */
final class GgufDaemonExecutionHandler {
    interface Operations {
        GgufRunArguments parseArguments(String[] arguments);

        Optional<Path> resolveModel(GgufRunArguments arguments);

        int prewarm(Path modelPath, GgufRunArguments arguments,
                PrintStream out, PrintStream err, GgufSessionCache sessionCache) throws Throwable;

        int run(Path modelPath, GgufRunArguments arguments,
                PrintStream out, PrintStream err, GgufSessionCache sessionCache) throws Throwable;

        void setRequestTiming(boolean enabled);

        void clearRequestTiming();

        boolean debugEnabled();

        String briefMessage(Throwable throwable);
    }

    private final int fallbackStatus;
    private final int errorStatus;
    private final Operations operations;

    GgufDaemonExecutionHandler(int fallbackStatus, int errorStatus, Operations operations) {
        this.fallbackStatus = fallbackStatus;
        this.errorStatus = errorStatus;
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
    }

    int execute(
            GgufDaemonRequestDecoder.Request request,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache) {
        boolean prewarmRequest = request.kind() == GgufDaemonRequestDecoder.Kind.PREWARM;
        operations.setRequestTiming(request.requestTiming());
        try {
            GgufRunArguments parsed = operations.parseArguments(request.arguments());
            if (!parsed.supported()) {
                return fallbackStatus;
            }
            Optional<Path> modelPath = operations.resolveModel(parsed);
            if (modelPath.isEmpty()) {
                return fallbackStatus;
            }
            return prewarmRequest
                    ? operations.prewarm(modelPath.get(), parsed, out, err, sessionCache)
                    : operations.run(modelPath.get(), parsed, out, err, sessionCache);
        } catch (Throwable throwable) {
            err.println("GGUF daemon request failed: " + operations.briefMessage(throwable));
            if (operations.debugEnabled()) {
                throwable.printStackTrace(err);
            }
            return errorStatus;
        } finally {
            operations.clearRequestTiming();
        }
    }
}
