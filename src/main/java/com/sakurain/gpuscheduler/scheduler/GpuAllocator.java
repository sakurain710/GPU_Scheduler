package com.sakurain.gpuscheduler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * GPU分配器 — BestFit算法
 * <p>
 * 最小化显存碎片：选择满足任务需求的最小显存GPU
 * 考虑异构GPU池（不同品牌/型号/显存）
 */
@Slf4j
@Component
public class GpuAllocator {

    private final GpuMapper gpuMapper;

    public GpuAllocator(GpuMapper gpuMapper) {
        this.gpuMapper = gpuMapper;
    }

    /**
     * BestFit算法 — 为任务分配最合适的GPU
     * <p>
     * 策略：
     * 1. 过滤：只考虑IDLE状态且显存 >= minMemoryGb的GPU
     * 2. 排序：按显存升序（最小的满足需求的GPU优先）
     * 3. 选择：返回第一个（显存最小的）
     *
     * @param task 待分配的任务
     * @return 分配的GPU，如果没有可用GPU则返回Optional.empty()
     */
    public Optional<Gpu> allocate(GpuTask task) {
        if (task.getMinMemoryGb() == null) {
            log.warn("任务{}没有指定最小显存需求，无法分配GPU", task.getId());
            return Optional.empty();
        }

        // 查询所有IDLE状态且显存满足需求的GPU
        QueryWrapper<Gpu> query = new QueryWrapper<>();
        query.eq("status", GpuStatus.IDLE.getCode())
                .ge("memory_gb", task.getMinMemoryGb())
                .orderByAsc("memory_gb");  // BestFit: 显存升序，最小的优先

        List<Gpu> candidates = gpuMapper.selectList(query);

        if (candidates.isEmpty()) {
            log.warn("没有可用GPU满足任务{}的需求: minMemoryGb={}",
                    task.getId(), task.getMinMemoryGb());
            return Optional.empty();
        }

        // BestFit: 选择显存最小的GPU（已按memory_gb升序排序）
        Gpu selected = candidates.get(0);
        log.info("BestFit分配: 任务{}分配到GPU[{}] {}, 显存{}GB (需求{}GB)",
                task.getId(), selected.getId(), selected.getName(),
                selected.getMemoryGb(), task.getMinMemoryGb());

        return Optional.of(selected);
    }

    /**
     * 查找所有可用的GPU（IDLE状态）
     *
     * @return 可用GPU列表
     */
    public List<Gpu> findAvailableGpus() {
        QueryWrapper<Gpu> query = new QueryWrapper<>();
        query.eq("status", GpuStatus.IDLE.getCode())
                .orderByAsc("memory_gb");
        return gpuMapper.selectList(query);
    }

    /**
     * 查找满足最小显存需求的GPU数量
     *
     * @param minMemoryGb 最小显存需求
     * @return 满足需求的GPU数量
     */
    public long countAvailableGpus(BigDecimal minMemoryGb) {
        QueryWrapper<Gpu> query = new QueryWrapper<>();
        query.eq("status", GpuStatus.IDLE.getCode())
                .ge("memory_gb", minMemoryGb);
        return gpuMapper.selectCount(query);
    }

    /**
     * 检查是否有足够的GPU资源
     *
     * @param task 待分配的任务
     * @return true如果有可用GPU，false否则
     */
    public boolean hasAvailableGpu(GpuTask task) {
        if (task.getMinMemoryGb() == null) {
            return false;
        }
        return countAvailableGpus(task.getMinMemoryGb()) > 0;
    }
}
