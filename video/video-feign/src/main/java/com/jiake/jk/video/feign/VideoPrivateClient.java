package com.jiake.jk.video.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.entity.Video;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "videoService", contextId = "VideoPrivateClient")
public interface VideoPrivateClient {

     @PutMapping("/video/api/private/to-published/{videoId}")
     Result<Void> putVideoStatusToPublished(@PathVariable Long videoId);

     @PutMapping("/video/api/private/status/{videoId}")
     Result<Boolean> transitionVideoStatus(@PathVariable Long videoId,
                                           @RequestParam("expectedStatus") Video.VideoStatus expectedStatus,
                                           @RequestParam("targetStatus") Video.VideoStatus targetStatus);

     @PutMapping("/video/api/private/processing/{videoId}/claim")
     Result<Boolean> claimVideoProcessing(@PathVariable Long videoId);
}
