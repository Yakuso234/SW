package com.jiake.jk.videoprocessor.serivce.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.videoprocessor.entity.UserVideoInteraction;
import com.jiake.jk.videoprocessor.mapper.UserVideoInteractionMapper;
import com.jiake.jk.videoprocessor.serivce.UserVideoInteractionService;
import org.springframework.stereotype.Service;

@Service
public class UserVideoInteractionServiceImpl extends ServiceImpl<UserVideoInteractionMapper, UserVideoInteraction> implements UserVideoInteractionService {
}
