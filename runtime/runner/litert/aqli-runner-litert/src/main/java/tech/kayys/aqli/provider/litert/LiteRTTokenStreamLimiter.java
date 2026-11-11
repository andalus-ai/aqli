package tech.kayys.aqli.provider.litert;

/**
 * Bounds streamed text output using an approximate whitespace-delimited token count.
 * The limiter preserves whitespace between emitted tokens and drops trailing whitespace
 * once the configured token limit has been reached.
 */
public final class LiteRTTokenStreamLimiter {
    private final int maxTokens;
    private final StringBuilder pendingWhitespace = new StringBuilder();
    private int emittedTokenCount;
    private boolean inToken;
    private boolean atLimit;

public LiteRTTokenStreamLimiter(int maxTokens) {
        this.maxTokens = Math.max(1, maxTokens);
    }

public String offer(String chunk) {
        if (chunk == null || chunk.isEmpty() || atLimit) {
            return "";
        }
        StringBuilder delta = new StringBuilder(chunk.length());
        for (int i = 0; i < chunk.length(); i++) {
            char ch = chunk.charAt(i);
            if (Character.isWhitespace(ch)) {
                pendingWhitespace.append(ch);
                inToken = false;
                continue;
            }
            if (!inToken) {
                int nextTokenCount = emittedTokenCount + 1;
                if (nextTokenCount > maxTokens) {
                    pendingWhitespace.setLength(0);
                    atLimit = true;
                    break;
                }
                emittedTokenCount = nextTokenCount;
                inToken = true;
            }
            if (pendingWhitespace.length() > 0) {
                delta.append(pendingWhitespace);
                pendingWhitespace.setLength(0);
            }
            delta.append(ch);
        }
        return delta.toString();
    }

public boolean atLimit() {
        return atLimit;
    }

public int emittedTokenCount() {
        return emittedTokenCount;
    }
}
