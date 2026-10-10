package tech.kayys.aqli.tokenizer.spi;

import java.util.List;

public interface PreTokenizer {
    List<String> split(String text);
}