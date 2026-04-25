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
    @Update("UPDATE gpu SET status = #{busyStatus}, allocated_memory_gb = #{allocatedMemoryGb}, " +
            "offline_reason = NULL, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{idleStatus} AND deleted_at IS NULL")
    int tryMarkBusy(@Param("gpuId") Long gpuId,
                    @Param("idleStatus") Integer idleStatus,
                    @Param("busyStatus") Integer busyStatus,
                    @Param("allocatedMemoryGb") java.math.BigDecimal allocatedMemoryGb);

    /**
     * 原子释放GPU：仅当当前状态为BUSY时更新为IDLE
     */
    @Update("UPDATE gpu SET status = #{idleStatus}, allocated_memory_gb = 0, " +
            "offline_reason = NULL, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{busyStatus} AND deleted_at IS NULL")
    int tryMarkIdle(@Param("gpuId") Long gpuId,
                    @Param("busyStatus") Integer busyStatus,
                    @Param("idleStatus") Integer idleStatus);

    /**
     * 原子下线GPU：仅当当前状态为BUSY时更新为OFFLINE。
     */
    @Update("UPDATE gpu SET status = #{offlineStatus}, allocated_memory_gb = 0, " +
            "offline_reason = #{offlineReason}, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{busyStatus} AND deleted_at IS NULL")
    int tryMarkOfflineFromBusy(@Param("gpuId") Long gpuId,
                               @Param("busyStatus") Integer busyStatus,
                               @Param("offlineStatus") Integer offlineStatus,
                               @Param("offlineReason") String offlineReason);

    /**
     * 原子上线GPU：仅当当前状态为OFFLINE时恢复为IDLE。
     */
    @Update("UPDATE gpu SET status = #{idleStatus}, offline_reason = NULL, updated_at = NOW() " +
            "WHERE id = #{gpuId} AND status = #{offlineStatus} AND deleted_at IS NULL")
    int tryMarkIdleFromOffline(@Param("gpuId") Long gpuId,
                               @Param("offlineStatus") Integer offlineStatus,
                               @Param("idleStatus") Integer idleStatus);

    /**
     * 写入最近心跳时间。
     */
    @Update("UPDATE gpu SET last_heartbeat_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{gpuId} AND deleted_at IS NULL")
    int updateHeartbeat(@Param("gpuId") Long gpuId);
}
