package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.Gpu;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GpuMapper extends BaseMapper<Gpu> {

    /**
     * 原子抢占GPU：仅当当前状态为IDLE时更新为BUSY
     */
    @Update("UPDATE gpu SET status = #{busyStatus}, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{idleStatus} AND deleted_at IS NULL")
    int tryMarkBusy(@Param("gpuId") Long gpuId,
                    @Param("idleStatus") Integer idleStatus,
                    @Param("busyStatus") Integer busyStatus);

    /**
     * 原子释放GPU：仅当当前状态为BUSY时更新为IDLE
     */
    @Update("UPDATE gpu SET status = #{idleStatus}, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{busyStatus} AND deleted_at IS NULL")
    int tryMarkIdle(@Param("gpuId") Long gpuId,
                    @Param("busyStatus") Integer busyStatus,
                    @Param("idleStatus") Integer idleStatus);

    /**
     * 原子下线GPU：仅当当前状态为BUSY时更新为OFFLINE。
     */
    @Update("UPDATE gpu SET status = #{offlineStatus}, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{busyStatus} AND deleted_at IS NULL")
    int tryMarkOfflineFromBusy(@Param("gpuId") Long gpuId,
                               @Param("busyStatus") Integer busyStatus,
                               @Param("offlineStatus") Integer offlineStatus);

    /**
     * 原子上线GPU：仅当当前状态为OFFLINE时恢复为IDLE。
     */
    @Update("UPDATE gpu SET status = #{idleStatus}, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{offlineStatus} AND deleted_at IS NULL")
    int tryMarkIdleFromOffline(@Param("gpuId") Long gpuId,
                               @Param("offlineStatus") Integer offlineStatus,
                               @Param("idleStatus") Integer idleStatus);
}
