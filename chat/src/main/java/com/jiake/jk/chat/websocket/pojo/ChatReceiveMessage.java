package com.jiake.jk.chat.websocket.pojo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jiake.jk.chat.entity.ChatMessage;
import com.jiake.jk.chat.response.ChatMessageMediaResponse;
import com.jiake.jk.chat.websocket._enum.ChatReceiveMessageType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatReceiveMessage {
    private final ChatReceiveMessageType type = ChatReceiveMessageType.NORMAL;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;
    private String content;
    private ChatMessage.MessageType messageType;
    private ChatMessageMediaResponse media;
    private LocalDateTime sentTime;
}
