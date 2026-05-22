package com.itheima.agent.service;

import com.itheima.agent.pojo.MemoryIdContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;

/**
 * 工具结果处理器 — 行数 ≤ 50 直接全文返回，> 50 落盘 + Flash 行范围摘要。
 */
@Slf4j
@Service
public class ToolResultSummarizer {

    private static final int MAX_FULL_DISPLAY_LINES = 50;

    private final ChatModel flashModel;

    @Autowired
    private ToolResultStore toolResultStore;

    public ToolResultSummarizer(@Qualifier("flashChatModel") ChatModel flashModel) {
        this.flashModel = flashModel;
    }

    /**
     * 处理工具结果。
     * ≤ 50 行 → 原文直接返回，不落盘。
     * > 50 行 → 落盘 data/tool_results/ + Qwen-Flash 行范围摘要。
     */
    public String process(String toolName, String rawText) {
        if (rawText == null) return null;

        int totalLines = rawText.split("\n", -1).length;

        // ≤ 50 行: 全量直接返回，不落盘
        if (totalLines <= MAX_FULL_DISPLAY_LINES) {
            return "[以下是工具查询的完整结果，请直接以表格形式呈现给用户，不要做口头总结]\n\n" + rawText;
        }

        // > 50 行: 落盘到本地
        String sessionId = MemoryIdContext.get();
        if (sessionId == null) sessionId = "unknown";
        String filePath = toolResultStore.saveRaw(sessionId, toolName, rawText);

        // Flash 生成行范围摘要
        String truncated = rawText.length() > 3000 ? rawText.substring(0, 3000) : rawText;
        String prompt = String.format("""
            将以下工具返回的数据压缩为结构化摘要，按行范围描述内容。
            格式要求:
            - 每行格式: "行  X-Y: [内容描述] (关键数值)"
            - 描述简洁，突出关键信息（状态、数量、异常等）
            - 保留具体的编号、数值、时间

            工具: %s
            总行数: %d

            原始数据:
            %s""", toolName, totalLines, truncated);

        try {
            String summary = flashModel.chat(prompt);
            if (summary == null || summary.trim().isEmpty()) {
                return String.format("[全量数据已保存 — 请告知用户文件位置]\n文件: %s\n行数: %d | 大小: %d 字符",
                    filePath, totalLines, rawText.length());
            }
            return String.format("[结构化摘要 — 数据量过大已自动压缩，请直接向用户呈现以下摘要]\n工具: %s | 原始: %d行/%d字符 | 文件: %s\n\n%s\n\n需要详情时调用 readToolResultFile(\"%s\", startLine, endLine)。",
                toolName, totalLines, rawText.length(), filePath, summary.trim(), filePath);
        } catch (Exception e) {
            log.warn("[Summarizer] Flash 失败: {}", e.getMessage());
            return String.format("[全量数据已保存 — 请告知用户文件位置]\n文件: %s\n行数: %d | 大小: %d 字符",
                filePath, totalLines, rawText.length());
        }
    }
}
