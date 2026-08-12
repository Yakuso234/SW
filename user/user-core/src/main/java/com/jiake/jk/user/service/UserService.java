package com.jiake.jk.user.service;

import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import com.jiake.jk.user.pojo.response.UserSearchResponse;

import java.util.List;
import java.util.Map;

public interface UserService {
    String getName(String id);

    Map<Long, String> getNameBatch(List<Long> idList);

    List<UserInfoInListResponse> getUserInfoInList(List<Long> idList);

    List<UserSearchResponse> search(Long userId, String query);
}
