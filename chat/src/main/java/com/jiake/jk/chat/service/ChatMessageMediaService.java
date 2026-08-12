package com.jiake.jk.chat.service;

import com.jiake.jk.chat.entity.ChatMessageMedia;
import com.jiake.jk.chat.request.ChatMediaPresignRequest;
import com.jiake.jk.chat.response.ChatMediaPresignResponse;
import com.jiake.jk.chat.response.ChatMessageMediaResponse;

public interface ChatMessageMediaService {
    /**
     * 获取聊天媒体文件直传地址，支持图片、视频、语音。
     */
    ChatMediaPresignResponse presignPutObject(ChatMediaPresignRequest request);

    ChatMessageMediaResponse uploadCallback(Long mediaId);

    ChatMessageMediaResponse toResponse(ChatMessageMedia media);
}
