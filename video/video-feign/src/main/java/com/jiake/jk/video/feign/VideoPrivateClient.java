package com.jiake.jk.video.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.response.VideoProcessingStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "videoService", contextId = "VideoPrivateClient")
public interface VideoPrivateClient {

     @GetMapping("/video/api/private/creator/{creatorId}/processing/{videoId}")
     Result<VideoProcessingStatusResponse> getCreatorVideoProcessingStatus(@PathVariable Long creatorId,
                                                                             @PathVariable Long videoId);

     @PutMapping("/video/api/private/to-published/{videoId}")
     Result<Void> putVideoStatusToPublished(@PathVariable Long videoId);

     @PutMapping("/video/api/private/status/{videoId}")
     Result<Boolean> transitionVideoStatus(@PathVariable Long videoId,
                                           @RequestParam("expectedStatus") Video.VideoStatus expectedStatus,
                                           @RequestParam("targetStatus") Video.VideoStatus targetStatus);

     @PutMapping("/video/api/private/processing/{videoId}/claim")
     Result<Boolean> claimVideoProcessing(@PathVariable Long videoId);

     @PutMapping("/video/api/private/processing/{videoId}/complete")
     Result<Void> completeVideoProcessing(@PathVariable Long videoId,
                                          @RequestBody VideoProcessingResultRequest request);

     @PutMapping("/video/api/private/processing/{videoId}/fail")
     Result<Void> failVideoProcessing(@PathVariable Long videoId,
                                      @RequestBody VideoProcessingFailureRequest request);

     record VideoProcessingResultRequest(String processedVideoKey, String coverKey) { }

     record VideoProcessingFailureRequest(String errorMessage) { }
}
