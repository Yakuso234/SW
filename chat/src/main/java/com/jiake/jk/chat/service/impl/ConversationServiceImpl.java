package com.jiake.jk.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiake.jk.chat.entity.ChatConversation;
import com.jiake.jk.chat.entity.ChatMessage;
import com.jiake.jk.chat.entity.ChatMessageMedia;
import com.jiake.jk.chat.entity.multi.RecentContact;
import com.jiake.jk.chat.mapper.ChatConversationMapper;
import com.jiake.jk.chat.mapper.ChatMessageMediaMapper;
import com.jiake.jk.chat.mapper.ChatMessageMapper;
import com.jiake.jk.chat.mapstruct.ConversationMapStruct;
import com.jiake.jk.chat.response.ChatMessageMediaResponse;
import com.jiake.jk.chat.response.ConversationMessageResponse;
import com.jiake.jk.chat.response.RecentContactResponse;
import com.jiake.jk.chat.service.ConversationService;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.user.feign.UserPrivateClient;
import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private ChatConversationMapper chatConversationMapper;
    @Autowired
    private UserPrivateClient userPrivateClient;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private ChatMessageMediaMapper chatMessageMediaMapper;
    @Autowired
    private AWSUtils awsUtils;

    @Override
    public List<RecentContactResponse> getRecentContacts(Long userId) {
        List<RecentContact> recentContactList = chatConversationMapper.selectRecentContacts(userId);
        if (recentContactList.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换实体类
        List<RecentContactResponse> recentContactResponseList = new ArrayList<>(recentContactList.size());
        for (RecentContact recentContact : recentContactList) {
            recentContactResponseList.add(ConversationMapStruct.INSTANCE.toRecentContactResponse(recentContact));
        }

        /* 完善联系人数据 */
        // 提取出contactId
        List<Long> contactIdList = recentContactResponseList.stream().map(RecentContactResponse::getContactId).toList();
        // 记录原数组索引
        List<Integer> sortedIndices = IntStream.range(0, contactIdList.size())
                .boxed()
                .sorted(Comparator.comparingLong(contactIdList::get))
                .toList();
        // 获取排序后的数组
        List<Long> sortedContactIdList = sortedIndices.stream().map(contactIdList::get).toList();

        List<UserInfoInListResponse> responseList = userPrivateClient.getUserInfoInList(sortedContactIdList).getData();
        for (int i = 0; i < responseList.size(); i++) {
            RecentContactResponse recentContactResponse = recentContactResponseList.get(sortedIndices.get(i));
            UserInfoInListResponse userInfoInListResponse = responseList.get(i);
            recentContactResponse.setContactName(userInfoInListResponse.getName());
            recentContactResponse.setContactAvatar(userInfoInListResponse.getAvatarUrl());
        }

        return recentContactResponseList;
    }

    @Override
    public List<ConversationMessageResponse> getConversationMessages(Long userId, Long conversationId, Long lastMinId) throws BadRequestException {
        // 鉴权
        ChatConversation conversation = chatConversationMapper.selectBothIdById(conversationId);
        if (!(userId.equals(conversation.getUser1Id()) || userId.equals(conversation.getUser2Id()))) {
            throw new BadRequestException("你没有权限！");
        }

        List<ChatMessage> chatMessageList = chatMessageMapper.selectByConversationId(conversationId, lastMinId);
        Map<Long, ChatMessageMediaResponse> mediaMap = getMessageMediaMap(chatMessageList);

        List<ConversationMessageResponse> conversationMessageResponseList = new ArrayList<>(chatMessageList.size());
        for (ChatMessage chatMessage : chatMessageList) {
            ConversationMessageResponse conversationMessage = ConversationMapStruct.INSTANCE.toConversationMessageResponse(chatMessage);
            conversationMessage.setIsUser(userId.equals(chatMessage.getSenderId()));
            conversationMessage.setMedia(mediaMap.get(chatMessage.getId()));

            conversationMessageResponseList.add(conversationMessage);
        }

        // 更新未读数
        if (userId.equals(conversation.getUser1Id())) {
           chatConversationMapper.update(new LambdaUpdateWrapper<ChatConversation>()
                   .set(ChatConversation::getUser1UnreadCount, 0)
                   .eq(ChatConversation::getId, conversationId)
                   .eq(ChatConversation::getUser1Id, userId));
        } else {
            chatConversationMapper.update(new LambdaUpdateWrapper<ChatConversation>()
                    .set(ChatConversation::getUser2UnreadCount, 0)
                    .eq(ChatConversation::getId, conversationId)
                    .eq(ChatConversation::getUser2Id, userId));
        }

        return conversationMessageResponseList;
    }

    private Map<Long, ChatMessageMediaResponse> getMessageMediaMap(List<ChatMessage> chatMessageList) {
        List<Long> messageIdList = chatMessageList.stream()
                .filter(chatMessage -> chatMessage.getMessageType() == ChatMessage.MessageType.IMAGE
                        || chatMessage.getMessageType() == ChatMessage.MessageType.VIDEO
                        || chatMessage.getMessageType() == ChatMessage.MessageType.VOICE)
                .map(ChatMessage::getId)
                .toList();
        if (messageIdList.isEmpty()) {
            return Map.of();
        }

        List<ChatMessageMedia> mediaList = chatMessageMediaMapper.selectList(new LambdaQueryWrapper<ChatMessageMedia>()
                .in(ChatMessageMedia::getMessageId, messageIdList));

        return mediaList.stream()
                .collect(Collectors.toMap(
                        ChatMessageMedia::getMessageId,
                        this::toMediaResponse,
                        (first, ignored) -> first
                ));
    }

    private ChatMessageMediaResponse toMediaResponse(ChatMessageMedia media) {
        return new ChatMessageMediaResponse(
                media.getId(),
                media.getMediaType(),
                awsUtils.generateAccessUrl(media.getUrl()),
                StringUtils.hasText(media.getCoverUrl()) ? awsUtils.generateAccessUrl(media.getCoverUrl()) : null
        );
    }
}
