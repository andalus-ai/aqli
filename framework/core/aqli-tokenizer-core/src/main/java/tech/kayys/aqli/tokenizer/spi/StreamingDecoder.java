package tech.kayys.aqli.tokenizer.spi;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

/**
 * Stateful helper for decoding generated tokens into text pieces.
 *
 * <p>
 * Handles the complexity of multi-token BPE merges, UTF-8 byte boundaries,
 * and tokenizer-specific whitespace symbols (e.g., GPT-2's 'Ġ' or SPM's ' ').
 */
public class StreamingDecoder {

    private final Tokenizer tokenizer;
    private final DecodeOptions options;
    private long[] tokenBuffer = new long[16];
    private int tokenCount = 0;
    private String lastFullText = "";

    private static final Pattern SCRIPT_DETECTOR = Pattern.compile(
            "(?:" +
                    "\\p{L}\\p{Lo}|" + // Kana/CJK
                    "\\p{L}\\p{IsGreek}|" + // Latin + Greek
                    "\\p{L}\\p{IsThai}|" + // Latin + Thai
                    "\\p{L}\\p{IsArabic}|" + // Latin + Arabic
                    "\\p{L}\\p{IsDevanagari}" + // Latin + Devanagari
                    ")");

    private static final Set<Character> SUSPICIOUS_SEQUENCES = new HashSet<>();
    static {
        // Characters that shouldn't appear together in natural text
        SUSPICIOUS_SEQUENCES.add('\uFFFD'); // Replacement character
    }

    public StreamingDecoder(Tokenizer tokenizer, DecodeOptions options) {
        this.tokenizer = tokenizer;
        this.options = options;
    }

    /**
     * Append a new token and return ONLY the newly decoded text delta.
     *
     * @param tokenId the next token from the model
     * @return the decoded string fragment corresponding to this token
     */
    public String decodeNext(long tokenId) {
        if (tokenCount >= tokenBuffer.length) {
            long[] newBuffer = new long[tokenBuffer.length * 2];
            System.arraycopy(tokenBuffer, 0, newBuffer, 0, tokenCount);
            tokenBuffer = newBuffer;
        }
        tokenBuffer[tokenCount++] = tokenId;

        // We re-decode the full sequence using ranged decode to handle BPE merges
        // and multi-byte UTF-8 correctly without copying prefixes.
        String currentFullText = tokenizer.decode(tokenBuffer, 0, tokenCount, options);

        if (currentFullText.length() < lastFullText.length()) {
            // This can happen with certain special tokens or normalization rules.
            // Reset and return the full text for this step.
            lastFullText = currentFullText;
            return currentFullText;
        }

        String delta = currentFullText.substring(lastFullText.length());
        lastFullText = currentFullText;
        return delta;
    }

    /**
     * Clear the internal state for a new generation session.
     */
    public void reset() {
        tokenCount = 0;
        lastFullText = "";
    }

    public String currentText() {
        return lastFullText;
    }
}
