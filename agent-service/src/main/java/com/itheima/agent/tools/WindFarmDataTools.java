package com.itheima.agent.tools;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.RealtimeDO;
import com.itheima.agent.feign.RealtimeFeignClient;
import com.itheima.agent.feign.WindturbineFeignClient;
import com.itheima.agent.pojo.MemoryIdContext;
import com.itheima.agent.repository.RedisChatMemoryProvider;
import com.itheima.agent.service.DegradationService;
import com.itheima.agent.service.EnhancedRAGService;
import com.itheima.agent.service.HistoryRetrievalService;
import com.itheima.agent.service.MemoryManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.rag.content.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WindFarmDataTools {

    @Autowired
    private RedisChatMemoryProvider chatMemoryProvider;

    @Autowired
    private EnhancedRAGService enhancedRAGService;

    @Autowired
    private DegradationService degradationService;

    @Autowired
    private HistoryRetrievalService historyRetrievalService;

    @Autowired
    private RealtimeFeignClient realtimeFeignClient;

    @Autowired
    private WindturbineFeignClient windturbineFeignClient;

    @Autowired
    private com.itheima.agent.service.ToolResultStore toolResultStore;

    @Autowired
    private com.itheima.agent.service.MemoryManager memoryManager;

    @Autowired
    private com.itheima.agent.service.ToolExecutionHooks hooks;

    @org.springframework.beans.factory.annotation.Value("${rag.turbine.default-limit:50}")
    private int defaultLimit;

    /**
     * 工具 1：风电知识库检索（RAG）- 增强版
     * 集成查询改写和 HyDE 功能
     */
    @Tool("检索风电运维知识库。当用户询问以下内容时必须调用此工具：" +
            "1. 故障代码的含义和处理方法；" +
            "2. 风机部件（叶片、齿轮箱、发电机、变桨系统等）的故障排查；" +
            "3. 技术参数、运维流程、操作规范；" +
            "4. 风电行业标准、技术规范；" +
            "5. 设备型号、技术规格等专业知识。" +
            "注意：不要用于闲聊、问候或简单的日常对话。")
    public String searchKnowledgeBase(
            @P("用户的搜索查询语句，应包含关键词，如故障代码、部件名称、技术术语等") String query
    ) {
        log.info("🔍 [RAG Tool] 触发知识库检索，原始查询：{}", query);
        if (hooks.shouldBypass()) return hooks.bypassMessage("知识库检索");
        if (hooks.isStepLimitReached()) return hooks.stepLimitMessage();

        try {
            EnhancedRAGService.EnhancedRAGResult result = enhancedRAGService.retrieveWithDetails(query);

            if (result.contents() == null || result.contents().isEmpty()) {
                log.warn("⚠️ [RAG Tool] 知识库未检索到相关内容");
                hooks.recordSuccess();
                return "未在知识库中找到与 '" + query + "' 相关的信息。请尝试使用其他关键词搜索，或联系技术支持。";
            }

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("📚 从知识库检索到以下相关信息：\n\n");

            if (result.hasRewrite()) {
                resultBuilder.append("💡 [查询优化] 原始查询已改写为：").append(result.rewrittenQuery()).append("\n\n");
            }

            if (result.hasHyDE()) {
                resultBuilder.append("📄 [HyDE] 已生成假设文档辅助检索\n\n");
            }

            int count = 0;
            for (Content content : result.contents()) {
                if (count >= 5) break;
                String text = content.textSegment().text();
                resultBuilder.append("【片段 ").append(++count).append("】\n");
                resultBuilder.append(text).append("\n\n");
            }

            log.info("✅ [RAG Tool] 检索完成，返回 {} 条片段，耗时 {} ms",
                    result.totalContents(), result.durationMs());

            hooks.recordSuccess();
            String finalResult = resultBuilder.toString();
            return compactToolResult("searchKnowledgeBase", query, finalResult);

        } catch (Exception e) {
            return hooks.recordFailure("知识库检索", e);
        }
    }

    /**
     * 工具 2：风机实时监测数据多条件灵活查询
     * 支持单条件或多条件组合，LLM 按用户意图传入任意条件
     *
     * 示例：
     * - "查风场10001的数据" → windfarm="10001"
     * - "1号风机最近有没有故障" → windfarm="10001", windturbine=1, status=1
     * - "最近一周的监测数据" → startTime="2026-04-23 00:00:00", endTime="2026-04-30 23:59:59"
     * - "查风场10001的1号风机最近3天正常状态的数据" → windfarm="10001", windturbine=1, status=0, startTime="..."
     */
    @Tool("查询风机实时监测数据，按条件灵活组合。重要说明：" +
            "1. 风场编号是字符串如'10001'、'10002'、'20001'等，风机编号是整数如1、2、3；" +
            "2. 状态码：0=正常 1=故障 9=未连接；" +
            "3. 当用户说'风场10001'时指的是windfarm='10001'，当用户说'1号风机'时指的是windturbine='1'；" +
            "4. 时间范围默认用当天，如'2026-05-02 00:00:00'到当前时间；" +
            "5. 所有参数均为可选，不传则不作为过滤条件。")
    public String queryRealtimeData(
            @P("风场编号，字符串如'10001'，不填则不限风场") String windfarm,
            @P(value = "风机编号，整数如1、2、3，不填则不限风机", required = false) String windturbine,
            @P(value = "状态码，0正常/1故障/9未连接，不填则不限状态", required = false) String status,
            @P(value = "开始时间，格式yyyy-MM-dd HH:mm:ss，默认为当天00:00:00", required = false) String startTime,
            @P(value = "结束时间，格式yyyy-MM-dd HH:mm:ss，默认为当前时间", required = false) String endTime,
            @P(value = "返回条数上限，默认50", required = false) String limit
    ) {
        log.info("📊 [Data Tool] 多条件查询 - 风场:{}, 风机:{}, 状态:{}, 时间:{}-{}",
                windfarm, windturbine, status, startTime, endTime);
        if (hooks.shouldBypass()) return hooks.bypassMessage("数据查询");
        if (hooks.isStepLimitReached()) return hooks.stepLimitMessage();

        try {
            Integer limitVal = parseOrNull(limit);
            if (limitVal == null || limitVal <= 0) limitVal = defaultLimit;

            Integer wtVal = parseOrNull(windturbine);
            Integer stVal = parseOrNull(status);

            Result<List<RealtimeDO>> result = realtimeFeignClient.queryByConditions(
                    windfarm, wtVal, stVal, startTime, endTime, limitVal);

            if (result == null || !Result.Status.SUCCESS.getCode().equals(result.getStatus())) {
                hooks.recordSuccess(); // 工具执行了但外部服务返回错误
                return "查询失败：" + (result != null ? result.getMessage() : "服务无响应");
            }

            List<RealtimeDO> records = result.getData();
            if (records == null || records.isEmpty()) {
                hooks.recordSuccess();
                return buildEmptyResultMessage(windfarm, wtVal, stVal, startTime, endTime);
            }

            hooks.recordSuccess();
            String formatted = formatResultTable(records);
            return compactToolResult("queryRealtimeData",
                    String.format("windfarm=%s,windturbine=%s,status=%s", windfarm, windturbine, status), formatted);

        } catch (Exception e) {
            return hooks.recordFailure("数据查询", e);
        }
    }

    private Integer parseOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private String buildEmptyResultMessage(String windfarm, Integer windturbine, Integer status,
                                            String startTime, String endTime) {
        StringBuilder cond = new StringBuilder();
        if (windfarm != null) cond.append("风场=").append(windfarm).append(" ");
        if (windturbine != null) cond.append("风机=").append(windturbine).append(" ");
        if (status != null) {
            String[] desc = {"正常", "故障", null, null, null, null, null, null, null, "未连接"};
            String s = status >= 0 && status < desc.length ? desc[status] : String.valueOf(status);
            cond.append("状态=").append(s != null ? s : status).append(" ");
        }
        if (startTime != null) cond.append("开始=").append(startTime).append(" ");
        if (endTime != null) cond.append("结束=").append(endTime).append(" ");
        return "未查询到符合条件的风机数据。查询条件：" + cond.toString().trim();
    }

    private String formatResultTable(List<RealtimeDO> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询到 ").append(records.size()).append(" 条监测记录：\n\n");
        sb.append("| 接收时间 | 风场 | 风机 | 状态 | 特征1 | 特征2 | 特征3 |\n");
        sb.append("|----------|------|------|------|-------|-------|-------|\n");

        for (RealtimeDO d : records) {
            String s = d.getStatus() != null ? d.getStatus().toString() : "-";
            sb.append(String.format("| %s | %s | %d | %s | %.2f | %.2f | %.2f |\n",
                    d.getGmtReceived() != null ? d.getGmtReceived().toString() : "-",
                    d.getWindfarm() != null ? d.getWindfarm() : "-",
                    d.getWindturbine() != null ? d.getWindturbine() : 0,
                    s,
                    d.getFeature1() != null ? d.getFeature1() : 0,
                    d.getFeature2() != null ? d.getFeature2() : 0,
                    d.getFeature3() != null ? d.getFeature3() : 0));
        }
        return sb.toString();
    }

    /**
     * 工具 3：聊天历史查询
     */
    @Tool("获取当前用户的聊天历史记录。当用户询问之前的对话内容、要求回顾历史、或需要参考之前的故障排查步骤时使用。")
    public String getChatHistory() {
        String userId = MemoryIdContext.get();

        // 降级检查：如果工具已禁用，直接返回降级提示
        if (!degradationService.isToolAvailable()) {
            log.warn("⚠️ [降级] 工具调用已禁用，无法获取聊天历史");
            hooks.recordSuccess();
            return "当前系统处于降级模式，历史查询工具暂不可用。请稍后再试。";
        }

        if (userId == null) {
            hooks.recordSuccess();
            return "错误：无法获取当前用户身份。";
        }

        try {
            List<ChatMessage> history = chatMemoryProvider.getFullHistory(userId);

            if (history.isEmpty()) {
                hooks.recordSuccess();
                return "当前用户暂无历史聊天记录。";
            }

            hooks.recordSuccess();
            String result = ChatMessageSerializer.messagesToJson(history);
            return compactToolResult("getChatHistory", userId, result);
        } catch (Exception e) {
            hooks.recordFailureOnly();
            log.error("❌ [History Tool] 获取历史记录失败", e);
            return "获取历史记录失败: " + e.getMessage();
        }
    }

    /**
     * 工具 5：智能检索相关历史对话
     * 根据当前问题关键词，从完整历史对话中召回相关内容及其上下文
     */
    @Tool("智能检索相关历史对话。当需要从历史对话中查找与当前问题相关的内容时使用此工具，" +
          "会自动根据关键词匹配召回相关对话及其上下两段上下文，比获取完整聊天历史更高效。")
    public String searchRelevantHistory(
            @P("用于检索历史的关键词或问题") String query
    ) {
        String userId = MemoryIdContext.get();

        // 降级检查
        if (!degradationService.isToolAvailable()) {
            log.warn("⚠️ [降级] 工具调用已禁用，无法检索历史");
            hooks.recordSuccess();
            return "当前系统处于降级模式，历史检索工具暂不可用。请稍后再试。";
        }

        if (userId == null) {
            hooks.recordSuccess();
            return "错误：无法获取当前用户身份。";
        }

        try {
            String result = historyRetrievalService.retrieveRelevantHistory(userId, query);
            if (result.isEmpty()) {
                hooks.recordSuccess();
                return "未找到与 '" + query + "' 相关的历史对话。";
            }
            hooks.recordSuccess();
            return compactToolResult("searchRelevantHistory", query, result);
        } catch (Exception e) {
            hooks.recordFailureOnly();
            log.error("❌ [History Search] 检索相关历史失败", e);
            return "检索相关历史失败: " + e.getMessage();
        }
    }

    /**
     * 工具 6：查询风场所有风机的健康状态
     * 状态说明：0-正常，1-故障，9-未连接
     */
    @Tool("查询风场所有风机的健康运行状态。当用户需要查看某个风场全部风机的当前状态、统计正常/故障风机数量时使用。")
    public String queryAllWindturbineStatus(
            @P("风场名称，如'风场1'、'WF10001'") String windfarm
    ) {
        log.info("📊 [Windturbine Tool] 查询所有风机状态 - 风场:{}", windfarm);

        if (!degradationService.isToolAvailable()) {
            log.warn("⚠️ [降级] 工具调用已禁用");
            hooks.recordSuccess();
            return "当前系统处于降级模式，该工具暂不可用。请稍后再试。";
        }

        try {
            if (windfarm == null || windfarm.trim().isEmpty()) {
                hooks.recordSuccess();
                return "错误：必须提供风场名称。";
            }

            Result<Map<String, Integer>> result = windturbineFeignClient.queryAllWindturbineStatus(windfarm);

            if (result == null || !Result.Status.SUCCESS.getCode().equals(result.getStatus())) {
                hooks.recordSuccess();
                return "查询失败：" + (result != null ? result.getMessage() : "服务无响应");
            }

            Map<String, Integer> statusMap = result.getData();

            if (statusMap == null || statusMap.isEmpty()) {
                return "风场 [" + windfarm + "] 未找到任何风机信息。";
            }

            // 统计各状态数量
            int normalCount = 0;
            int faultCount = 0;
            int offlineCount = 0;

            StringBuilder sb = new StringBuilder();
            sb.append("风场 [").append(windfarm).append("] 共 ").append(statusMap.size()).append(" 台风机，当前状态如下：\n\n");
            sb.append("| 风机编号 | 状态 | 状态说明 |\n");
            sb.append("|----------|------|----------|\n");

            for (Map.Entry<String, Integer> entry : statusMap.entrySet()) {
                String turbineId = entry.getKey();
                Integer status = entry.getValue();
                String statusDesc;

                switch (status) {
                    case 0:
                        statusDesc = "正常";
                        normalCount++;
                        break;
                    case 1:
                        statusDesc = "故障";
                        faultCount++;
                        break;
                    case 9:
                        statusDesc = "未连接";
                        offlineCount++;
                        break;
                    default:
                        statusDesc = "未知(" + status + ")";
                }

                sb.append(String.format("| %s | %d | %s |\n", turbineId, status, statusDesc));
            }

            // 添加统计信息
            sb.append("\n统计总结：\n");
            sb.append("- 总计：").append(statusMap.size()).append(" 台\n");
            sb.append("- 正常运行：").append(normalCount).append(" 台\n");
            sb.append("- 故障：").append(faultCount).append(" 台\n");
            sb.append("- 未连接：").append(offlineCount).append(" 台\n");

            log.info("✅ [Windturbine Tool] 查询完成，{} 台风机，正常:{}, 故障:{}, 离线:{}",
                    statusMap.size(), normalCount, faultCount, offlineCount);

            hooks.recordSuccess();
            String statusText = sb.toString();
            return compactToolResult("queryAllWindturbineStatus", windfarm, statusText);

        } catch (Exception e) {
            hooks.recordFailureOnly();
            log.error("❌ [Windturbine Tool] 查询执行出错", e);
            return "查询执行出错：" + e.getMessage();
        }
    }

    // ---- 辅助方法 ----

    /** 工具结果超过此长度时自动保存完整数据到磁盘，返回结构化摘要 */
    private static final int TOOL_RESULT_MAX_CHARS = 500;

    /**
     * 保存完整工具结果到磁盘，若结果过大则返回结构化摘要 + 文件路径引用。
     * 摘要保留前 400 字符关键信息 + 行数统计，LLM 可按需用 readToolResultFile 读取指定行范围。
     */
    private String compactToolResult(String toolName, String params, String result) {
        if (result == null) return null;
        try {
            String sessionId = MemoryIdContext.get();
            if (sessionId == null) sessionId = "unknown";
            // 始终保存完整结果到磁盘
            String filePath = toolResultStore.save(sessionId, toolName, params, result);
            if (filePath == null) return result;

            if (result.length() > TOOL_RESULT_MAX_CHARS) {
                int lineCount = result.split("\n").length;
                // 结构化摘要：保留前 400 字符 + 统计信息 + 文件引用
                String preview = result.substring(0, Math.min(400, result.length()));
                // 截断到最后一个完整行
                int lastNewline = preview.lastIndexOf('\n');
                if (lastNewline > 200) preview = preview.substring(0, lastNewline);

                log.info("[ToolResult] 结果过大 ({} chars, {} lines), 生成摘要并归档: {}",
                        result.length(), lineCount, filePath);
                return String.format(
                    "%s\n\n[数据摘要] 工具: %s | 总字符: %d | 总行数: %d | 文件: %s\n"
                    + "以上为结果预览。如需查看完整数据或特定行范围，请调用 readToolResultFile 工具并指定文件路径和行范围。",
                    preview, toolName, result.length(), lineCount, filePath);
            }
            return result;
        } catch (Exception e) {
            log.warn("记录工具结果失败: {}", e.getMessage());
            return result;
        }
    }

    // ---- 工具 7: 读取磁盘存储的工具结果文件 ----

    @Tool("读取之前工具调用的完整结果文件。支持按行范围读取，避免一次性加载过大文件。" +
          "文件路径在工具返回的摘要信息中提供。指定 startLine 和 endLine 可仅读取部分行（1-indexed，含两端）。")
    public String readToolResultFile(
            @P("要读取的文件路径，例如 data/tool_results/session123/20260519_143000_searchKnowledgeBase.csv") String filePath,
            @P("起始行号（1-indexed，可选，不填则从第1行开始）") String startLine,
            @P("结束行号（1-indexed，可选，不填则读到文件末尾）") String endLine
    ) {
        log.info("📄 [File Tool] 读取工具结果文件: {}, 行范围: {}-{}", filePath, startLine, endLine);
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            List<String> allLines = Files.readAllLines(path);
            int totalLines = allLines.size();

            int start = 1;
            int end = totalLines;
            try {
                if (startLine != null && !startLine.isEmpty()) start = Integer.parseInt(startLine);
                if (endLine != null && !endLine.isEmpty()) end = Integer.parseInt(endLine);
            } catch (NumberFormatException ignored) {}

            start = Math.max(1, Math.min(start, totalLines));
            end = Math.max(start, Math.min(end, totalLines));

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件: %s | 总行数: %d | 当前读取: %d-%d 行\n\n", filePath, totalLines, start, end));
            for (int i = start - 1; i < end; i++) {
                sb.append(String.format("%6d| %s\n", i + 1, allLines.get(i)));
            }

            hooks.recordSuccess();
            String result = sb.toString();
            return compactToolResult("readToolResultFile", filePath, result);
        } catch (Exception e) {
            hooks.recordFailureOnly();
            return "读取文件失败: " + e.getMessage();
        }
    }

    // ---- 工具 8: 保存记忆 ----

    @Tool("保存一条持久化记忆，支持四种类型：" +
          "1. user - 用户角色、偏好、知识背景；" +
          "2. feedback - 用户对助手行为的反馈（应做/不应做）；" +
          "3. project - 项目上下文、截止日期、决策；" +
          "4. reference - 外部系统引用、链接、资源位置。" +
          "当用户明确要求记住某事、给出行为反馈、或提及重要的项目信息时使用。")
    public String saveMemory(
            @P("记忆名称，短横线命名，如 user-role、feedback-testing") String name,
            @P("记忆类型: user, feedback, project, reference") String type,
            @P("一句话描述，用于索引检索") String description,
            @P("记忆正文内容") String content
    ) {
        log.info("🧠 [Memory] 保存记忆: name={}, type={}", name, type);
        try {
            MemoryManager.MemoryType memType;
            try {
                memType = MemoryManager.MemoryType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return "无效的记忆类型: " + type + "，可选值: user, feedback, project, reference";
            }
            String sid = MemoryIdContext.get(); if (sid == null) sid = "unknown";
            MemoryManager.MemoryEntry entry = memoryManager.saveMemory(sid, name, description, memType, content);
            hooks.recordSuccess();
            String msg = entry != null
                    ? "记忆已保存: " + name + " (" + type + ")"
                    : "记忆保存失败";
            return compactToolResult("saveMemory", name + "," + type, msg);
        } catch (Exception e) {
            hooks.recordFailureOnly();
            return "保存记忆失败: " + e.getMessage();
        }
    }

    // ---- 工具 9: 更新记忆 ----

    @Tool("更新一条已有的记忆。当用户说'我之前说的是...'、'改一下我的偏好'、'修正之前的记录'等需要修改已保存记忆时使用。" +
          "如果只改内容，保留原有描述；如果描述也变了，传入新的描述。")
    public String updateMemory(
            @P("要更新的记忆名称") String name,
            @P("新的记忆正文内容") String newContent,
            @P("新的描述（可选，不填则保留原有描述）") String newDescription
    ) {
        log.info("✏️ [Memory] 更新记忆: name={}", name);
        try {
            MemoryManager.MemoryEntry entry;
            String sid = MemoryIdContext.get(); if (sid == null) sid = "unknown";
            if (newDescription != null && !newDescription.trim().isEmpty()) {
                entry = memoryManager.updateMemory(sid, name, newDescription.trim(), newContent);
            } else {
                entry = memoryManager.updateMemory(sid, name, newContent);
            }
            hooks.recordSuccess();
            if (entry == null) {
                return "未找到记忆: " + name + "，请先使用 saveMemory 创建。";
            }
            return compactToolResult("updateMemory", name, "记忆已更新: " + name + " (" + entry.getType() + ")");
        } catch (Exception e) {
            hooks.recordFailureOnly();
            return "更新记忆失败: " + e.getMessage();
        }
    }

    // ---- 工具 10: 删除记忆 ----

    @Tool("删除一条已保存的记忆。当用户明确说'删除...记忆'、'忘掉...'时使用。")
    public String deleteMemory(
            @P("要删除的记忆名称") String name
    ) {
        log.info("🗑️ [Memory] 删除记忆: {}", name);
        try {
            String sid = MemoryIdContext.get(); if (sid == null) sid = "unknown";
            boolean ok = memoryManager.deleteMemory(sid, name);
            hooks.recordSuccess();
            return ok ? "记忆已删除: " + name : "未找到记忆: " + name;
        } catch (Exception e) {
            hooks.recordFailureOnly();
            return "删除记忆失败: " + e.getMessage();
        }
    }

}
