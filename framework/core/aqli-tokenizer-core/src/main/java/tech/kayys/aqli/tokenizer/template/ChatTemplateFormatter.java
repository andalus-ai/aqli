/*
 * Aqli Inference Engine — SafeTensor Text Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChatTemplateFormatter.java
 */
package tech.kayys.aqli.models.core;

import java.util.ArrayList;
import java.util.List;
import tech.kayys.aqli.spi.Message;

/**
 * Bridges Aqli messages to Aljabr's SPI chat template discovery mechanism.
 * Architecture-specific formatters live in {@code tech.kayys.aljabr.models.*}.
 */
public final class ChatTemplateFormatter {

    private ChatTemplateFormatter() {}

    public static String format(List<Message> messages, String modelType) {
        if (messages == null) {
            return "";
        }
        List<tech.kayys.aljabr.spi.Message> alkMessages = new ArrayList<>(messages.size());
        for (Message m : messages) {
            tech.kayys.aljabr.spi.Message.Role role;
            try {
                role = tech.kayys.aljabr.spi.Message.Role.valueOf(m.getRole().name());
            } catch (Exception e) {
                role = tech.kayys.aljabr.spi.Message.Role.USER;
            }
            alkMessages.add(new tech.kayys.aljabr.spi.Message(role, m.getContent()));
        }
        return tech.kayys.aljabr.spi.tokenizer.ChatTemplateFormatter.format(alkMessages, modelType);
    }

    public static boolean supportsModelType(String modelType) {
        return tech.kayys.aljabr.spi.tokenizer.ChatTemplateFormatter.supportsModelType(modelType);
    }

    public static String formatChatML(List<Message> messages) {
        return format(messages, "chatml");
    }
}
