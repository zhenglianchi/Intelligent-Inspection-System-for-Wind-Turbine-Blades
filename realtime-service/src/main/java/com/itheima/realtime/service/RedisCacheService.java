package com.itheima.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统一Redis缓存服务
 * 替换原wtb-health-monitor中的Caffeine本地缓存
 * 支持两种缓存过期策略：
 * 1. common缓存：24小时过期，用于存储不常变化的数据（最大风机编号、特征曲线、文件路径）
 * 2. state缓存：20秒过期，用于存储实时状态数据（风机运行状态）
 *
 * Key前缀设计：
 * - wtb:common:{key} 公共缓存
 * - wtb:state:{key}  状态缓存
 */
@Slf4j
@Service
public class RedisCacheService {

    private static final String COMMON_PREFIX = "wtb:common:";
    private static final String STATE_PREFIX = "wtb:state:";
    private static final int COMMON_EXPIRE_HOURS = 24;
    private static final int STATE_EXPIRE_SECONDS = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisCacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取公共缓存中的值（24小时过期）
     *
     * @param key 缓存键
     * @param clazz 返回值类型
     * @param <T>  返回值类型泛型
     * @return 缓存值，不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getCommon(String key, Class<T> clazz) {
        String redisKey = COMMON_PREFIX + key;
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return null;
        }
        // 如果已经是目标类型直接返回
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        // 如果是JSON字符串反序列化
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, clazz);
            } catch (JsonProcessingException e) {
                log.error("❌ [RedisCache] 反序列化失败, key={}", key, e);
                return null;
            }
        }
        // LinkedHashMap 等 Map 类型没有 @class 信息，用 objectMapper 转换
        if (value instanceof Map) {
            try {
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, clazz);
            } catch (JsonProcessingException e) {
                log.error("❌ [RedisCache] Map转换失败, key={}", key, e);
                return null;
            }
        }
        return (T) value;
    }

    /**
     * 写入公共缓存（24小时过期）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void putCommon(String key, Object value) {
        String redisKey = COMMON_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, COMMON_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("✅ [RedisCache] 写入公共缓存, key={}, 过期={}小时", key, COMMON_EXPIRE_HOURS);
    }

    /**
     * 获取状态缓存中的值（20秒过期）
     *
     * @param key   缓存键
     * @param clazz 返回值类型
     * @param <T>   返回值类型泛型
     * @return 缓存值，不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> clazz) {
        String redisKey = STATE_PREFIX + key;
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, clazz);
            } catch (JsonProcessingException e) {
                log.error("❌ [RedisCache] 反序列化失败, key={}", key, e);
                return null;
            }
        }
        return (T) value;
    }

    // Lua 脚本：原子执行 SET key value EX ttl
    private static final String LUA_PUT_STATE =
            "redis.call('SET', KEYS[1], ARGV[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "return 1";

    /**
     * 写入状态缓存（20秒过期）
     */
    public void putState(String key, Object value) {
        String redisKey = STATE_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, STATE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        log.debug("✅ [RedisCache] 写入状态缓存, key={}, 过期={}秒", key, STATE_EXPIRE_SECONDS);
    }

    /**
     * 使用 Lua 脚本原子写入状态缓存（20秒过期）
     * 替代 Redisson 分布式锁，Lua 脚本在 Redis 服务端原子执行，
     * 确保 SET + EXPIRE 为一个不可分割的操作
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void putStateAtomic(String key, Object value) {
        String redisKey = STATE_PREFIX + key;
        String serialized = value instanceof String ? (String) value : serialize(value);
        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        LUA_PUT_STATE, Long.class
                ),
                Collections.singletonList(redisKey),
                serialized, String.valueOf(STATE_EXPIRE_SECONDS)
        );
        log.debug("✅ [RedisCache-Lua] 原子写入状态缓存, key={}, 过期={}秒", key, STATE_EXPIRE_SECONDS);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("❌ [RedisCache] 序列化失败", e);
            return value.toString();
        }
    }

    /**
     * 从公共缓存中删除指定key
     *
     * @param key 缓存键
     */
    public void evictCommon(String key) {
        String redisKey = COMMON_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.debug("🗑️ [RedisCache] 删除公共缓存, key={}", key);
    }

    /**
     * 从状态缓存中删除指定key
     *
     * @param key 缓存键
     */
    public void evictState(String key) {
        String redisKey = STATE_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.debug("🗑️ [RedisCache] 删除状态缓存, key={}", key);
    }

    /**
     * 清空所有wtb相关缓存
     */
    public void evictAll() {
        // 扫描并删除所有wtb前缀的key
        var commonKeys = redisTemplate.keys(COMMON_PREFIX + "*");
        if (commonKeys != null && !commonKeys.isEmpty()) {
            redisTemplate.delete(commonKeys);
        }
        var stateKeys = redisTemplate.keys(STATE_PREFIX + "*");
        if (stateKeys != null && !stateKeys.isEmpty()) {
            redisTemplate.delete(stateKeys);
        }
        log.info("🗑️ [RedisCache] 清空所有wtb缓存");
    }
}
