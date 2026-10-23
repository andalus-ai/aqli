package tech.kayys.aqli.safetensor.engine.prompt;

import java.util.List;
import tech.kayys.aljabr.spi.tokenizer.ChatTemplateFormatter;
import tech.kayys.aljabr.spi.Message;
import tech.kayys.aljabr.spi.model.ModelRuntimeTraits;

/**
 * Runtime prompt-template compatibility layer for checkpoints whose prompt
 * formatting must not depend on whatever tokenizer-core artifact happens to be
 * on the classpath.
 */
public final class PromptTemplateCompat {

    private PromptTemplateCompat() {
    }

    public static String format(List<Message> messages, String modelType) {
        return format(messages, modelType, null);
    }

    public static String format(List<Message> messages, String modelType, ModelRuntimeTraits runtimeTraits) {
        return ChatTemplateFormatter.format(messages, modelType);
    }
}
