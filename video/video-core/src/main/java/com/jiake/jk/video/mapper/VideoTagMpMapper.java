package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.multi.VideoTagMp;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoTagMpMapper extends BaseMapper<VideoTagMp> {
    void insertBatch(Long video_id, List<Long> allTagList);
}
