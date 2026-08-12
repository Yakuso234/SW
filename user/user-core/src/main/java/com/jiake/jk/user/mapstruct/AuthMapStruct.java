package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.User;
import com.jiake.jk.user.pojo.request.RegisterRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AuthMapStruct {
    AuthMapStruct INSTANCE = Mappers.getMapper(AuthMapStruct.class);

    User registerRequestToUser(RegisterRequest registerRequest);
}
