package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.multi.UserFriendWithBasicInfo;
import com.jiake.jk.user.pojo.response.UserFriendResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface FollowMapStruct {
    FollowMapStruct INSTANCE = Mappers.getMapper(FollowMapStruct.class);

    UserFriendResponse toUserFriendResponse(UserFriendWithBasicInfo userFriendWithBasicInfo);
}
