package com.jiake.jk.admin.mapstruct;

import com.jiake.jk.admin.entity.Admin;
import com.jiake.jk.admin.request.PostAccountRequest;
import com.jiake.jk.admin.response.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AccountMapStruct {
    AccountMapStruct INSTANCE = Mappers.getMapper(AccountMapStruct.class);

    AccountResponse adminToAccountResponse(Admin admin);

    Admin addAccountRequestToAdmin(PostAccountRequest postAccountRequest);
}
