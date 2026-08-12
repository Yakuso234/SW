package com.jiake.jk.video.controller._private;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.service.VideoService;
import com.jiake.jk.video.pojo.entity.Video;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "VideoPrivate")
@RestController
@RequestMapping("/private")
public class VideoPrivateController {

    @Autowired
    private VideoService videoService;

    @PutMapping("/to-published/{videoId}")
    public Result<Void> putVideoStatusToPublished(@PathVariable Long videoId) {
        videoService.putVideoStatusToPublished(videoId);
        return Result.success();
    }

    @PutMapping("/status/{videoId}")
    public Result<Boolean> transitionVideoStatus(@PathVariable Long videoId,
                                                  @RequestParam("expectedStatus") Video.VideoStatus expectedStatus,
                                                  @RequestParam("targetStatus") Video.VideoStatus targetStatus) {
        return Result.success(videoService.transitionVideoStatus(videoId, expectedStatus, targetStatus));
    }

}
