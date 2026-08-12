package com.jiake.jk.chat.controller;

import com.jiake.jk.chat.request.ChatMediaPresignRequest;
import com.jiake.jk.chat.response.ChatMediaPresignResponse;
import com.jiake.jk.chat.response.ChatMessageMediaResponse;
import com.jiake.jk.chat.service.ChatMessageMediaService;
import com.jiake.jk.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ChatMessageMedia")
@RestController
@RequestMapping("/me/message-media")
public class ChatMessageMediaController {

    @Autowired
    private ChatMessageMediaService chatMessageMediaService;

    @Operation(summary = "Get direct upload url for chat image, video or voice")
    @PostMapping("/presign-put-object")
    public Result<ChatMediaPresignResponse> presignPutObject(@RequestBody ChatMediaPresignRequest request) {
        return Result.success(chatMessageMediaService.presignPutObject(request));
    }

    @Operation(summary = "Chat image, video or voice upload callback")
    @PostMapping("/upload-callback")
    public Result<ChatMessageMediaResponse> uploadCallback(@RequestParam Long mediaId) {
        return Result.success(chatMessageMediaService.uploadCallback(mediaId));
    }
}
