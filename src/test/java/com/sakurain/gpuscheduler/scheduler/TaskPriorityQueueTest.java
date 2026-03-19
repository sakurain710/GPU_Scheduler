package com.sakurain.gpuscheduler.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis优先队列集成测试 — 需要Redis运行
 */
@SpringBootTest
class TaskPriorityQueueTest {

    @Autowired
    private TaskPriorityQueue queue;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 清空测试队列
        redisTemplate.delete("gpu:task:queue");
    }

    @Test
    void enqueueAndDequeue() {
        queue.enqueue(1L, 5.0);
        queue.enqueue(2L, 8.0);
        queue.enqueue(3L, 3.0);

        assertEquals(3, queue.size());

        // 高优先级先出队
        assertEquals(2L, queue.dequeue());
        assertEquals(1L, queue.dequeue());
        assertEquals(3L, queue.dequeue());
        assertNull(queue.dequeue());
    }

    @Test
    void peek() {
        queue.enqueue(10L, 7.0);
        queue.enqueue(20L, 2.0);

        assertEquals(10L, queue.peek());
        // peek不移除
        assertEquals(2, queue.size());
    }

    @Test
    void remove() {
        queue.enqueue(1L, 5.0);
        queue.enqueue(2L, 8.0);

        assertTrue(queue.remove(1L));
        assertFalse(queue.remove(999L));
        assertEquals(1, queue.size());
        assertEquals(2L, queue.dequeue());
    }

    @Test
    void contains() {
        queue.enqueue(1L, 5.0);

        assertTrue(queue.contains(1L));
        assertFalse(queue.contains(999L));
    }

    @Test
    void updateScore() {
        queue.enqueue(1L, 3.0);
        queue.enqueue(2L, 5.0);

        // 任务1老化后优先级提升到10
        queue.updateScore(1L, 10.0);

        // 现在任务1应该先出队
        assertEquals(1L, queue.dequeue());
    }

    @Test
    void refreshScores() {
        queue.enqueue(1L, 3.0);
        queue.enqueue(2L, 5.0);

        // 模拟老化：所有任务优先级+10
        queue.refreshScores(taskId -> {
            if (taskId == 1L) return 13.0;
            return 15.0;
        });

        // 任务2仍然优先级更高
        assertEquals(2L, queue.dequeue());
        assertEquals(1L, queue.dequeue());
    }

    @Test
    void emptyQueueOperations() {
        assertNull(queue.dequeue());
        assertNull(queue.peek());
        assertEquals(0, queue.size());
        assertTrue(queue.allMembers().isEmpty());
    }
}
