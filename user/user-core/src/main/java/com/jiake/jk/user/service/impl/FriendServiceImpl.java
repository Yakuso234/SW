package com.jiake.jk.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.user.mapper.FriendMapper;
import com.jiake.jk.user.pojo.entity.UserFriend;
import com.jiake.jk.user.service.FriendService;
import org.springframework.stereotype.Service;

@Service
public class FriendServiceImpl extends ServiceImpl<FriendMapper, UserFriend> implements FriendService {
}
