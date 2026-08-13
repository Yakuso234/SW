package com.jiake.jk.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.user.mapper.ChatConversationMapper;
import com.jiake.jk.user.mapper.FollowMapper;
import com.jiake.jk.user.mapper.FriendMapper;
import com.jiake.jk.user.pojo.entity.ChatConversation;
import com.jiake.jk.user.pojo.entity.UserFollow;
import com.jiake.jk.user.pojo.entity.UserFriend;
import com.jiake.jk.user.pojo.entity.UserNewFriendMessage;
import com.jiake.jk.user.pojo.response.FollowListResponse;
import com.jiake.jk.user.pojo.response.FollowerIdPageResponse;
import com.jiake.jk.user.pojo.response.FollowUserResponse;
import com.jiake.jk.user.pojo.response.UserFriendResponse;
import com.jiake.jk.user.service.FollowService;
import com.jiake.jk.user.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final SnowflakeUtils snowflakeUtils;
    private final FriendMapper friendMapper;
    private final FriendService friendService;
    private final ChatConversationMapper chatConversationMapper;
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

        // 查询是否有反向关系
        if (!followMapper.selectIsRelationExist(followeeId, followerId)) {
            return;
        }

        // 插入好友记录
        UserFriend userFriend1 = new UserFriend();
        userFriend1.setUserId(followerId);
        userFriend1.setFriendId(followeeId);
        UserFriend userFriend2 = new UserFriend();
        userFriend2.setUserId(followeeId);
        userFriend2.setFriendId(followerId);

        friendService.saveBatch(List.of(userFriend1, userFriend2));

        // 插入会话记录（临时做法）
        ChatConversation chatConversation = new ChatConversation();
        chatConversation.setUser1Id(followerId);
        chatConversation.setUser2Id(followeeId);
        chatConversation.setUser1UnreadCount(0);
        chatConversation.setUser2UnreadCount(0);
        chatConversation.setUpdatedTime(LocalDateTime.now());
        chatConversation.setCreatedTime(LocalDateTime.now());

        chatConversationMapper.insert(chatConversation);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        // 尝试删除关注记录
        if (!followMapper.deleteByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return;
        }

        // 查询是否有反向关系
        if (!followMapper.selectIsRelationExist(followeeId, followerId)) {
            return;
        }

        // 有反向关系，删除好友记录
        friendMapper.delete(new LambdaQueryWrapper<UserFriend>()
                .and(wrapper -> wrapper
                        .eq(UserFriend::getUserId, followerId)
                        .eq(UserFriend::getFriendId, followeeId)
                )
                .or(wrapper -> wrapper
                        .eq(UserFriend::getUserId, followeeId)
                        .eq(UserFriend::getFriendId, followerId)
                )
        );

        // 删除会话记录（临时做法）
        chatConversationMapper.delete(new LambdaQueryWrapper<ChatConversation>()
                .and(wrapper -> wrapper
                        .eq(ChatConversation::getUser1Id, followerId)
                        .eq(ChatConversation::getUser2Id, followeeId)
                )
                .or(wrapper -> wrapper.eq(ChatConversation::getUser1Id, followeeId)
                        .eq(ChatConversation::getUser2Id, followerId)
                )
        );
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
