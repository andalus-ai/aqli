package tech.kayys.aqli.server.api.v1.dto;

import tech.kayys.aqli.spi.Message;

import java.util.ArrayList;
import java.util.List;

public class ChatCompletionMapper {

    public static List<Message> toMessages(List<ChatCompletionMessage> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (ChatCompletionMessage dto : dtos) {
            if ("system".equalsIgnoreCase(dto.role())) {
                messages.add(Message.system(dto.content() != null ? dto.content() : ""));
            } else if ("user".equalsIgnoreCase(dto.role())) {
                messages.add(Message.user(dto.content() != null ? dto.content() : ""));
            } else if ("assistant".equalsIgnoreCase(dto.role())) {
                if (dto.toolCalls() != null && !dto.toolCalls().isEmpty()) {
                    messages.add(Message.assistantWithToolCalls(dto.content(), dto.toolCalls()));
                } else {
                    messages.add(Message.assistant(dto.content() != null ? dto.content() : ""));
                }
            } else if ("tool".equalsIgnoreCase(dto.role())) {
                messages.add(Message.tool(dto.toolCallId(), dto.content() != null ? dto.content() : ""));
            }
        }
        return messages;
    }
}
