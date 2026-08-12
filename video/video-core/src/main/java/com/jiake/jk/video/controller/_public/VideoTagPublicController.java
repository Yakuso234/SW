package com.jiake.jk.video.controller._public;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.response.GetSimpleVideoTagResponse;
import com.jiake.jk.video.service.VideoTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Video-Tag")
@RestController
@RequestMapping("/public/tag")
public class VideoTagPublicController {

    @Autowired
    private VideoTagService videoTagService;

    @Operation(summary = "获取视频标签（当用户上传视频时可以选中这些视频标签）")
    @GetMapping("/simple")
    public Result<List<String>> getSimpleVideoTags() {
        return Result.success(videoTagService.getSimpleVideoTags());
    }
}
