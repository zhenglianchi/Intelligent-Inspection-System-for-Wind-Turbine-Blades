package com.itheima.agent.controller;

import com.itheima.agent.aiservice.WindFarmAssistant;
import com.itheima.agent.config.DegradationConfig;
import com.itheima.agent.dto.ChatResponse;
import com.itheima.agent.service.ChatMessageProducer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private WindFarmAssistant windFarmAssistant;

    @Autowired
    private DegradationService degradationService;

    @Autowired
    private ChatMessageProducer chatMessageProducer;

    @Autowired
    private HistoryRetrievalService historyRetrievalService;

    @Autowired
    private com.itheima.agent.repository.RedisChatMemoryProvider chatMemoryProvider;

    private final Counter ragQueryCounter;
    private final Timer ragQueryTimer;

    public ChatController(MeterRegistry meterRegistry) {
        this.ragQueryCounter = Counter.builder("rag.query.total")
                .description("Total RAG query count")
                .register(meterRegistry);

        this.ragQueryTimer = Timer.builder("rag.query.duration")
                .description("RAG query duration")
                .register(meterRegistry);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(
            @RequestParam(required = false, defaultValue = "default-session") String memoryId,
            @RequestBody Map<String, String> payload
    ) {
        String message = payload.get("message");

        if (message == null || message.trim().isEmpty()) {
            return Map.of("answer", "问题不能为空");
        }

        log.info("👤 [Session: {}] 收到用户提问：{}", memoryId, message);

        if (degradationService.isEmergency()) {
            log.warn("⚠️ 系统处于紧急降级模式");
            return Map.of(
                    "answer", degradationService.getFallbackMessage(),
                    "degraded", true,
                    "level", "EMERGENCY"
            );
        }

        ragQueryCounter.increment();

        try {
            CompletableFuture<ChatResponse> future = chatMessageProducer.sendChatRequest(memoryId, message);
            ChatResponse response = future.get(65, TimeUnit.SECONDS);

            Map<String, Object> result = new HashMap<>();
            if ("ERROR".equals(response.getStatus()) || "DEGRADED".equals(response.getStatus())) {
                result.put("answer", response.getAnswer() != null ? response.getAnswer() : response.getErrorMessage());
                result.put("messageId", response.getMessageId());
                result.put("memoryId", response.getMemoryId());
                result.put("status", response.getStatus());
                result.put("degraded", response.isDegraded());
            } else {
                result.put("answer", response.getAnswer());
                result.put("messageId", response.getMessageId());
                result.put("memoryId", response.getMemoryId());
                result.put("durationMs", response.getDurationMs() != null ? response.getDurationMs() : 0);
                result.put("status", response.getStatus());
            }
            return result;

        } catch (TimeoutException e) {
            log.error("⏰ [消息队列] 请求超时: memoryId={}", memoryId);
            return Map.of(
                    "answer", "请求处理超时，请稍后重试",
                    "status", "TIMEOUT"
            );
        } catch (Exception e) {
            log.error("❌ [消息队列] 处理异常: memoryId={}", memoryId, e);
            return Map.of(
                    "answer", "系统繁忙，请稍后再试: " + e.getMessage(),
                    "status", "ERROR"
            );
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam(required = false, defaultValue = "default-session") String memoryId,
            @RequestBody Map<String, String> payload
    ) {
        String message = payload.get("message");
        SseEmitter emitter = new SseEmitter(300000L);  // 5分钟超时

        if (message == null || message.trim().isEmpty()) {
            sendEvent(emitter, "error", "问题不能为空");
            emitter.complete();
            return emitter;
        }

        log.info("👤 [Session: {}] 收到流式提问：{}", memoryId, message);

        if (degradationService.isEmergency()) {
            sendEvent(emitter, "content", degradationService.getFallbackMessage());
            sendDone(emitter);
            return emitter;
        }

        ragQueryCounter.increment();
        MemoryIdContext.set(memoryId);

        Executors.newSingleThreadExecutor().execute(() -> {
            windFarmAssistant.chatStream(memoryId, message)
                    .doOnNext(chunk -> sendEvent(emitter, "content", chunk))
                    .doOnComplete(() -> {
                        log.info("✅ [Session: {}] 流式响应完成", memoryId);
                        sendDone(emitter);
                        degradationService.recordSuccess("llm");
                    })
                    .doOnError(e -> {
                        log.error("❌ [Session: {}] 流式响应出错", memoryId, e);
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

    @GetMapping("/chat")
    public Map<String, Object> chatGet(
            @RequestParam(required = false, defaultValue = "default-session") String memoryId,
            @RequestParam String message
    ) {
        return chat(memoryId, Map.of("message", message));
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

    @GetMapping("/queue/stats")
    public Map<String, Object> getQueueStats() {
        return Map.of(
                "pendingRequests", chatMessageProducer.getPendingRequestCount()
        );
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
}
