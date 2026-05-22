package com.itheima.agent.service;

import com.itheima.agent.aiservice.ChatAssistant;
import com.itheima.agent.aiservice.QueryAssistant;
import com.itheima.agent.aiservice.RagAssistant;
import com.itheima.agent.metrics.SessionMetricsTracker;
import com.itheima.agent.pojo.MemoryIdContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent 路由器 — 意图识别 → 路由到对应的子 Agent
 *
 * 路由策略:
 *   CHAT  → ChatAssistant  (Qwen-Flash, 无工具, 快速)
 *   RAG   → RagAssistant   (Qwen-Plus, 知识库+历史+记忆工具)
 *   QUERY → QueryAssistant (Qwen-Plus, 实时数据+风机状态工具)
 *
 * 上下文串行: 所有 Agent 使用同一个 ChatMemoryProvider，同一个 memoryId。
 */
@Slf4j
@Service
public class AgentRouter {

    @Autowired
    private IntentRouter intentRouter;

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private RagAssistant ragAssistant;

    @Autowired
    private QueryAssistant queryAssistant;

    @Autowired
    private SessionMetricsTracker sessionMetrics;

    /**
     * 路由并流式返回响应
     */
    public Flux<String> route(String memoryId, String message) {
        MemoryIdContext.set(memoryId);

        IntentRouter.IntentResult intent = intentRouter.classify(message);
        log.info("[AgentRouter] session={}, intent={}, reason={}", memoryId, intent.type(), intent.reason());

        return switch (intent.type()) {
            case RAG -> {
                log.info("[AgentRouter] → RagAssistant (Qwen-Plus, RAG tools)");
                yield ragAssistant.chatStream(memoryId, message);
            }
            case QUERY -> {
                log.info("[AgentRouter] → QueryAssistant (Qwen-Plus, query tools)");
                yield queryAssistant.chatStream(memoryId, message);
            }
            case CHAT -> {
                log.info("[AgentRouter] → ChatAssistant (Qwen-Flash, no tools)");
                yield chatAssistant.chatStream(memoryId, message);
            }
        };
    }

    /**
     * 仅供管理/调试: 强制指定 Agent 路由
     */
    public Flux<String> routeTo(String agent, String memoryId, String message) {
        MemoryIdContext.set(memoryId);
        return switch (agent.toLowerCase()) {
            case "rag"   -> ragAssistant.chatStream(memoryId, message);
            case "query" -> queryAssistant.chatStream(memoryId, message);
            default      -> chatAssistant.chatStream(memoryId, message);
        };
    }
}
