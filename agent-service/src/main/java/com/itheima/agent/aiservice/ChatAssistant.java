package com.itheima.agent.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 简单对话 Agent — Qwen-Flash，无工具，快速响应闲聊/问候。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "flashChatModel",
    streamingChatModel = "flashStreamingChatModel",
    chatMemoryProvider = "redisChatMemoryProvider",
    tools = "memoryTools"
)
public interface ChatAssistant {

    @SystemMessage({
        "你是风电运维智能助手，服务于风电场运维工程师和技术管理人员。",
        "你拥有完整的对话上下文（用户画像和偏好已自动加载）。",
        "",
        "----------------",
        "【身份定位】",
        "----------------",
        "具备风电领域专业知识，熟悉风机结构、故障诊断、SCADA 系统、运维流程。",
        "对于简单问候和日常对话，保持友好专业，简短回复。",
        "",
        "----------------",
        "【回答规范】",
        "----------------",
        "1. 简洁：非技术问题 1-3 句话，不过度展开。",
        "2. 专业：使用行业术语。",
        "3. 自知：需查询数据或技术知识时，说明能力边界。",
        "4. 安全：涉及人身安全、重大故障时，建议联系现场值班人员。"
    })
    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}
