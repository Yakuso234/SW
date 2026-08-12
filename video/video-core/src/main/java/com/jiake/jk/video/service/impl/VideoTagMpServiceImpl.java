package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.video.mapper.VideoTagMpMapper;
import com.jiake.jk.video.pojo.entity.multi.VideoTagMp;
import com.jiake.jk.video.service.VideoTagMpService;
import org.springframework.stereotype.Service;

@Service
public class VideoTagMpServiceImpl extends ServiceImpl<VideoTagMpMapper, VideoTagMp> implements VideoTagMpService {
}
