package tech.kayys.aqli.provider.litert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteRTTokenStreamLimiterTest {

    @Test
    void matchesChunkedStreamTokenLimit() {
        LiteRTTokenStreamLimiter limiter = new LiteRTTokenStreamLimiter(3);

        String output = limiter.offer("hello ")
                + limiter.offer("world\nfrom ")
                + limiter.offer("aqli now");

        assertEquals("hello world\nfrom", output);
        assertEquals(3, limiter.emittedTokenCount());
        assertTrue(limiter.atLimit());
    }

    @Test
    void defersTrailingWhitespaceUntilNextToken() {
        LiteRTTokenStreamLimiter limiter = new LiteRTTokenStreamLimiter(2);

        assertEquals("hello", limiter.offer("hello "));
        assertEquals("", limiter.offer("\n"));
        assertEquals(" \nworld", limiter.offer("world"));
        assertEquals(2, limiter.emittedTokenCount());
        assertFalse(limiter.atLimit());
    }
}
