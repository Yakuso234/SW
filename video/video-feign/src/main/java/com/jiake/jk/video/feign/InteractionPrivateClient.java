package com.jiake.jk.video.feign;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.request.VideoInteractionBatchRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "videoService", contextId = "InteractionPrivateClient")
public interface InteractionPrivateClient {

    @PostMapping("/video/api/private/interaction/like-batch")
    Result<Void> likeBatch(@RequestBody VideoInteractionBatchRequest videoInteractionBatchRequest);

    @PostMapping("/video/api/private/interaction/favorite-batch")
    Result<Void> favoriteBatch(@RequestBody VideoInteractionBatchRequest videoInteractionBatchRequest);
}
