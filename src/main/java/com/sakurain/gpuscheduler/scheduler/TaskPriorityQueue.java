package com.sakurain.gpuscheduler.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 基于Redis ZSet的任务优先队列
 * <p>
 * score = -(base_priority + age_bonus)，取负值使高优先级排在前面（ZSet默认升序）。
 * member = taskId 的字符串形式。
 * <p>
 * 动态老化：score在入队时按当时的 effective_priority 计算，
 * 调度器定期调用 refreshScores() 重新计算所有等待任务的score。
 */
@Slf4j
@Component
public class TaskPriorityQueue {

    private static final String QUEUE_KEY = "gpu:task:queue";

    private final ZSetOperations<String, String> zSetOps;
    private final RedisTemplate<String, String> redisTemplate;

    public TaskPriorityQueue(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOps = redisTemplate.opsForZSet();
    }

    /**
     * 入队 — score取负值，使高优先级任务排在ZSet最前
     *
     * @param taskId            任务ID
     * @param effectivePriority 有效优先级（base_priority + aging bonus）
     */
    public void enqueue(Long taskId, double effectivePriority) {
        String member = taskId.toString();
        zSetOps.add(QUEUE_KEY, member, -effectivePriority);
        log.info("任务入队: taskId={}, effectivePriority={}", taskId, effectivePriority);
    }

    /**
     * 出队 — 弹出优先级最高的任务ID（score最小，即负值绝对值最大）
     *
     * @return 任务ID，队列为空时返回null
     */
    public Long dequeue() {
        Set<ZSetOperations.TypedTuple<String>> tuples = zSetOps.popMin(QUEUE_KEY, 1);
        if (tuples == null || tuples.isEmpty()) {
            return null;
        }
        ZSetOperations.TypedTuple<String> top = tuples.iterator().next();
        Long taskId = Long.valueOf(Objects.requireNonNull(top.getValue()));
        log.info("任务出队: taskId={}, score={}", taskId, top.getScore());
        return taskId;
    }

    /**
     * 查看队首任务但不移除
     */
    public Long peek() {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, 0);
        if (members == null || members.isEmpty()) {
            return null;
        }
        return Long.valueOf(members.iterator().next());
    }

    /**
     * 从队列中移除指定任务（取消或状态转换时调用）
     */
    public boolean remove(Long taskId) {
        Long removed = zSetOps.remove(QUEUE_KEY, taskId.toString());
        if (removed != null && removed > 0) {
            log.info("任务移出队列: taskId={}", taskId);
            return true;
        }
        return false;
    }

    /**
     * 更新任务的score（老化刷新时调用）
     */
    public void updateScore(Long taskId, double newEffectivePriority) {
        zSetOps.add(QUEUE_KEY, taskId.toString(), -newEffectivePriority);
    }

    /**
     * 批量刷新所有队列中任务的score — 由定时调度器调用
     *
     * @param scoreProvider 根据taskId计算新的effectivePriority
     */
    public void refreshScores(java.util.function.LongToDoubleFunction scoreProvider) {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (String member : members) {
            long taskId = Long.parseLong(member);
            double newPriority = scoreProvider.applyAsDouble(taskId);
            zSetOps.add(QUEUE_KEY, member, -newPriority);
        }
        log.debug("队列score刷新完成, 任务数={}", members.size());
    }

    /**
     * 队列中的任务数量
     */
    public long size() {
        Long sz = zSetOps.size(QUEUE_KEY);
        return sz != null ? sz : 0;
    }

    /**
     * 查询指定任务是否在队列中
     */
    public boolean contains(Long taskId) {
        Double score = zSetOps.score(QUEUE_KEY, taskId.toString());
        return score != null;
    }

    /**
     * 获取所有排队中的任务ID（按优先级降序）
     */
    public Set<String> allMembers() {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, -1);
        return members != null ? members : Collections.emptySet();
    }
}
