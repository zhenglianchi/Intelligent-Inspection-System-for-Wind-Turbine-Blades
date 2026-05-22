package com.itheima.agent.controller;

import com.itheima.agent.aiservice.WindFarmAssistant;
import com.itheima.agent.config.DegradationConfig;
import com.itheima.agent.service.ContextManager;
import com.itheima.agent.service.DegradationService;
import com.itheima.agent.service.HistoryRetrievalService;
import com.itheima.agent.pojo.MemoryIdContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private WindFarmAssistant windFarmAssistant;

    @Autowired
    private com.itheima.agent.service.AgentRouter agentRouter;

    @Autowired
    private DegradationService degradationService;

    @Autowired
    private HistoryRetrievalService historyRetrievalService;

    @Autowired
    private com.itheima.agent.repository.RedisChatMemoryProvider chatMemoryProvider;

    @Autowired
    private com.itheima.agent.metrics.RagMetrics ragMetrics;

    @Autowired
    private com.itheima.agent.metrics.SessionMetricsTracker sessionMetrics;

    @Autowired
    private ContextManager contextManager;

    private final Counter ragQueryCounter;

    public ChatController(MeterRegistry meterRegistry) {
        this.ragQueryCounter = Counter.builder("rag.query.total")
                .description("Total RAG query count")
                .register(meterRegistry);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam(required = false, defaultValue = "default-session") String memoryId,
            @RequestBody Map<String, String> payload
    ) {
        String message = payload.get("message");
        SseEmitter emitter = new SseEmitter(300000L);

        if (message == null || message.trim().isEmpty()) {
            sessionMetrics.recordTask(memoryId, 0, 0, 0, false);
            ragMetrics.recordTaskFailure();
            sendEvent(emitter, "error", "问题不能为空");
            emitter.complete();
            return emitter;
        }

        log.info("👤 [Session: {}] 收到流式提问：{}", memoryId, message);

        if (degradationService.isEmergency()) {
            sessionMetrics.recordTask(memoryId, 0, 0, 0, false);
            ragMetrics.recordTaskFailure();
            sendEvent(emitter, "content", degradationService.getFallbackMessage());
            sendDone(emitter);
            return emitter;
        }

        ragQueryCounter.increment();
        MemoryIdContext.set(memoryId);
        sessionMetrics.setSessionName(memoryId, message);

        final Timer.Sample e2eSample = ragMetrics.startTimer();
        final long requestStartNanos = System.nanoTime();
        final boolean[] firstToken = {true};
        final long[] firstTokenNanos = {0};

        Executors.newSingleThreadExecutor().execute(() -> {
            StringBuilder responseAccumulator = new StringBuilder();
            agentRouter.route(memoryId, message)
                    .doOnNext(chunk -> {
                        if (firstToken[0]) {
                            firstTokenNanos[0] = System.nanoTime();
                            ragMetrics.recordTTFT(e2eSample);
                            firstToken[0] = false;
                        }
                        responseAccumulator.append(chunk);
                        sendEvent(emitter, "content", chunk);
                    })
                    .doOnComplete(() -> {
                        log.info("✅ [Session: {}] 流式响应完成", memoryId);
                        int estimatedTokens = contextManager.estimateTokens(responseAccumulator.toString())
                                + contextManager.estimateTokens(message);
                        log.info("[Token] session={}, 本轮估算消耗 ~{} tokens", memoryId, estimatedTokens);

                        long ttftMs = firstTokenNanos[0] > 0
                                ? (firstTokenNanos[0] - requestStartNanos) / 1_000_000
                                : 0;
                        long e2eMs = (System.nanoTime() - requestStartNanos) / 1_000_000;
                        sessionMetrics.recordTask(memoryId, ttftMs, e2eMs, estimatedTokens, true);

                        ragMetrics.recordTaskSuccess(e2eSample, estimatedTokens);
                        sendDone(emitter);
                        degradationService.recordSuccess("llm");
                    })
                    .doOnError(e -> {
                        log.error("❌ [Session: {}] 流式响应出错", memoryId, e);
                        long e2eMs = (System.nanoTime() - requestStartNanos) / 1_000_000;
                        sessionMetrics.recordTask(memoryId, 0, e2eMs, 0, false);
                        ragMetrics.recordTaskFailure();
                        sendEvent(emitter, "error", e.getMessage());
                        emitter.complete();
                        degradationService.recordError("llm");
                    })
                    .blockLast();
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.error("❌ [SSE] 连接错误: {}", e.getMessage()));
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String type, String text) {
        try {
            String escaped = escapeJson(text);
            emitter.send(SseEmitter.event().data("{\"" + type + "\": \"" + escaped + "\"}"));
        } catch (Exception e) {
            log.warn("SSE sendEvent failed: {}", e.getMessage());
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("{\"done\": true}"));
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE sendDone failed: {}", e.getMessage());
        }
    }

    @GetMapping("/degradation/status")
    public Map<String, Object> getDegradationStatus() {
        return degradationService.getStatus();
    }

    @PostMapping("/degradation/level")
    public Map<String, Object> setDegradationLevel(@RequestParam String level) {
        try {
            DegradationConfig.DegradationLevel newLevel = DegradationConfig.DegradationLevel.valueOf(level.toUpperCase());
            degradationService.setLevel(newLevel);
            return Map.of(
                    "success", true,
                    "message", "降级级别已设置为: " + newLevel,
                    "status", degradationService.getStatus()
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                    "success", false,
                    "message", "无效的降级级别，可选值: NORMAL, DISABLE_RAG, DISABLE_TOOL, EMERGENCY"
            );
        }
    }

    @PostMapping("/degradation/reset")
    public Map<String, Object> resetDegradation() {
        degradationService.reset();
        return Map.of(
                "success", true,
                "message", "降级状态已重置",
                "status", degradationService.getStatus()
        );
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions() {
        return chatMemoryProvider.listSessions();
    }

    @DeleteMapping("/sessions/{memoryId}")
    public Map<String, Object> deleteSession(@PathVariable String memoryId) {
        chatMemoryProvider.deleteSession(memoryId);
        return Map.of("success", true);
    }

    @GetMapping("/sessions/{memoryId}/history")
    public List<Map<String, Object>> getSessionHistory(@PathVariable String memoryId) {
        List<dev.langchain4j.data.message.ChatMessage> history = chatMemoryProvider.getFullHistory(memoryId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var msg : history) {
            Map<String, Object> m = new HashMap<>();
            if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                m.put("role", "user");
                m.put("content", ((dev.langchain4j.data.message.UserMessage) msg).singleText());
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                m.put("role", "assistant");
                m.put("content", ((dev.langchain4j.data.message.AiMessage) msg).text());
            } else {
                continue; // skip system messages
            }
            result.add(m);
        }
        return result;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * RAG 监控指标 — 全局聚合
     */
    @GetMapping("/metrics/global")
    public Map<String, Object> getGlobalMetrics() {
        Map<String, Object> m = sessionMetrics.globalSummary();
        // 用 Micrometer 实测值覆盖管道各级延迟 (SessionMetricsTracker 只记了总时间)
        MeterRegistry reg = ragMetrics.getRegistry();
        m.put("avgEmbeddingMs", timerAvg(reg, "rag.pipeline.embedding"));
        m.put("avgVectorMs", timerAvg(reg, "rag.pipeline.vector.search"));
        m.put("avgBM25Ms", timerAvg(reg, "rag.pipeline.bm25.search"));
        m.put("avgRerankMs", timerAvg(reg, "rag.pipeline.rerank"));
        m.put("avgCacheMs", timerAvg(reg, "rag.pipeline.cache.check"));
        m.put("avgRetrieveMs", timerAvg(reg, "rag.pipeline.total.retrieve"));
        m.put("ragRetrieves", timerCount(reg, "rag.pipeline.total.retrieve"));
        return m;
    }

    private double timerAvg(MeterRegistry reg, String name) {
        var t = reg.find(name).timer();
        return t != null ? Math.round(t.mean(TimeUnit.MILLISECONDS) * 10.0) / 10.0 : 0;
    }

    private long timerCount(MeterRegistry reg, String name) {
        var t = reg.find(name).timer();
        return t != null ? t.count() : 0;
    }

    /**
     * RAG 监控指标 — 按 session 维度，每个会话的平均延迟、成功率等
     */
    @GetMapping("/metrics/sessions")
    public List<com.itheima.agent.metrics.SessionMetricsTracker.SessionSummary> getSessionMetrics() {
        return sessionMetrics.listAll();
    }

    /**
     * RAG 监控指标 — 兼容旧接口，返回全局 + 前10个会话
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new HashMap<>();
        MeterRegistry reg = ragMetrics.getRegistry();

        // Timer 指标: mean / max / count
        record TimerInfo(double mean, double max, long count) {}
        m.put("ttft", timerInfo(reg, "rag.ttft"));
        m.put("e2e", timerInfo(reg, "rag.e2e"));
        m.put("taskDuration", timerInfo(reg, "rag.task.duration"));

        // TPS gauge
        double tps = reg.find("rag.tps").gauge() != null ? reg.find("rag.tps").gauge().value() : 0;
        m.put("tps", Math.round(tps * 100.0) / 100.0);

        // Counter 指标
        m.put("taskSuccess", counterValue(reg, "rag.task.completed"));
        m.put("taskFailure", counterValue(reg, "rag.task.failed"));
        m.put("toolSuccess", counterValue(reg, "rag.tool.calls"));
        m.put("toolFailure", counterValue(reg, "rag.tool.failures"));

        // Token 用量分布
        double[] tokenStats = summaryStats(reg, "rag.token.usage");
        m.put("tokenAvg", Math.round(tokenStats[0]));
        m.put("tokenMax", Math.round(tokenStats[1]));
        m.put("tokenCount", (long) tokenStats[2]);

        // RAG 管道各级延迟
        m.put("embeddingMs", timerInfo(reg, "rag.pipeline.embedding"));
        m.put("vectorSearchMs", timerInfo(reg, "rag.pipeline.vector.search"));
        m.put("bm25SearchMs", timerInfo(reg, "rag.pipeline.bm25.search"));
        m.put("rerankMs", timerInfo(reg, "rag.pipeline.rerank"));
        m.put("cacheCheckMs", timerInfo(reg, "rag.pipeline.cache.check"));
        m.put("totalRetrieveMs", timerInfo(reg, "rag.pipeline.total.retrieve"));

        // 派生指标
        double taskTotal = counterValue(reg, "rag.task.completed") + counterValue(reg, "rag.task.failed");
        double successRate = taskTotal > 0
                ? Math.round(counterValue(reg, "rag.task.completed") / taskTotal * 1000.0) / 10.0
                : 100.0;
        m.put("successRate", successRate);

        double toolTotal = counterValue(reg, "rag.tool.calls") + counterValue(reg, "rag.tool.failures");
        double toolRate = toolTotal > 0
                ? Math.round(counterValue(reg, "rag.tool.calls") / toolTotal * 1000.0) / 10.0
                : 100.0;
        m.put("toolSuccessRate", toolRate);

        return m;
    }

    private Map<String, Object> timerInfo(MeterRegistry reg, String name) {
        Map<String, Object> info = new HashMap<>();
        var t = reg.find(name).timer();
        if (t != null) {
            info.put("avg", Math.round(t.mean(TimeUnit.MILLISECONDS) * 10.0) / 10.0);
            info.put("max", Math.round(t.max(TimeUnit.MILLISECONDS) * 10.0) / 10.0);
            info.put("count", t.count());
        } else {
            info.put("avg", 0);
            info.put("max", 0);
            info.put("count", 0);
        }
        return info;
    }

    private long counterValue(MeterRegistry reg, String name) {
        var c = reg.find(name).counter();
        return c != null ? (long) c.count() : 0;
    }

    private double[] summaryStats(MeterRegistry reg, String name) {
        var s = reg.find(name).summary();
        if (s != null) {
            return new double[]{s.mean(), s.max(), s.count()};
        }
        return new double[]{0, 0, 0};
    }
}
