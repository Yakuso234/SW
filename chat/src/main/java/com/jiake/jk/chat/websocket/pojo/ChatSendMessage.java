package com.jiake.jk.chat.websocket.pojo;

import com.jiake.jk.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSendMessage {
    private Long conversationId;
    private String clientMsgId;
    private String content;
    private ChatMessage.MessageType messageType;
    private Long mediaId;
}
