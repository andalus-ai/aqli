package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tech.kayys.aqli.inference.gguf.GgufDaemonRequestDecoder;
import tech.kayys.aqli.inference.gguf.GgufRunArguments;

import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class GgufDaemonExecutionHandlerTest {
    private static final int FALLBACK = 17;
    private static final int ERROR = 2;
    private static final Path MODEL = Path.of("model.gguf");

    @Test
    void runRequestDelegatesAndClearsRequestTiming() {
        FakeOperations operations = new FakeOperations();
        GgufDaemonExecutionHandler handler = handler(operations);

        int status = handler.execute(request(GgufDaemonRequestDecoder.Kind.RUN, true),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()), null);

        assertEquals(0, status);
        assertEquals(1, operations.runCalls);
        assertEquals(0, operations.prewarmCalls);
        assertTrue(operations.timingWasEnabled);
        assertTrue(operations.timingWasCleared);
    }

    @Test
    void prewarmRequestUsesPrewarmOperation() {
        FakeOperations operations = new FakeOperations();
        GgufDaemonExecutionHandler handler = handler(operations);

        int status = handler.execute(request(GgufDaemonRequestDecoder.Kind.PREWARM, false),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()), null);

        assertEquals(0, status);
        assertEquals(0, operations.runCalls);
        assertEquals(1, operations.prewarmCalls);
    }

    @Test
    void unsupportedArgumentsReturnFallbackWithoutExecuting() {
        FakeOperations operations = new FakeOperations();
        operations.arguments = GgufRunArguments.parse(new String[] {"run", "--unknown-option"},
                "cpu", "java");
        GgufDaemonExecutionHandler handler = handler(operations);

        int status = handler.execute(request(GgufDaemonRequestDecoder.Kind.RUN, false),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()), null);

        assertEquals(FALLBACK, status);
        assertEquals(0, operations.runCalls);
        assertEquals(0, operations.prewarmCalls);
        assertTrue(operations.timingWasCleared);
    }

    @Test
    void executionFailureReturnsErrorAndReportsMessage() {
        FakeOperations operations = new FakeOperations();
        operations.failure = new IllegalStateException("execution failed");
        operations.debug = true;
        GgufDaemonExecutionHandler handler = handler(operations);
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int status = handler.execute(request(GgufDaemonRequestDecoder.Kind.RUN, false),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(error), null);

        assertEquals(ERROR, status);
        assertTrue(error.toString().contains("GGUF daemon request failed: execution failed"));
        assertTrue(error.toString().contains("IllegalStateException"));
        assertTrue(operations.timingWasCleared);
    }

    @Test
    void missingModelReturnsFallback() {
        FakeOperations operations = new FakeOperations();
        operations.model = Optional.empty();
        GgufDaemonExecutionHandler handler = handler(operations);

        int status = handler.execute(request(GgufDaemonRequestDecoder.Kind.RUN, false),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()), null);

        assertEquals(FALLBACK, status);
        assertFalse(operations.runCalls > 0);
    }

    private static GgufDaemonExecutionHandler handler(FakeOperations operations) {
        return new GgufDaemonExecutionHandler(FALLBACK, ERROR, operations);
    }

    private static GgufDaemonRequestDecoder.Request request(
            GgufDaemonRequestDecoder.Kind kind, boolean timing) {
        return new GgufDaemonRequestDecoder.Request(kind, new String[] {"run"}, timing);
    }

    private static final class FakeOperations implements GgufDaemonExecutionHandler.Operations {
        private GgufRunArguments arguments = GgufRunArguments.parse(
                new String[] {"run", "--model", "model.gguf", "--prompt", "hello"}, "cpu", "java");
        private Optional<Path> model = Optional.of(MODEL);
        private Throwable failure;
        private boolean debug;
        private boolean timingWasEnabled;
        private boolean timingWasCleared;
        private int runCalls;
        private int prewarmCalls;

        @Override
        public GgufRunArguments parseArguments(String[] raw) {
            return arguments;
        }

        @Override
        public Optional<Path> resolveModel(GgufRunArguments parsed) {
            return model;
        }

        @Override
        public int prewarm(Path modelPath, GgufRunArguments parsed,
                PrintStream out, PrintStream err, GgufSessionCache cache) {
            prewarmCalls++;
            return 0;
        }

        @Override
        public int run(Path modelPath, GgufRunArguments parsed,
                PrintStream out, PrintStream err, GgufSessionCache cache) {
            runCalls++;
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            return 0;
        }

        @Override
        public void setRequestTiming(boolean enabled) {
            timingWasEnabled = enabled;
        }

        @Override
        public void clearRequestTiming() {
            timingWasCleared = true;
        }

        @Override
        public boolean debugEnabled() {
            return debug;
        }

        @Override
        public String briefMessage(Throwable throwable) {
            return throwable.getMessage();
        }
    }
}
