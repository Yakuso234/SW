package com.jiake.jk.chat.response;

import com.jiake.jk.chat.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageResponse {
    Long id;
    Long conversationId;
    Boolean isUser;
    String content;
    ChatMessage.MessageType messageType;
    ChatMessageMediaResponse media;
    LocalDateTime createdTime;
}
