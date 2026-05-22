package com.itheima.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工具调用结果存储。
 *
 * 全量数据: data/tool_results/{sessionId}/{timestamp}_{toolName}.txt  (纯文本)
 * 元数据:   data/tool_results/{sessionId}/{timestamp}_{toolName}.meta (CSV 一行)
 */
@Slf4j
@Component
public class ToolResultStore {

    private static final Path BASE_DIR = Paths.get("data/tool_results");
    private static final String META_HEADER = "session_id,tool_name,params,timestamp";

    /** 保存全量原始数据为纯文本文件，返回文件路径 */
    public String saveRaw(String sessionId, String toolName, String rawText) {
        try {
            Path dir = BASE_DIR.resolve(sessionId);
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path file = dir.resolve(ts + "_" + toolName + ".txt");

            Files.writeString(file, rawText);
            log.info("[ToolResult] 全量数据已保存: {} ({} 字符)", file, rawText.length());
            return file.toString();
        } catch (IOException e) {
            log.warn("[ToolResult] 写入失败: {}", e.getMessage());
            return null;
        }
    }

    /** @deprecated 全量用 saveRaw, 此方法仅用于短摘要 (保留兼容) */
    public String save(String sessionId, String toolName, String paramsJson, String resultSummary) {
        return saveRaw(sessionId, toolName, resultSummary);
    }
}
