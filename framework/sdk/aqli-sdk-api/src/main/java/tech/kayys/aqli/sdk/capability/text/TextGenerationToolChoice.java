package tech.kayys.aqli.sdk.capability.text;

/**
 * Typed, transport-neutral selection policy for tools during text generation.
 *
 * <p>This contract deliberately contains no execution-SPI or transport-specific
 * types. Adapters are responsible for translating it into their wire or
 * execution representation.</p>
 */
public record TextGenerationToolChoice(Mode mode, String functionName) {

    public TextGenerationToolChoice {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == Mode.FUNCTION) {
            if (functionName == null || functionName.isBlank()) {
                throw new IllegalArgumentException("functionName must not be blank for FUNCTION choice");
            }
            functionName = functionName.trim();
        } else if (functionName != null) {
            throw new IllegalArgumentException("functionName is only valid for FUNCTION choice");
        }
    }

    public static TextGenerationToolChoice auto() {
        return new TextGenerationToolChoice(Mode.AUTO, null);
    }

    public static TextGenerationToolChoice none() {
        return new TextGenerationToolChoice(Mode.NONE, null);
    }

    public static TextGenerationToolChoice required() {
        return new TextGenerationToolChoice(Mode.REQUIRED, null);
    }

    public static TextGenerationToolChoice function(String name) {
        return new TextGenerationToolChoice(Mode.FUNCTION, name);
    }

    public enum Mode {
        AUTO,
        NONE,
        REQUIRED,
        FUNCTION
    }
}
