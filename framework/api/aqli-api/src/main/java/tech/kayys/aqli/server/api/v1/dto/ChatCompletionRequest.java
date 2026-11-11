package tech.kayys.aqli.server.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.kayys.aqli.spi.tool.ToolDefinition;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
    String model,
    List<ChatCompletionMessage> messages,
    boolean stream,
    double temperature,
    int maxTokens,
    List<ToolDefinition> tools,
    Object toolChoice
) {}
