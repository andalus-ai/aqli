package tech.kayys.aqli.onnx.runner.tts.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MossTtsOnnxRunnerResourceTest {
    @Test
    void closesPartiallyOpenedSessionsInReverseAcquisitionOrder() {
        List<String> closed = new ArrayList<>();
        AutoCloseable first = () -> closed.add("first");
        AutoCloseable second = () -> closed.add("second");
        AutoCloseable third = () -> closed.add("third");

        MossTtsOnnxRunner.closePartiallyOpenedSessions(List.of(first, second, third));

        assertEquals(List.of("third", "second", "first"), closed);
    }

    @Test
    void toleratesCloseFailuresAndContinuesReleasingEarlierSessions() {
        List<String> closed = new ArrayList<>();
        AutoCloseable first = () -> closed.add("first");
        AutoCloseable failing = () -> {
            closed.add("failing");
            throw new IllegalStateException("close failed");
        };
        AutoCloseable third = () -> closed.add("third");

        MossTtsOnnxRunner.closePartiallyOpenedSessions(List.of(first, failing, third));

        assertEquals(List.of("third", "failing", "first"), closed);
    }
}
