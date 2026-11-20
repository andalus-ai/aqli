package tech.kayys.aqli.sdk.capability.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextGenerationToolChoiceTest {

    @Test
    void createsNonFunctionChoicesWithoutFunctionName() {
        assertEquals(new TextGenerationToolChoice(TextGenerationToolChoice.Mode.AUTO, null),
                TextGenerationToolChoice.auto());
        assertEquals(new TextGenerationToolChoice(TextGenerationToolChoice.Mode.NONE, null),
                TextGenerationToolChoice.none());
        assertEquals(new TextGenerationToolChoice(TextGenerationToolChoice.Mode.REQUIRED, null),
                TextGenerationToolChoice.required());
    }

    @Test
    void createsNamedFunctionChoiceAndNormalizesName() {
        assertEquals(new TextGenerationToolChoice(TextGenerationToolChoice.Mode.FUNCTION, "lookup"),
                TextGenerationToolChoice.function(" lookup "));
    }

    @Test
    void rejectsMissingOrBlankFunctionName() {
        assertThrows(IllegalArgumentException.class,
                () -> TextGenerationToolChoice.function(null));
        assertThrows(IllegalArgumentException.class,
                () -> TextGenerationToolChoice.function(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationToolChoice(TextGenerationToolChoice.Mode.FUNCTION, null));
    }

    @Test
    void rejectsFunctionNameForNonFunctionModes() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationToolChoice(TextGenerationToolChoice.Mode.AUTO, "lookup"));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationToolChoice(TextGenerationToolChoice.Mode.NONE, "lookup"));
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationToolChoice(TextGenerationToolChoice.Mode.REQUIRED, "lookup"));
    }

    @Test
    void rejectsNullMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextGenerationToolChoice(null, null));
    }
}
