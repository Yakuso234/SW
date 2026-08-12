package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.User;
import com.jiake.jk.user.pojo.response.ProfileBasicResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProfileMapStruct {
    ProfileMapStruct INSTANCE = Mappers.getMapper(ProfileMapStruct.class);

    ProfileBasicResponse userToBasicProfileResponse(User user);
}
