package com.jiake.jk.live.websocket.pojo;

import lombok.Data;

@Data
public class ChatSendMessage {
    Long id;
    String name;
    String content;
}
