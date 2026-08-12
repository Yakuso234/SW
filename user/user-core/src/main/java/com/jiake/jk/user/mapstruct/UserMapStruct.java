package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.User;
import com.jiake.jk.user.pojo.response.ProfileResponse;
import com.jiake.jk.user.pojo.response.UserInfoInListResponse;
import com.jiake.jk.user.pojo.response.UserSearchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserMapStruct {
    UserMapStruct INSTANCE = Mappers.getMapper(UserMapStruct.class);

    UserInfoInListResponse toUserInfoInListResponse(User user);

    UserSearchResponse toUserSearchResponse(User user);

    ProfileResponse toProfileResponse(User user);
}
