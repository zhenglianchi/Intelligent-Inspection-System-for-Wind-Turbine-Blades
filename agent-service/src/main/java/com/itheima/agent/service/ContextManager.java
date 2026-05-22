package com.itheima.agent.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 多层上下文压缩管理器
 *
 * Layer 1 — 工具结果过期归档 (30 min): 旧工具调用结果替换为占位符
 * Layer 2 — 消息截断 (70% token 阈值): 截断最旧消息，保留边界标记
 * Layer 3 — 摘要压缩 (90% token 阈值): LLM 摘要压缩全量历史，保留最后 20 条消息
 *
 * Token 计算: 使用 DashScope Tokenizer API 精确计数, API 不可用时回退估算
 */
@Slf4j
@Component
public class ContextManager {

    private static final int KEEP_LAST_MESSAGES = 20;

    private final RestClient restClient;

    @Autowired
    private TokenCounter tokenCounter;

    private static final String SUMMARY_DIR = "data/chat_summaries";

    @Value("${rag.compression.max-tokens:500000}")
    private int maxTokens;

    @Value("${rag.compression.truncate-threshold:0.70}")
    private double truncateThreshold;

    @Value("${rag.compression.summarize-threshold:0.90}")
    private double summarizeThreshold;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    // 追踪每个会话是否已压缩过，避免重复压缩
    private final Set<String> summarizedSessions = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> truncatedSessions = Collections.synchronizedSet(new HashSet<>());

    public ContextManager() {
        this.restClient = RestClient.builder().build();
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(SUMMARY_DIR));
        } catch (java.io.IOException ignored) {}
    }

    // ---- 暴露给 ChatMemoryStore ----

    /**
     * 对完整历史消息列表执行多层压缩，返回压缩后的消息列表
     */
    public List<ChatMessage> compact(List<ChatMessage> fullHistory, String memoryId) {
        if (fullHistory == null || fullHistory.isEmpty()) {
            return fullHistory;
        }

        int currentTokens = tokenCounter.countTokens(fullHistory);
        log.debug("[ContextManager] memoryId={}, messages={}, tokens={} (API 精确)",
                memoryId, fullHistory.size(), currentTokens);

        // Layer 1: 工具结果归档（始终执行）
        List<ChatMessage> result = archiveOldToolResults(fullHistory, memoryId);

        // Layer 3: 90% 阈值 → 摘要压缩
        if (currentTokens >= maxTokens * summarizeThreshold) {
            log.warn("[ContextManager] Token {}/{} >= 90%, 触发摘要压缩 session={}",
                    currentTokens, maxTokens, memoryId);
            result = summarizeAndKeepLast(result, memoryId);
            summarizedSessions.add(memoryId);
            truncatedSessions.remove(memoryId);
            return result;
        }

        // Layer 2: 70% 阈值 → 截断旧消息
        if (currentTokens >= maxTokens * truncateThreshold) {
            log.info("[ContextManager] Token {}/{} >= 70%, 截断旧消息 session={}",
                    currentTokens, maxTokens, memoryId);
            result = truncateOldest(result, memoryId);
            truncatedSessions.add(memoryId);
            return result;
        }

        return result;
    }

    /** ChatMemoryStore 在 updateMessages 后调用，用于追踪最新 token 数 */
    public void notifyNewMessages(String memoryId, List<ChatMessage> newMessages) {
        // 当前仅用于日志，后续可扩展
        if (!newMessages.isEmpty()) {
            log.debug("[ContextManager] session={} 新增 {} 条消息", memoryId, newMessages.size());
        }
    }

    // ---- Layer 1: 工具结果过期归档 (时间阈值) ----

    /**
     * 将过期的工具调用结果替换为占位符。
     * 判断依据：消息在历史列表中的位置（前40%视为旧消息，预估超过30分钟）。
     *
     * 与 WindFarmDataTools.compactToolResult() 的分工：
     *   - compactToolResult: 立即处理，大结果 → 结构化摘要（保留关键信息）
     *   - archiveOldToolResults: 延迟处理，旧结果 → 占位符（时间过期）
     */
    private List<ChatMessage> archiveOldToolResults(List<ChatMessage> messages, String memoryId) {
        int totalSize = messages.size();
        if (totalSize == 0) return messages;

        // 前 40% 的消息视为旧消息（保守估计超过30分钟）
        int cutoffIndex = (int) (totalSize * 0.4);

        List<ChatMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);

            if (i < cutoffIndex) {
                // 检查是否为工具结果消息
                if (msg instanceof ToolExecutionResultMessage toolMsg) {
                    String placeholder = String.format(
                        "[工具结果已过期归档] 工具: %s | session: %s | "
                        + "该工具调用结果已超过30分钟，完整数据已保存到磁盘。"
                        + "如需查看请使用 searchMemories 或 readToolResultFile 工具。",
                        toolMsg.toolName(), memoryId);
                    result.add(AiMessage.from(placeholder));
                    log.debug("[ContextManager] 过期工具结果归档: tool={}, session={}",
                            toolMsg.toolName(), memoryId);
                    continue;
                }

                // 检查 AiMessage 是否包含已归档的摘要标记（超过两轮的摘要也视为过期）
                if (msg instanceof AiMessage aiMsg) {
                    String text = aiMsg.text();
                    if (text != null && text.contains("[数据摘要]") && i < cutoffIndex) {
                        String placeholder = String.format(
                            "[工具结果已过期归档] 该结果在上文摘要中已超过时效。"
                            + "如需重新查询请使用对应工具。");
                        result.add(AiMessage.from(placeholder));
                        continue;
                    }
                }
            }

            result.add(msg);
        }
        return result;
    }

    // ---- Layer 2: 消息截断 (70%) ----

    private List<ChatMessage> truncateOldest(List<ChatMessage> messages, String memoryId) {
        // 保留最后 50 条消息，确保上下文足够
        int keep = Math.min(messages.size(), 50);
        List<ChatMessage> kept = new ArrayList<>(messages.subList(messages.size() - keep, messages.size()));

        // 在开头插入截断标记
        int removed = messages.size() - keep;
        if (removed > 0) {
            String marker = String.format(
                    "[上下文截断] 已移除 %d 条较早的对话消息 (token 达到 70%% 阈值)。"
                    + "以下是最近的 %d 条消息。如需完整历史，请使用 getChatHistory 工具。",
                    removed, keep);
            List<ChatMessage> result = new ArrayList<>();
            result.add(SystemMessage.from(marker));
            result.addAll(kept);
            log.info("[ContextManager] 截断完成 session={}: {} → {} 条消息", memoryId, messages.size(), keep);
            return result;
        }
        return messages;
    }

    // ---- Layer 3: 摘要压缩 (90%) ----

    private List<ChatMessage> summarizeAndKeepLast(List<ChatMessage> messages, String memoryId) {
        if (messages.size() <= KEEP_LAST_MESSAGES) {
            return messages;
        }

        // 分割: 保留最后 20 条，其余用于生成摘要
        int split = messages.size() - KEEP_LAST_MESSAGES;
        List<ChatMessage> toSummarize = messages.subList(0, split);
        List<ChatMessage> recent = new ArrayList<>(messages.subList(split, messages.size()));

        // 生成摘要
        String summary = generateSummary(toSummarize, memoryId);
        String prevSummary = loadSummary(memoryId);

        List<ChatMessage> result = new ArrayList<>();

        // 插入摘要作为上下文
        StringBuilder summaryText = new StringBuilder();
        summaryText.append("[对话历史摘要 — 上下文已压缩]\n");
        if (prevSummary != null && !prevSummary.isEmpty()) {
            summaryText.append("【此前摘要】\n").append(prevSummary).append("\n\n");
        }
        summaryText.append("【本轮摘要】\n").append(summary).append("\n");
        summaryText.append("---\n以下是最近的对话:");

        result.add(SystemMessage.from(summaryText.toString()));
        result.addAll(recent);

        // 保存摘要到本地文件
        saveSummary(memoryId, summary);

        log.info("[ContextManager] 摘要压缩完成 session={}: {} → {} 条消息",
                memoryId, messages.size(), result.size());

        return result;
    }

    private String generateSummary(List<ChatMessage> messages, String memoryId) {
        try {
            StringBuilder conversationText = new StringBuilder();
            for (ChatMessage msg : messages) {
                String role = msg instanceof UserMessage ? "用户" :
                              msg instanceof AiMessage ? "助手" :
                              msg instanceof ToolExecutionResultMessage ? "工具结果" : "系统";
                String text = extractText(msg);
                if (text != null && !text.trim().isEmpty()) {
                    conversationText.append("[").append(role).append("] ").append(text).append("\n");
                }
            }

            String prompt = String.format(
                "请将以下对话历史压缩为一段简洁的摘要（300字以内），只保留: "
                + "1) 用户的核心问题和需求 "
                + "2) 关键的技术决策和结论 "
                + "3) 重要的故障代码、技术参数等数据\n\n%s",
                conversationText.toString().length() > 8000
                    ? conversationText.substring(0, 8000)
                    : conversationText.toString()
            );

            Map<String, Object> body = Map.of(
                "model", "qwen-plus",
                "messages", new Object[]{Map.of("role", "user", "content", prompt)},
                "max_tokens", 500
            );

            var resp = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && resp.containsKey("choices")) {
                var choices = (List<Map<String, Object>>) resp.get("choices");
                String summary = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                log.info("[ContextManager] 摘要生成成功 session={}, length={}", memoryId, summary.length());
                return summary;
            }
        } catch (Exception e) {
            log.warn("[ContextManager] 摘要生成失败 session={}: {}", memoryId, e.getMessage());
        }
        return "[摘要生成失败] 请参考后续最新对话。";
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof UserMessage um) {
            return um.contents().stream()
                    .filter(c -> c instanceof dev.langchain4j.data.message.TextContent)
                    .map(c -> ((dev.langchain4j.data.message.TextContent) c).text())
                    .reduce("", (a, b) -> a + b);
        }
        if (msg instanceof AiMessage am) return am.text();
        if (msg instanceof ToolExecutionResultMessage tm) return tm.toolName() + ": " + tm.text();
        if (msg instanceof SystemMessage sm) return sm.text();
        return msg.toString();
    }

    // ---- Token 计算 (委托给 TokenCounter, 优先使用 API 精确计数) ----

    public int estimateTokens(List<ChatMessage> messages) {
        return tokenCounter.countTokens(messages);
    }

    public int estimateTokens(String text) {
        return tokenCounter.countTokens(text);
    }

    // ---- 摘要文件 I/O ----

    private void saveSummary(String memoryId, String summary) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(SUMMARY_DIR, memoryId + ".txt");
            java.nio.file.Files.writeString(p, summary);
        } catch (java.io.IOException e) {
            log.warn("[ContextManager] 保存摘要失败 session={}: {}", memoryId, e.getMessage());
        }
    }

    private String loadSummary(String memoryId) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(SUMMARY_DIR, memoryId + ".txt");
            if (java.nio.file.Files.exists(p)) return java.nio.file.Files.readString(p);
        } catch (java.io.IOException ignored) {}
        return null;
    }

    // ---- 状态查询 ----

    public boolean isSessionSummarized(String memoryId) {
        return summarizedSessions.contains(memoryId);
    }

    public void resetSession(String memoryId) {
        summarizedSessions.remove(memoryId);
        truncatedSessions.remove(memoryId);
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(SUMMARY_DIR, memoryId + ".txt"));
        } catch (java.io.IOException ignored) {}
    }
}
