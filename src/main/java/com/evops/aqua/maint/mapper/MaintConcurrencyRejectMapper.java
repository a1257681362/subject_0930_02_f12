package com.evops.aqua.maint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.aqua.maint.entity.MaintConcurrencyReject;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaintConcurrencyRejectMapper extends BaseMapper<MaintConcurrencyReject> {
}
