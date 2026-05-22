package com.itheima.agent.service;

import com.itheima.agent.config.RabbitMQConfig;
import com.itheima.agent.dto.KnowledgeRebuildStatus;
import com.itheima.agent.dto.KnowledgeRebuildTask;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class KnowledgeRebuildProducer {

    private static final String STATUS_KEY_PREFIX = "knowledge:rebuild:status:";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, KnowledgeRebuildStatus> localStatusCache = new ConcurrentHashMap<>();

    /** 启动时清除所有残留重建状态，防止重启后重复消费旧任务 */
    @PostConstruct
    public void cleanupOnStartup() {
        try {
            // 清除 Redis 中的重建状态
            var keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("启动时清除 {} 个残留重建状态", keys.size());
            }
            localStatusCache.clear();
            // 清空重建队列中的待处理消息
            rabbitTemplate.execute(channel -> {
                channel.queuePurge(RabbitMQConfig.KNOWLEDGE_REBUILD_QUEUE);
                return null;
            });
            log.info("重建队列已清空");
        } catch (Exception e) {
            log.warn("启动清理重建状态异常: {}", e.getMessage());
        }
    }

    public String submitRebuildTask(boolean clearBeforeRebuild) {
        clearPreviousStatus();

        KnowledgeRebuildTask task = clearBeforeRebuild
                ? KnowledgeRebuildTask.createClearAndRebuild()
                : KnowledgeRebuildTask.createAppend();

        KnowledgeRebuildStatus status = KnowledgeRebuildStatus.pending(task);
        saveStatus(status);

        log.info("📤 [知识库重建] 提交异步任务: taskId={}, type={}",
                task.getTaskId(), task.getTaskType());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.KNOWLEDGE_REBUILD_EXCHANGE,
                RabbitMQConfig.KNOWLEDGE_REBUILD_ROUTING_KEY,
                task
        );

        return task.getTaskId();
    }

    public KnowledgeRebuildStatus getStatus(String taskId) {
        KnowledgeRebuildStatus status = localStatusCache.get(taskId);
        if (status == null) {
            Object cached = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + taskId);
            if (cached instanceof KnowledgeRebuildStatus) {
                status = (KnowledgeRebuildStatus) cached;
                localStatusCache.put(taskId, status);
            }
        }
        return status;
    }

    public boolean isRebuildRunning() {
        // 先查本地缓存
        boolean localRunning = localStatusCache.values().stream()
                .anyMatch(s -> KnowledgeRebuildStatus.STATUS_RUNNING.equals(s.getStatus()));
        if (localRunning) return true;
        // 再查 Redis（多实例 / 缓存清空场景下状态仍在 Redis）
        try {
            var keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    Object val = redisTemplate.opsForValue().get(key);
                    if (val instanceof KnowledgeRebuildStatus s
                            && KnowledgeRebuildStatus.STATUS_RUNNING.equals(s.getStatus())) {
                        localStatusCache.put(s.getTaskId(), s); // 回填本地缓存
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis 查询重建状态失败: {}", e.getMessage());
        }
        return false;
    }

    public String getCurrentRunningTaskId() {
        // 先查本地缓存
        String localTaskId = localStatusCache.values().stream()
                .filter(s -> KnowledgeRebuildStatus.STATUS_RUNNING.equals(s.getStatus()))
                .findFirst()
                .map(KnowledgeRebuildStatus::getTaskId)
                .orElse(null);
        if (localTaskId != null) return localTaskId;
        // 再查 Redis
        try {
            var keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    Object val = redisTemplate.opsForValue().get(key);
                    if (val instanceof KnowledgeRebuildStatus s
                            && KnowledgeRebuildStatus.STATUS_RUNNING.equals(s.getStatus())) {
                        localStatusCache.put(s.getTaskId(), s);
                        return s.getTaskId();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis 查询重建状态失败: {}", e.getMessage());
        }
        return null;
    }

    private void saveStatus(KnowledgeRebuildStatus status) {
        localStatusCache.put(status.getTaskId(), status);
        redisTemplate.opsForValue().set(
                STATUS_KEY_PREFIX + status.getTaskId(),
                status,
                24,
                TimeUnit.HOURS
        );
    }

    @RabbitListener(queues = RabbitMQConfig.KNOWLEDGE_STATUS_QUEUE)
    public void handleStatusUpdate(KnowledgeRebuildStatus status) {
        log.info("📥 [知识库重建] 收到状态更新: taskId={}, status={}, progress={}%",
                status.getTaskId(), status.getStatus(), status.getProgress());

        saveStatus(status);
    }

    public void clearCompletedTasks() {
        localStatusCache.entrySet().removeIf(entry ->
                KnowledgeRebuildStatus.STATUS_COMPLETED.equals(entry.getValue().getStatus()) ||
                KnowledgeRebuildStatus.STATUS_FAILED.equals(entry.getValue().getStatus())
        );
    }

    /** 清除上一次重建的所有残留状态 (Redis + 本地缓存) */
    private void clearPreviousStatus() {
        localStatusCache.clear();
        try {
            var keys = redisTemplate.keys(STATUS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
            log.info("🧹 已清除上次重建的 {} 个残留状态", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.warn("清除残留状态异常: {}", e.getMessage());
        }
    }
}
