package com.itheima.agent.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SessionMetricsTracker {

    private static final Path PERSIST_FILE = Paths.get("data/metrics/sessions.json");
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Map<String, SessionStats> stats = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromDisk() {
        try {
            if (Files.exists(PERSIST_FILE)) {
                List<PersistEntry> entries = JSON.readValue(Files.readAllBytes(PERSIST_FILE),
                        new TypeReference<List<PersistEntry>>() {});
                for (PersistEntry e : entries) {
                    SessionStats s = new SessionStats();
                    s.taskCount = e.taskCount; s.taskSuccess = e.taskSuccess; s.taskFailure = e.taskFailure;
                    s.toolCallCount = e.toolCallCount; s.toolSuccess = e.toolSuccess; s.toolFailure = e.toolFailure;
                    s.totalTTFT = e.totalTTFT; s.totalE2E = e.totalE2E; s.totalTokens = e.totalTokens;
                    s.ttftCount = e.ttftCount;
                    s.ragRetrieveCount = e.ragRetrieveCount;
                    s.totalEmbeddingMs = e.totalEmbeddingMs; s.totalVectorMs = e.totalVectorMs;
                    s.totalBM25Ms = e.totalBM25Ms; s.totalRerankMs = e.totalRerankMs;
                    s.totalCacheMs = e.totalCacheMs; s.totalRetrieveMs = e.totalRetrieveMs;
                    s.displayName = e.displayName;
                    s.lastActive = e.lastActive;
                    stats.put(e.sessionId, s);
                }
                log.info("[Metrics] 从磁盘加载 {} 个会话指标", entries.size());
            }
        } catch (Exception e) {
            log.warn("[Metrics] 加载失败: {}", e.getMessage());
        }
        // 每 30 秒异步落盘
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "metrics-persist"); t.setDaemon(true); return t; })
                .scheduleWithFixedDelay(this::saveToDisk, 30, 30, TimeUnit.SECONDS);
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(PERSIST_FILE.getParent());
            List<PersistEntry> entries = new ArrayList<>();
            for (Map.Entry<String, SessionStats> e : stats.entrySet()) {
                SessionStats s = e.getValue();
                PersistEntry pe = new PersistEntry();
                synchronized (s) {
                    pe.sessionId = e.getKey();
                    pe.displayName = s.displayName;
                    pe.taskCount = s.taskCount; pe.taskSuccess = s.taskSuccess; pe.taskFailure = s.taskFailure;
                    pe.toolCallCount = s.toolCallCount; pe.toolSuccess = s.toolSuccess; pe.toolFailure = s.toolFailure;
                    pe.totalTTFT = s.totalTTFT; pe.totalE2E = s.totalE2E; pe.totalTokens = s.totalTokens;
                    pe.ttftCount = s.ttftCount;
                    pe.ragRetrieveCount = s.ragRetrieveCount;
                    pe.totalEmbeddingMs = s.totalEmbeddingMs; pe.totalVectorMs = s.totalVectorMs;
                    pe.totalBM25Ms = s.totalBM25Ms; pe.totalRerankMs = s.totalRerankMs;
                    pe.totalCacheMs = s.totalCacheMs; pe.totalRetrieveMs = s.totalRetrieveMs;
                    pe.lastActive = s.lastActive;
                }
                entries.add(pe);
            }
            Files.write(PERSIST_FILE, JSON.writeValueAsBytes(entries));
        } catch (Exception e) {
            log.warn("[Metrics] 落盘失败: {}", e.getMessage());
        }
    }

    // ---- record ----

    public void setSessionName(String sessionId, String firstMessage) {
        if (firstMessage == null || firstMessage.isEmpty()) return;
        SessionStats s = stats.computeIfAbsent(sessionId, k -> new SessionStats());
        if (s.displayName == null) s.displayName = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : firstMessage;
    }

    public void recordTask(String sessionId, long ttftMs, long e2eMs, long tokenCount, boolean success) {
        SessionStats s = stats.computeIfAbsent(sessionId, k -> new SessionStats());
        synchronized (s) {
            s.taskCount++;
            if (success) { s.taskSuccess++; } else { s.taskFailure++; }
            s.totalTTFT += ttftMs;
            s.totalE2E += e2eMs;
            s.totalTokens += tokenCount;
            if (ttftMs > 0) s.ttftCount++;
            s.lastActive = Instant.now();
        }
    }

    public void recordToolCall(String sessionId, boolean success) {
        SessionStats s = stats.computeIfAbsent(sessionId, k -> new SessionStats());
        synchronized (s) {
            s.toolCallCount++;
            if (success) { s.toolSuccess++; } else { s.toolFailure++; }
            s.lastActive = Instant.now();
        }
    }

    public void recordRagPipeline(String sessionId, long embeddingMs, long vectorMs, long bm25Ms,
                                   long rerankMs, long cacheMs, long totalMs) {
        SessionStats s = stats.computeIfAbsent(sessionId, k -> new SessionStats());
        synchronized (s) {
            s.ragRetrieveCount++;
            s.totalEmbeddingMs += embeddingMs; s.totalVectorMs += vectorMs;
            s.totalBM25Ms += bm25Ms; s.totalRerankMs += rerankMs;
            s.totalCacheMs += cacheMs; s.totalRetrieveMs += totalMs;
            s.lastActive = Instant.now();
        }
    }

    // ---- query ----

    public List<SessionSummary> listAll() {
        List<SessionSummary> result = new ArrayList<>();
        for (Map.Entry<String, SessionStats> e : stats.entrySet())
            result.add(buildSummary(e.getKey(), e.getValue()));
        result.sort((a, b) -> Long.compare(b.lastActiveMs, a.lastActiveMs));
        return result;
    }

    public Map<String, Object> globalSummary() {
        long taskCount = 0, taskSuccess = 0, taskFailure = 0, toolCalls = 0, toolFailures = 0;
        double totalTTFT = 0, totalE2E = 0, totalTokens = 0, ttftCount = 0;
        int ragN = 0;
        long totalEmb = 0, totalVec = 0, totalBm25 = 0, totalRerank = 0, totalCache = 0, totalRetr = 0;
        for (SessionStats s : stats.values()) {
            synchronized (s) {
                taskCount += s.taskCount; taskSuccess += s.taskSuccess; taskFailure += s.taskFailure;
                toolCalls += s.toolCallCount; toolFailures += s.toolFailure;
                totalTTFT += s.totalTTFT; totalE2E += s.totalE2E; totalTokens += s.totalTokens;
                ttftCount += s.ttftCount;
                ragN += s.ragRetrieveCount;
                totalEmb += s.totalEmbeddingMs; totalVec += s.totalVectorMs; totalBm25 += s.totalBM25Ms;
                totalRerank += s.totalRerankMs; totalCache += s.totalCacheMs; totalRetr += s.totalRetrieveMs;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessions", stats.size()); m.put("taskCount", taskCount);
        m.put("taskSuccess", taskSuccess); m.put("taskFailure", taskFailure);
        m.put("successRate", taskCount > 0 ? r(taskSuccess * 100.0 / taskCount) : 100.0);
        m.put("avgTTFT", ttftCount > 0 ? r((double) totalTTFT / ttftCount) : 0);
        m.put("avgE2E", taskCount > 0 ? r((double) totalE2E / taskCount) : 0);
        m.put("toolCalls", toolCalls); m.put("toolFailures", toolFailures);
        m.put("toolSuccessRate", toolCalls > 0 ? r((toolCalls - toolFailures) * 100.0 / toolCalls) : 100.0);
        m.put("avgTokens", taskCount > 0 ? r((double) totalTokens / taskCount) : 0);
        m.put("totalTokens", totalTokens);
        m.put("ragRetrieves", ragN); m.put("avgEmbeddingMs", ragN > 0 ? r((double) totalEmb / ragN) : 0);
        m.put("avgVectorMs", ragN > 0 ? r((double) totalVec / ragN) : 0);
        m.put("avgBM25Ms", ragN > 0 ? r((double) totalBm25 / ragN) : 0);
        m.put("avgRerankMs", ragN > 0 ? r((double) totalRerank / ragN) : 0);
        m.put("avgCacheMs", ragN > 0 ? r((double) totalCache / ragN) : 0);
        m.put("avgRetrieveMs", ragN > 0 ? r((double) totalRetr / ragN) : 0);
        return m;
    }

    private SessionSummary buildSummary(String id, SessionStats s) {
        synchronized (s) {
            int n = s.ragRetrieveCount;
            return new SessionSummary(id, s.displayName != null ? s.displayName : id,
                s.taskCount, s.taskSuccess, s.taskFailure,
                s.toolCallCount, s.toolCallCount - s.toolFailure, s.toolFailure,
                s.ttftCount > 0 ? r((double) s.totalTTFT / s.ttftCount) : 0,
                s.taskCount > 0 ? r((double) s.totalE2E / s.taskCount) : 0,
                s.taskCount > 0 ? r((double) s.totalTokens / s.taskCount) : 0,
                s.totalTokens,
                s.taskCount > 0 ? r(s.taskSuccess * 100.0 / s.taskCount) : 100.0,
                s.lastActive.toEpochMilli(), n,
                n > 0 ? r((double) s.totalEmbeddingMs / n) : 0,
                n > 0 ? r((double) s.totalVectorMs / n) : 0,
                n > 0 ? r((double) s.totalBM25Ms / n) : 0,
                n > 0 ? r((double) s.totalRerankMs / n) : 0,
                n > 0 ? r((double) s.totalCacheMs / n) : 0,
                n > 0 ? r((double) s.totalRetrieveMs / n) : 0);
        }
    }

    private static double r(double v) { return Math.round(v * 10.0) / 10.0; }

    // ---- data classes ----

    private static class SessionStats {
        int taskCount, taskSuccess, taskFailure;
        int toolCallCount, toolSuccess, toolFailure;
        long totalTTFT, totalE2E, totalTokens;
        int ttftCount, ragRetrieveCount;
        long totalEmbeddingMs, totalVectorMs, totalBM25Ms, totalRerankMs, totalCacheMs, totalRetrieveMs;
        String displayName;
        Instant lastActive = Instant.now();
    }

    @Data
    @lombok.AllArgsConstructor
    public static class SessionSummary {
        final String sessionId, displayName;
        final int taskCount, taskSuccess, taskFailure, toolCalls, toolSuccess, toolFailure;
        final double avgTTFT, avgE2E, avgTokens;
        final long totalTokens;
        final double successRate;
        final long lastActiveMs;
        final int ragRetrieves;
        final double avgEmbeddingMs, avgVectorMs, avgBM25Ms, avgRerankMs, avgCacheMs, avgRetrieveMs;
    }

    @Data
    static class PersistEntry {
        String sessionId, displayName;
        int taskCount, taskSuccess, taskFailure, toolCallCount, toolSuccess, toolFailure;
        long totalTTFT, totalE2E, totalTokens;
        int ttftCount, ragRetrieveCount;
        long totalEmbeddingMs, totalVectorMs, totalBM25Ms, totalRerankMs, totalCacheMs, totalRetrieveMs;
        Instant lastActive;
    }
}
