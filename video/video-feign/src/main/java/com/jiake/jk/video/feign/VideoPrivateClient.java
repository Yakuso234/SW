package com.jiake.jk.video.feign;

import com.jiake.jk.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "videoService", contextId = "VideoPrivateClient")
public interface VideoPrivateClient {

     @PutMapping("/video/api/private/to-published/{videoId}")
     Result<Void> putVideoStatusToPublished(@PathVariable Long videoId);
}