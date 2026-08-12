package com.jiake.jk.product.mapper.multi;

import com.jiake.jk.product.pojo.model.multi.SkuSpecInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SkuMapper {

     List<Long> selectMerchantIdBySkuIds(List<Long> skuIdList);

     List<SkuSpecInfo> selectSpecsBySkuIds(List<Long> skuIdList);
}
