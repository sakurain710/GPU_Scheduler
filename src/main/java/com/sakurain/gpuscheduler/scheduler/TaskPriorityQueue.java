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
 * score = -effectivePriority * PRIORITY_SCALE + sequence
 * sequence越小表示入队越早，用于同优先级下FIFO
 */
@Slf4j
@Component
public class TaskPriorityQueue {

    private static final String QUEUE_KEY = "gpu:task:queue";
    private static final String QUEUE_SEQ_MAP_KEY = "gpu:task:queue:seq:map";
    private static final String QUEUE_SEQ_COUNTER_KEY = "gpu:task:queue:seq:counter";
    private static final double PRIORITY_SCALE = 1_000_000_000D;

    private final ZSetOperations<String, String> zSetOps;
    private final RedisTemplate<String, String> redisTemplate;

    public TaskPriorityQueue(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOps = redisTemplate.opsForZSet();
    }

    public void enqueue(Long taskId, double effectivePriority) {
        String member = taskId.toString();
        long seq = resolveSequence(member);
        double score = composeScore(effectivePriority, seq);
        zSetOps.add(QUEUE_KEY, member, score);
        log.info("任务入队: taskId={}, effectivePriority={}, seq={}", taskId, effectivePriority, seq);
    }

    public Long dequeue() {
        Set<ZSetOperations.TypedTuple<String>> tuples = zSetOps.popMin(QUEUE_KEY, 1);
        if (tuples == null || tuples.isEmpty()) {
            return null;
        }
        ZSetOperations.TypedTuple<String> top = tuples.iterator().next();
        String member = Objects.requireNonNull(top.getValue());
        Long taskId = Long.valueOf(member);
        redisTemplate.opsForHash().delete(QUEUE_SEQ_MAP_KEY, member);
        log.info("任务出队: taskId={}, score={}", taskId, top.getScore());
        return taskId;
    }

    public Long peek() {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, 0);
        if (members == null || members.isEmpty()) {
            return null;
        }
        return Long.valueOf(members.iterator().next());
    }

    public boolean remove(Long taskId) {
        String member = taskId.toString();
        Long removed = zSetOps.remove(QUEUE_KEY, member);
        redisTemplate.opsForHash().delete(QUEUE_SEQ_MAP_KEY, member);
        if (removed != null && removed > 0) {
            log.info("任务移出队列: taskId={}", taskId);
            return true;
        }
        return false;
    }

    public void updateScore(Long taskId, double newEffectivePriority) {
        String member = taskId.toString();
        long seq = resolveSequence(member);
        zSetOps.add(QUEUE_KEY, member, composeScore(newEffectivePriority, seq));
    }

    public void refreshScores(java.util.function.LongToDoubleFunction scoreProvider) {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (String member : members) {
            long taskId = Long.parseLong(member);
            double newPriority = scoreProvider.applyAsDouble(taskId);
            long seq = resolveSequence(member);
            zSetOps.add(QUEUE_KEY, member, composeScore(newPriority, seq));
        }
        log.debug("队列score刷新完成, 任务数{}", members.size());
    }

    public long size() {
        Long sz = zSetOps.size(QUEUE_KEY);
        return sz != null ? sz : 0;
    }

    public boolean contains(Long taskId) {
        Double score = zSetOps.score(QUEUE_KEY, taskId.toString());
        return score != null;
    }

    public Set<String> allMembers() {
        Set<String> members = zSetOps.range(QUEUE_KEY, 0, -1);
        return members != null ? members : Collections.emptySet();
    }

    private long resolveSequence(String member) {
        Object existing = redisTemplate.opsForHash().get(QUEUE_SEQ_MAP_KEY, member);
        if (existing != null) {
            return Long.parseLong(existing.toString());
        }
        Long seq = redisTemplate.opsForValue().increment(QUEUE_SEQ_COUNTER_KEY);
        long value = seq != null ? seq : System.nanoTime();
        redisTemplate.opsForHash().put(QUEUE_SEQ_MAP_KEY, member, String.valueOf(value));
        return value;
    }

    private double composeScore(double effectivePriority, long sequence) {
        return -effectivePriority * PRIORITY_SCALE + sequence;
    }
}
