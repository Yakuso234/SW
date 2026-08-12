package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.UserAddress;
import com.jiake.jk.user.pojo.response.AddressResponse;
import com.jiake.jk.user.pojo.request.PutAddressRequest;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AddressMapstruct {
    AddressMapstruct INSTANCE = Mappers.getMapper(AddressMapstruct.class);

    UserAddress putAddressRequestToUserAddress(PutAddressRequest putAddressRequest);
    AddressResponse userAddressToAddressResponse(UserAddress userAddress);
}
