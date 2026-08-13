package com.jiake.jk.video.controller._public;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.utils.UserContext;
import com.jiake.jk.video.pojo.response.GetPublishedVideoResponse;
import com.jiake.jk.video.pojo.response.PublishedFeedResponse;
import com.jiake.jk.video.pojo.response.VideoMainResponse;
import com.jiake.jk.video.pojo.response.VideoSearchResponse;
import com.jiake.jk.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Video")
@RestController
@RequestMapping("/public")
public class VideoPublicController {

    @Autowired
    private VideoService videoService;

    @Operation(summary = "获取视频（刷视频时继续加载视频）")
    @GetMapping("/list")
    public Result<List<VideoMainResponse>> getVideos() {
        return Result.success(videoService.getVideos(UserContext.getUser()));
    }

    @Operation(summary = "按发布时间游标分页获取已发布视频 Feed")
    @GetMapping("/feed")
    public Result<PublishedFeedResponse> getPublishedFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(videoService.getPublishedFeed(UserContext.getUser(), cursor, pageSize));
    }

    @Operation(summary = "获取指定视频数据")
    @GetMapping
    public Result<VideoMainResponse> getVideo(@RequestParam Long videoId) {
        return Result.success(videoService.getVideo(UserContext.getUser(), videoId));
    }

    @Operation(summary = "获取他人已发布的作品")
    @GetMapping("/published")
    public Result<List<GetPublishedVideoResponse>> getPublishedVideo(
            @RequestParam Long creatorId,
            @RequestParam(required = false) Long lastMinId) {
        return Result.success(videoService.getPublishedVideo(creatorId, lastMinId));
    }

    @Operation(summary = "搜索视频")
    @GetMapping("/search")
    public Result<List<VideoSearchResponse>> searchVideos(@RequestParam String keyword) {
        return Result.success(videoService.searchVideos(keyword));
    }
}
