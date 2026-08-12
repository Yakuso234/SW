package com.jiake.jk.chat.mapstruct;

import com.jiake.jk.chat.entity.ChatMessage;
import com.jiake.jk.chat.entity.multi.RecentContact;
import com.jiake.jk.chat.response.ConversationMessageResponse;
import com.jiake.jk.chat.response.RecentContactResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ConversationMapStruct {
    ConversationMapStruct INSTANCE = Mappers.getMapper(ConversationMapStruct.class);

    RecentContactResponse toRecentContactResponse(RecentContact recentContact);
    ConversationMessageResponse toConversationMessageResponse(ChatMessage chatMessage);
}
