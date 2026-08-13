package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.VideoFeedInbox;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoFeedInboxMapper extends BaseMapper<VideoFeedInbox> {
    int insertIgnoreBatch(List<VideoFeedInbox> inboxItems);
}
