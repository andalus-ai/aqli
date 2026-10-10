package tech.kayys.aqli.tokenizer;

import tech.kayys.aqli.tokenizer.loader.HuggingFaceBpeLoader;
import tech.kayys.aqli.tokenizer.spi.*;

public class TokenizerFactory {

    public static Tokenizer create(ModelConfig config) {

        return switch (config.getTokenizerType()) {

            case BPE -> new HuggingFaceBpeLoader()
                    .load(config.getTokenizerPath());

            case SENTENCE_PIECE -> {
                throw new UnsupportedOperationException("SentencePiece not yet implemented");
            }

            case WORD_PIECE -> {
                throw new UnsupportedOperationException("WordPiece not yet implemented");
            }

            case TIKTOKEN -> {
                throw new UnsupportedOperationException("Tiktoken not yet implemented");
            }
        };
    }
}