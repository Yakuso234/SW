package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.video.mapper.VideoUserCollectionsItemMapper;
import com.jiake.jk.video.pojo.entity.VideoUserCollectionsItem;
import com.jiake.jk.video.service.CollectionsItemService;
import org.springframework.stereotype.Service;

@Service
public class CollectionsItemServiceImpl extends ServiceImpl<VideoUserCollectionsItemMapper, VideoUserCollectionsItem> implements CollectionsItemService {
}
