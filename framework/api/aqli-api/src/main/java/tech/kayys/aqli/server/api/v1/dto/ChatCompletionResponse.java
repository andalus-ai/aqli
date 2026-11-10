package tech.kayys.aqli.server.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Choice(
        int index,
        ChatCompletionMessage message,
        String finishReason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
        long promptTokens,
        long completionTokens,
        long totalTokens
    ) {}
}
