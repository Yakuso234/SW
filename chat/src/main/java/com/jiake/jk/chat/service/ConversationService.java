package com.jiake.jk.chat.service;

import com.jiake.jk.chat.response.ConversationMessageResponse;
import com.jiake.jk.chat.response.RecentContactResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface ConversationService {
    List<RecentContactResponse> getRecentContacts(Long userId);

    List<ConversationMessageResponse> getConversationMessages(Long userId, Long conversationId, Long lastMinId) throws BadRequestException;
}
