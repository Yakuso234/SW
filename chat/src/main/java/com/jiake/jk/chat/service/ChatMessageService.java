package com.jiake.jk.chat.service;

import com.jiake.jk.chat.websocket.pojo.ChatSendMessage;

public interface ChatMessageService {
    void handleChatMessage(ChatSendMessage sendMessage, Long senderId);
}
