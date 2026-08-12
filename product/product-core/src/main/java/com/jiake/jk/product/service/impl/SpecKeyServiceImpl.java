package com.jiake.jk.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.product.mapper.SpecKeyMapper;
import com.jiake.jk.product.pojo.model.entity.SpecKey;
import com.jiake.jk.product.service.SpecKeyService;
import org.springframework.stereotype.Service;

@Service
public class SpecKeyServiceImpl extends ServiceImpl<SpecKeyMapper, SpecKey> implements SpecKeyService {
}
