package com.jiake.jk.user.mapstruct;

import com.jiake.jk.user.pojo.entity.Merchant;
import com.jiake.jk.user.pojo.response.MerchantBasicDataResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MerchantMapStruct {
    MerchantMapStruct INSTANCE = Mappers.getMapper(MerchantMapStruct.class);

    MerchantBasicDataResponse toMerchantBasicDataResponse(Merchant merchant);
}
