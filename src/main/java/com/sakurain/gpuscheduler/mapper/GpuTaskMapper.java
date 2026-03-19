package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.GpuTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GpuTaskMapper extends BaseMapper<GpuTask> {
}
