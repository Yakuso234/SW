package com.jiake.jk.chat.request;

import lombok.Data;

@Data
public class ChatMediaPresignRequest {
    private Integer mediaType;
    private String contentType;
    private String coverContentType;
}
