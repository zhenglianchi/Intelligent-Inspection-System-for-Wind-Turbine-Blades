package com.itheima.agent.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 意图识别路由器 — 使用 Qwen-Flash 将用户消息分类，决定由哪个 Agent 处理。
 *
 * 分类:
 *   CHAT   → 简单对话/问候/闲聊，无需工具 (→ ChatAssistant, Qwen-Flash)
 *   RAG    → 知识库检索/故障排查/技术规范 (→ RagAssistant, Qwen-Plus)
 *   QUERY  → 数据库查询/实时数据/风机状态 (→ QueryAssistant, Qwen-Plus)
 *
 * 上下文串行: 所有 Agent 共享同一个 ChatMemoryProvider，session 一致。
 */
@Slf4j
@Service
public class IntentRouter {

    private final ChatModel flashModel;

    public IntentRouter(@Qualifier("flashChatModel") ChatModel flashModel) {
        this.flashModel = flashModel;
    }

    /**
     * 分类用户意图。返回 IntentType + 简洁理由。
     */
    public IntentResult classify(String userMessage) {
        String prompt = """
            你是一个意图分类器。根据用户消息判断意图，只回复一个单词：
            - CHAT: 问候、闲聊、简单对话、不需要检索数据或知识库的问题
            - RAG: 需要查询知识库、技术文档、故障代码、运维规范、设备参数
            - QUERY: 需要查询数据库、实时监测数据、风机运行状态、风场统计

            用户消息: %s

            只回复 CHAT、RAG 或 QUERY。""".formatted(userMessage);

        try {
            String response = flashModel.chat(prompt);
            if (response == null) return new IntentResult(IntentType.CHAT, "fallback");

            String trimmed = response.trim().toUpperCase();
            if (trimmed.contains("QUERY")) return new IntentResult(IntentType.QUERY, "matched:" + trimmed);
            if (trimmed.contains("RAG")) return new IntentResult(IntentType.RAG, "matched:" + trimmed);
            return new IntentResult(IntentType.CHAT, "default:" + trimmed);
        } catch (Exception e) {
            log.warn("[IntentRouter] 分类失败, 回退到 CHAT: {}", e.getMessage());
            return new IntentResult(IntentType.CHAT, "error_fallback");
        }
    }

    public enum IntentType { CHAT, RAG, QUERY }

    public record IntentResult(IntentType type, String reason) {
        public boolean isChat()  { return type == IntentType.CHAT; }
        public boolean isRag()   { return type == IntentType.RAG; }
        public boolean isQuery() { return type == IntentType.QUERY; }
    }
}
