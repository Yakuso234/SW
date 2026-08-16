package com.jiake.jk.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.user.mapper.FollowMapper;
import com.jiake.jk.user.pojo.entity.UserFollow;
import com.jiake.jk.user.pojo.response.FollowListResponse;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import com.jiake.jk.user.pojo.response.FollowUserResponse;
import com.jiake.jk.user.pojo.response.UserFriendResponse;
import com.jiake.jk.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final SnowflakeUtils snowflakeUtils;
    private final AWSUtils awsUtils;


    @Override
    @Transactional
    public void follow(Long followerId, Long followeeId) throws BadRequestException {
        // 不能关注自己
        if (followerId.equals(followeeId)) {
            throw new BadRequestException("不能关注自己！");
        }

        UserFollow userFollow = new UserFollow();
        userFollow.setId(snowflakeUtils.nextId());
        userFollow.setFollowerId(followerId);
        userFollow.setFolloweeId(followeeId);
        userFollow.setCreatedTime(LocalDateTime.now());

        // 如果已经插入则忽略
        if (!followMapper.insertIgnore(userFollow)) {
            return;
        }

        // 关注关系只维护社交图谱。互关不应隐式创建聊天会话或好友记录，
        // 否则 User 服务会把内容社交与 Chat 领域强耦合，且无法独立演进。
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        // 尝试删除关注记录
        if (!followMapper.deleteByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return;
        }

        // 取消关注同样只影响关注关系；聊天领域若需要解除关系，应由自身业务显式处理。
    }

    @Override
    public List<Boolean> getFollowStatus(Long followerId, List<Long> followeeIdList) {
        return followMapper.selectFollowStatusBatch(followerId, followeeIdList);
    }

    @Override
    public List<UserFriendResponse> getFriends(Long user) {
//        return FollowMapStruct.INSTANCE.toUserFriendResponse()
        return null;
    }

    @Override
    public FollowListResponse getFollowingList(Long userId, Long lastId) {
        return getFollowList(followMapper.selectFollowingPage(userId, lastId));
    }

    @Override
    public FollowListResponse getFollowerList(Long userId, Long lastId) {
        return getFollowList(followMapper.selectFollowerPage(userId, lastId));
    }

    @Override
    public FollowerIdPageResponse getFollowerIdPage(Long followeeId, Long lastFollowId, int pageSize) {
        int limit = Math.min(Math.max(pageSize, 1), 500) + 1;
        LambdaQueryWrapper<UserFollow> query = new LambdaQueryWrapper<UserFollow>()
                .select(UserFollow::getId, UserFollow::getFollowerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .orderByDesc(UserFollow::getId)
                .last("LIMIT " + limit);
        if (lastFollowId != null) {
            query.lt(UserFollow::getId, lastFollowId);
        }
        List<UserFollow> rows = followMapper.selectList(query);
        boolean hasMore = rows.size() == limit;
        if (hasMore) {
            rows = rows.subList(0, limit - 1);
        }
        Long nextFollowId = hasMore ? rows.get(rows.size() - 1).getId() : null;
        return new FollowerIdPageResponse(rows.stream().map(UserFollow::getFollowerId).toList(), nextFollowId, hasMore);
    }

    private FollowListResponse getFollowList(List<FollowUserResponse> list) {
        list.forEach(item -> item.setAvatarUrl(awsUtils.generateAccessUrl(item.getAvatarUrl())));

        FollowListResponse response = new FollowListResponse();
        response.setList(list);
        return response;
    }

}
