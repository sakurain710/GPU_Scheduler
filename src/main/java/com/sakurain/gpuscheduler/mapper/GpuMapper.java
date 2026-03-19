package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.Gpu;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GpuMapper extends BaseMapper<Gpu> {
}
