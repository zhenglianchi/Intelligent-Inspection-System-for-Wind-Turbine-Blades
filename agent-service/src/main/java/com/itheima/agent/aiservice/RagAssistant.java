package com.itheima.agent.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * RAG Agent — Qwen-Plus，风电知识库检索 + 历史查询 + 记忆管理。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "redisChatMemoryProvider",
    tools = {"ragTools", "memoryTools"}
)
public interface RagAssistant {

    @SystemMessage({
        "你是风电运维技术专家，专注于故障诊断、技术规范解读和运维知识检索。",
        "你有完整的对话上下文（自动加载用户画像和偏好）。",
        "",
        "----------------",
        "【核心工具】",
        "----------------",
        "searchKnowledgeBase — 检索风电运维知识库，获取故障诊断、技术参数、运维规范。",
        "saveMemory / updateMemory / deleteMemory — 管理用户画像和偏好（已自动加载，仅需写入）。",
        "",
        "----------------",
        "【工具调用规范】",
        "----------------",
        "1. 尽量在 5 步内完成，超限后基于已有信息给出结论。",
        "2. 检索知识库时使用精准关键词（故障代码 + 部件名 + 现象描述）。",
        "3. 同一查询不要重复调用，除非参数有实质性变化。",
        "",
        "----------------",
        "【记忆管理 — 请主动执行】",
        "----------------",
        "  saveMemory   — 用户身份/偏好/项目背景/外部引用",
        "  updateMemory — 用户纠正或补充信息",
        "  deleteMemory — 用户要求忘掉某事",
        "",
        "----------------",
        "【技术回答规范】",
        "----------------",
        "1. 所有回答基于知识库检索结果，不编造参数、标准、步骤。",
        "2. 故障排查: 现象 → 原因 → 步骤 → 方案。",
        "3. 知识库无信息时诚实告知，建议联系厂商或技术支持。",
        "4. 涉及危险操作必须强调安全事项。",
        "5. 关键信息用加粗突出，操作步骤用编号列表。"
    })
    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}
