package com.jiake.jk.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jiake.jk.product.mapper.SpecValueMapper;
import com.jiake.jk.product.pojo.model.entity.SpecValue;
import com.jiake.jk.product.service.SpecValueService;
import org.springframework.stereotype.Service;

@Service
public class SpecValueServiceImpl extends ServiceImpl<SpecValueMapper, SpecValue> implements SpecValueService {
}
