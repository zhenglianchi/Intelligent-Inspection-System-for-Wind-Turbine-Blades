package com.itheima.agent.tools;

import com.itheima.agent.service.EnhancedRAGService;
import com.itheima.agent.service.ToolExecutionHooks;
import com.itheima.agent.service.ToolResultSummarizer;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * RAG Agent 工具 — 知识库检索 + 归档文件读取。
 */
@Slf4j
@Component("ragTools")
public class RagTools {

    @Autowired private EnhancedRAGService enhancedRAGService;
    @Autowired private ToolExecutionHooks hooks;
    @Autowired private ToolResultSummarizer summarizer;

    @Tool("检索风电运维知识库。查询故障代码含义、部件故障排查、技术参数、运维流程、行业标准等专业知识。")
    public String searchKnowledgeBase(
            @P("搜索查询语句，包含关键词如故障代码、部件名称、技术术语") String query) {
        log.info("[RAG] 检索: {}", query);
        String blocked = hooks.checkBlocked("知识库检索");
        if (blocked != null) return blocked;
        try {
            EnhancedRAGService.EnhancedRAGResult result = enhancedRAGService.retrieveWithDetails(query);
            if (result.contents() == null || result.contents().isEmpty()) {
                hooks.recordSuccess();
                return "未在知识库中找到与 '" + query + "' 相关的信息。";
            }
            StringBuilder sb = new StringBuilder();
            if (result.hasRewrite()) sb.append("[查询优化] 已改写为: ").append(result.rewrittenQuery()).append("\n\n");
            int count = 0;
            for (Content c : result.contents()) {
                if (count >= 5) break;
                sb.append("[").append(++count).append("] ").append(c.textSegment().text()).append("\n\n");
            }
            hooks.recordSuccess();
            return summarizer.process("RAG检索结果", sb.toString());
        } catch (Exception e) {
            return hooks.recordFailure("知识库检索", e);
        }
    }

    @Tool("读取归档的工具结果文件（全量数据保存于此）。指定文件路径和行范围按需读取。")
    public String readToolResultFile(
            @P("文件路径（从摘要信息中获取）") String filePath,
            @P(value = "起始行号（1-indexed，可选）", required = false) String startLine,
            @P(value = "结束行号（1-indexed，可选）", required = false) String endLine) {
        String blocked = hooks.checkBlocked("文件读取");
        if (blocked != null) return blocked;
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return "文件不存在: " + filePath;
            List<String> allLines = Files.readAllLines(path);
            int total = allLines.size();
            int s = 1, e = total;
            try { if (startLine != null && !startLine.isEmpty()) s = Integer.parseInt(startLine); } catch (NumberFormatException ignored) {}
            try { if (endLine != null && !endLine.isEmpty()) e = Integer.parseInt(endLine); } catch (NumberFormatException ignored) {}
            s = Math.max(1, Math.min(s, total));
            e = Math.max(s, Math.min(e, total));
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件: %s | 总行数: %d | 读取: %d-%d 行\n\n", filePath, total, s, e));
            for (int i = s - 1; i < e; i++) sb.append(String.format("%6d| %s\n", i + 1, allLines.get(i)));
            hooks.recordSuccess();
            return sb.toString();
        } catch (Exception e) {
            return hooks.recordFailure("文件读取", e);
        }
    }
}
