package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.TaskDlq;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskDlqMapper extends BaseMapper<TaskDlq> {
}
