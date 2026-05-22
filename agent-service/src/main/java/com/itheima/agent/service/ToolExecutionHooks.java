package com.itheima.agent.service;

import com.itheima.agent.metrics.RagMetrics;
import com.itheima.agent.metrics.SessionMetricsTracker;
import com.itheima.agent.pojo.MemoryIdContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class ToolExecutionHooks {

    @Autowired private DegradationService degradationService;
    @Autowired private RagMetrics ragMetrics;
    @Autowired private SessionMetricsTracker sessionMetrics;

    @Value("${rag.react.max-steps:5}") private int maxSteps;
    @Value("${rag.react.max-retries-per-call:2}") private int maxRetriesPerCall;

    private static final Path FAILURE_LOG_DIR = Paths.get("data/logs/tool_errors");

    private final ThreadLocal<Integer> stepCounter = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Map<String, Integer>> consecutiveFailures = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Set<String>> blockedTools = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<String> lastToolName = new ThreadLocal<>();

    // ---- 入口 ----

    public boolean shouldBypass() { return !degradationService.isToolAvailable(); }
    public String bypassMessage(String toolName) {
        log.warn("[降级] 工具已禁用: {}", toolName);
        recordMetrics(false);
        return "当前系统处于降级模式，" + toolName + " 工具暂不可用。";
    }

    public String checkBlocked(String toolName) {
        lastToolName.set(toolName);
        if (stepCounter.get() >= maxSteps) {
            recordMetrics(false);
            return String.format("[步数限制] 已调用 %d/%d 次工具，请基于已有信息直接回答。", stepCounter.get(), maxSteps);
        }
        if (blockedTools.get().contains(toolName)) {
            recordMetrics(false);
            return String.format("[工具已阻止] '%s' 因连续失败 %d 次被临时禁用。", toolName, maxRetriesPerCall);
        }
        return null;
    }

    public boolean isStepLimitReached() { return stepCounter.get() >= maxSteps || blockedTools.get().contains(lastToolName.get()); }

    public String stepLimitMessage() {
        String tn = lastToolName.get();
        if (blockedTools.get().contains(tn))
            return String.format("[工具已阻止] '%s' 连续失败 %d 次，请换用其他工具。", tn, maxRetriesPerCall);
        return String.format("[步数限制] 已调用 %d/%d 次工具。", stepCounter.get(), maxSteps);
    }

    // ---- 成功/失败记录 ----

    public void recordSuccess() {
        stepCounter.set(stepCounter.get() + 1);
        String tn = lastToolName.get();
        if (tn != null) consecutiveFailures.get().remove(tn);
        recordMetrics(true);
    }

    public void recordFailureOnly() {
        stepCounter.set(stepCounter.get() + 1);
        recordMetrics(false);
        String tn = lastToolName.get();
        if (tn != null) {
            int fails = consecutiveFailures.get().merge(tn, 1, Integer::sum);
            if (fails >= maxRetriesPerCall) { blockedTools.get().add(tn); log.warn("[防循环] '{}' 连续失败 {} 次, 已黑名单", tn, fails); }
        }
    }

    public String recordFailure(String toolName, Exception e) {
        stepCounter.set(stepCounter.get() + 1);
        recordMetrics(false);
        writeFailureLog(toolName, e.getMessage());
        log.error("[工具执行失败] {}: {}", toolName, e.getMessage(), e);
        int fails = consecutiveFailures.get().merge(toolName, 1, Integer::sum);
        if (fails >= maxRetriesPerCall) { blockedTools.get().add(toolName); log.warn("[防循环] '{}' 连续失败 {} 次, 已黑名单", toolName, fails); }
        return toolName + " 执行出错：" + e.getMessage();
    }

    public void resetSteps() { stepCounter.remove(); consecutiveFailures.remove(); blockedTools.remove(); lastToolName.remove(); }

    // ---- 失败日志落盘 ----

    private void writeFailureLog(String toolName, String error) {
        try {
            Files.createDirectories(FAILURE_LOG_DIR);
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path logFile = FAILURE_LOG_DIR.resolve(date + ".log");
            String line = String.format("[%s] session=%s tool=%s error=%s\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                MemoryIdContext.get(), toolName, error);
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private void recordMetrics(boolean success) {
        ragMetrics.recordToolCall(success);
        String sid = MemoryIdContext.get();
        if (sid != null) sessionMetrics.recordToolCall(sid, success);
    }
}
