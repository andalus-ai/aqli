package tech.kayys.aqli.safetensor.engine.prompt;

import java.util.List;
import tech.kayys.aljabr.spi.tokenizer.ChatTemplateFormatter;
import tech.kayys.aljabr.spi.Message;
import tech.kayys.aljabr.spi.model.ModelRuntimeTraits;

/**
 * Runtime prompt-template compatibility layer for checkpoints whose prompt
 * formatting must not depend on whatever tokenizer-core artifact happens to be
 * on the classpath.
 *
 * <p>When a {@link ModelRuntimeTraits} is supplied and the {@code modelType}
 * string does not match any registered {@link tech.kayys.aljabr.spi.tokenizer.ModelChatTemplate}
 * provider, trait flags are used to select the correct format:
 * <ul>
 *   <li>{@code nativeBf16Matvec} → Gemma 4 turn-tag format ({@code <bos><|turn>…})</li>
 *   <li>{@code qwenText} → ChatML format ({@code <|im_start|>…})</li>
 * </ul>
 * Falls back to ChatML when no trait matches.
 */
public final class PromptTemplateCompat {

    private PromptTemplateCompat() {
    }

    public static String format(List<Message> messages, String modelType) {
        return format(messages, modelType, null);
    }

    public static String format(List<Message> messages, String modelType, ModelRuntimeTraits runtimeTraits) {
        // Prefer an explicit modelType match via the ServiceLoader-backed registry.
        if (modelType != null && !modelType.isBlank()
                && ChatTemplateFormatter.supportsModelType(modelType)) {
            return ChatTemplateFormatter.format(messages, modelType);
        }

        // Fall back to trait-based routing when the modelType is absent or unknown
        // (e.g. "custom") but runtime traits carry enough information to pick a format.
        if (runtimeTraits != null) {
            if (runtimeTraits.nativeBf16Matvec()) {
                // Gemma 4: route through the registry using the canonical model-type token
                // so that Gemma4ChatTemplate is used even when the caller passes "custom".
                return ChatTemplateFormatter.format(messages, "gemma4_text");
            }
            // qwenText and all other traits fall through to ChatML below.
        }

        return ChatTemplateFormatter.formatChatML(messages);
    }
}
