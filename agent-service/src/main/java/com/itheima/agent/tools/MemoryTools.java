package com.itheima.agent.tools;

import com.itheima.agent.pojo.MemoryIdContext;
import com.itheima.agent.service.MemoryManager;
import com.itheima.agent.service.ToolExecutionHooks;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 记忆管理工具 — 写操作 (save/update/delete)。
 * 读取由 ChatMemoryProvider 自动注入上下文，无需工具。
 */
@Slf4j
@Component("memoryTools")
public class MemoryTools {

    @Autowired private MemoryManager memoryManager;
    @Autowired private ToolExecutionHooks hooks;

    @Tool("保存持久化记忆。用户表达偏好/习惯/身份/项目背景时主动使用。" +
          "类型: user=画像, feedback=行为偏好, project=项目上下文, reference=外部引用。" +
          "命名: 英文短横线如 user-role、prefer-concise。")
    public String saveMemory(
            @P("记忆名称，英文短横线") String name,
            @P("类型: user/feedback/project/reference") String type,
            @P("一句话描述") String description,
            @P("正文内容") String content) {
        String blocked = hooks.checkBlocked("保存记忆");
        if (blocked != null) return blocked;
        try {
            String sid = MemoryIdContext.get();
            if (sid == null) sid = "unknown";
            MemoryManager.MemoryType mt = MemoryManager.MemoryType.valueOf(type.toUpperCase());
            MemoryManager.MemoryEntry entry = memoryManager.saveMemory(sid, name, description, mt, content);
            hooks.recordSuccess();
            return entry != null ? "已保存: " + name + " (" + type + ")" : "保存失败";
        } catch (IllegalArgumentException e) {
            hooks.recordFailureOnly();
            return "无效类型: " + type + "，可选: user, feedback, project, reference";
        } catch (Exception e) {
            return hooks.recordFailure("保存记忆", e);
        }
    }

    @Tool("更新已有记忆。用户纠正或补充信息时使用。")
    public String updateMemory(
            @P("记忆名称") String name,
            @P("新正文") String newContent,
            @P(value = "新描述（可选）", required = false) String newDescription) {
        String blocked = hooks.checkBlocked("更新记忆");
        if (blocked != null) return blocked;
        try {
            String sid = MemoryIdContext.get();
            if (sid == null) sid = "unknown";
            MemoryManager.MemoryEntry entry;
            if (newDescription != null && !newDescription.trim().isEmpty())
                entry = memoryManager.updateMemory(sid, name, newDescription.trim(), newContent);
            else entry = memoryManager.updateMemory(sid, name, newContent);
            hooks.recordSuccess();
            return entry != null ? "已更新: " + name : "未找到: " + name;
        } catch (Exception e) { return hooks.recordFailure("更新记忆", e); }
    }

    @Tool("删除记忆。用户说'忘掉...'时使用。")
    public String deleteMemory(@P("记忆名称") String name) {
        String blocked = hooks.checkBlocked("删除记忆");
        if (blocked != null) return blocked;
        try {
            hooks.recordSuccess();
            String sid = MemoryIdContext.get();
            if (sid == null) sid = "unknown";
            return memoryManager.deleteMemory(sid, name) ? "已删除: " + name : "未找到: " + name;
        } catch (Exception e) { return hooks.recordFailure("删除记忆", e); }
    }
}
