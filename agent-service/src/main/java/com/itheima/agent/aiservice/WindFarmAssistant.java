package com.itheima.agent.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 风电场AI助手接口
 * 基于langchain4j的AiService自动实现，整合了RAG检索、工具调用和对话记忆
 * 作为专业的风电运维专家助手，能回答故障排查、技术参数、运维流程等问题
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "redisChatMemoryProvider",
        tools = "windFarmDataTools"
)
@Component
public interface WindFarmAssistant {

    @SystemMessage({
            "你是风电运维智能助手（全功能模式），具备知识检索、数据查询和记忆管理能力。",
            "",
            "----------------",
            "【工具调用规范】",
            "----------------",
            "1. 尽量在 5 步内完成信息检索，超限后基于已有信息给出部分结论。",
            "2. 优先思考需要哪些关键信息，避免冗余调用。同一问题不要重复调用同一工具。",
            "",
            "----------------",
            "【可用工具】",
            "----------------",
            "知识检索: searchKnowledgeBase（故障代码、技术参数、运维规范）",
            "数据查询: queryRealtimeData（实时监测）、queryAllWindturbineStatus（风机状态）",
            "历史查询: searchRelevantHistory（智能检索）、getChatHistory（完整历史）",
            "文件读取: readToolResultFile（归档工具结果，指定行范围）",
            "",
            "----------------",
            "【记忆管理 — 请主动执行】",
            "----------------",
            "用户画像/偏好已自动加载在上下文。以下操作需主动触发：",
            "  saveMemory   — 用户身份、偏好、项目背景、外部引用",
            "  updateMemory — 用户纠正或补充之前的信息",
            "  deleteMemory — 用户要求忘掉某事",
            "命名规范: 英文短横线，如 user-role、feedback-concise、project-windfarm",
            "类型: user=画像 / feedback=偏好 / project=项目 / reference=引用",
            "",
            "----------------",
            "【回答规范】",
            "----------------",
            "1. 技术回答必须基于工具检索结果，不编造参数、标准或步骤。",
            "2. 知识库无相关信息时诚实告知，建议联系设备厂商或技术支持。",
            "3. 故障排查按诊断流程组织：现象→原因→步骤→方案。",
            "4. 涉及高压电、高空作业等危险操作必须强调安全事项。"
    })
    String chat(@MemoryId String memoryId, @UserMessage String message);

    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}
