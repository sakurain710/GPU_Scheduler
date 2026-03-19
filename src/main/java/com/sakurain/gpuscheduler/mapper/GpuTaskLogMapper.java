package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.GpuTaskLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GpuTaskLogMapper extends BaseMapper<GpuTaskLog> {
}
