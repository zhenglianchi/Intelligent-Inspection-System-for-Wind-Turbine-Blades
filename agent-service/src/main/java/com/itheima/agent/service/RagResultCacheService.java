package com.itheima.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * RAG 检索结果向量缓存
 *
 * 原理：对用户 query 做 embedding，在语义缓存索引中查找相似的历史 query。
 * 相似度 >= minSimilarity 时直接返回缓存结果，避免重复的 RAG 检索流程。
 *
 * 工具调用（如 queryRealtimeData）不缓存——实时数据必须每次查询。
 *
 * Redis 存储结构:
 *   rag:cache:semantic:{md5}  → Hash  {query, result_json, embedding_bytes}
 *   rag_cache_semantic_idx     → RediSearch 向量索引 (预先创建或 auto-create)
 */
@Slf4j
@Service
public class RagResultCacheService {

    private static final String CACHE_PREFIX = "rag:cache:semantic:";
    private static final String INDEX_NAME = "rag_cache_semantic_idx";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.cache.semantic.enabled:true}")
    private boolean enabled;

    @Value("${rag.cache.semantic.min-similarity:0.85}")
    private double minSimilarity;

    @Value("${rag.cache.semantic.ttl-minutes:30}")
    private int ttlMinutes;

    public RagResultCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 查找语义缓存
     * @param queryEmbedding 查询的 embedding 向量 (float[] 序列化为字节)
     * @return 缓存的检索结果 JSON，未命中返回 null
     */
    public String findSimilarQuery(float[] queryEmbedding, String queryMd5) {
        if (!enabled) return null;

        try {
            // 用 RediSearch 向量搜索查找相似的历史查询
            byte[] vecBytes = floatsToBytes(queryEmbedding);
            String cacheKey = CACHE_PREFIX + queryMd5;

            // 先检查精确匹配缓存
            String exact = redisTemplate.opsForValue().get(cacheKey);
            if (exact != null && !exact.isEmpty()) {
                log.debug("RAG cache exact hit: {}", queryMd5.substring(0, 8));
                return exact;
            }

            // 向量搜索 (需要预先创建索引)
            try {
                // FT.SEARCH rag_cache_semantic_idx "*=>[KNN 1 @embedding $vec RETURN 3 key query result]"
                // 简化为：直接在 cache keys 中遍历做相似度匹配
                var keys = redisTemplate.keys(CACHE_PREFIX + "*");
                if (keys != null) {
                    for (String key : keys) {
                        String storedVec = (String) redisTemplate.opsForHash().get(key, "embedding");
                        if (storedVec == null) continue;
                        float[] stored = bytesToFloats(storedVec.getBytes(StandardCharsets.ISO_8859_1));
                        double sim = cosineSimilarity(queryEmbedding, stored);
                        if (sim >= minSimilarity) {
                            String result = (String) redisTemplate.opsForHash().get(key, "result");
                            log.info("RAG cache semantic hit, similarity={:.3f}", sim);
                            return result;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("RAG cache vector search failed (index may not exist): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("RAG cache lookup failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 存入缓存
     */
    public void cacheResult(String queryMd5, String originalQuery, String resultJson, float[] embedding) {
        if (!enabled || originalQuery == null || resultJson == null) return;

        String key = CACHE_PREFIX + queryMd5;
        try {
            redisTemplate.opsForHash().put(key, "query", originalQuery);
            redisTemplate.opsForHash().put(key, "result", resultJson);
            byte[] vecBytes = floatsToBytes(embedding);
            redisTemplate.opsForHash().put(key, "embedding", new String(vecBytes, StandardCharsets.ISO_8859_1));
            redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            log.warn("RAG cache write failed: {}", e.getMessage());
        }
    }

    public void evictAll() {
        var keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        log.info("RAG cache cleared");
    }

    public String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    // ---- 向量运算 ----

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private byte[] floatsToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4]     = (byte) (bits >> 24);
            bytes[i * 4 + 1] = (byte) (bits >> 16);
            bytes[i * 4 + 2] = (byte) (bits >> 8);
            bytes[i * 4 + 3] = (byte) bits;
        }
        return bytes;
    }

    private float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = ((bytes[i * 4] & 0xFF) << 24)
                     | ((bytes[i * 4 + 1] & 0xFF) << 16)
                     | ((bytes[i * 4 + 2] & 0xFF) << 8)
                     | (bytes[i * 4 + 3] & 0xFF);
            floats[i] = Float.intBitsToFloat(bits);
        }
        return floats;
    }
}
