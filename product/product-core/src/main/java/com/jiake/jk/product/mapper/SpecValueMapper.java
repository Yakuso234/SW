package com.jiake.jk.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.product.pojo.model.entity.SpecValue;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SpecValueMapper extends BaseMapper<SpecValue> {
    @Insert("insert into spec_value (value_id, value_name) values (#{id}, #{value})")
    void insert(Long id, String value);

    void deleteBatch(List<Long> valueIdList);
}
