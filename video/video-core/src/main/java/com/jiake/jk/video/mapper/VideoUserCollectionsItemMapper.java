package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.VideoUserCollectionsItem;
import com.jiake.jk.video.pojo.entity.multi.VideoCollectionsWithVideo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VideoUserCollectionsItemMapper extends BaseMapper<VideoUserCollectionsItem> {
    List<VideoCollectionsWithVideo> selectCollectionsItemList(Long collectionsId, Long lastMinId);
}
