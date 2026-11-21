package tech.kayys.aqli.sdk.internal;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGenerationStreamTest {

    @Test
    void cancellationInvokesUpstreamAndSuppressesSubsequentEvents() {
        DefaultGenerationStream stream = new DefaultGenerationStream();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger tokens = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        stream.onToken(token -> tokens.incrementAndGet());
        stream.onComplete(completed::incrementAndGet);
        stream.attachCancellation(cancelled::incrementAndGet);

        stream.emitToken("before");
        stream.cancel();
        stream.emitToken("after");
        stream.emitComplete();
        stream.cancel();

        assertEquals(1, cancelled.get());
        assertEquals(1, tokens.get());
        assertEquals(0, completed.get());
    }

    @Test
    void cancellationBeforeUpstreamAttachmentCancelsWhenAttached() {
        DefaultGenerationStream stream = new DefaultGenerationStream();
        AtomicInteger cancelled = new AtomicInteger();

        stream.cancel();
        stream.attachCancellation(cancelled::incrementAndGet);

        assertEquals(1, cancelled.get());
    }

    @Test
    void normalTerminationDoesNotCancelUpstream() {
        DefaultGenerationStream stream = new DefaultGenerationStream();
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        stream.attachCancellation(cancelled::incrementAndGet);
        stream.onComplete(completed::incrementAndGet);
        stream.emitComplete();
        stream.cancel();

        assertEquals(0, cancelled.get());
        assertEquals(1, completed.get());
    }

    @Test
    void upstreamAttachmentAfterSynchronousTerminationIsNotCancelled() {
        DefaultGenerationStream stream = new DefaultGenerationStream();
        AtomicInteger cancelled = new AtomicInteger();

        stream.emitComplete();
        stream.attachCancellation(cancelled::incrementAndGet);

        assertEquals(0, cancelled.get());
    }
}
