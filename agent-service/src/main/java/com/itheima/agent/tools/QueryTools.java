package com.itheima.agent.tools;

import com.itheima.agent.feign.RealtimeFeignClient;
import com.itheima.agent.feign.WindfarmFeignClient;
import com.itheima.agent.feign.WindturbineFeignClient;
import com.itheima.agent.service.ToolExecutionHooks;
import com.itheima.agent.service.ToolResultSummarizer;
import com.itheima.consultant.common.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import com.itheima.consultant.entity.RealtimeDO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Query Agent 专用工具集 — 风场/风机数据查询。
 *
 * 数据关系 (详见 healthmonitor.sql):
 *   hm_windfarm_info.windfarm  ←──→  hm_realtime.windfarm  (风场编号)
 *   hm_windfarm_info.name       = 中文名称, e.g. "围场塞罕坝风场"
 *   hm_windfarm_info.windturbine_count = 该风场最大风机编号 (风机编号 1~N)
 *   hm_realtime.windturbine     = 风机编号, 1~windturbine_count
 *   hm_realtime.status          = 0正常/1故障/9未连接
 *
 * 用户可能输入中文风场名 → 先用 lookupWindfarm 模糊匹配查询编号 → 再查询数据。
 */
@Slf4j
@Component("queryTools")
public class QueryTools {

    @Autowired private RealtimeFeignClient realtimeFeignClient;
    @Autowired private WindturbineFeignClient windturbineFeignClient;
    @Autowired private WindfarmFeignClient windfarmFeignClient;
    @Autowired private ToolExecutionHooks hooks;
    @Autowired private ToolResultSummarizer summarizer;

    @Value("${rag.turbine.default-limit:50}")
    private int defaultLimit;

    /** 风场信息本地缓存: windfarm编号 → {name, turbineCount, ...} */
    private final Map<String, Map<String, Object>> windfarmCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadWindfarmCache() {
        try {
            Result<List<Map<String, Object>>> res = windfarmFeignClient.listAllWindfarms();
            if (res != null && Result.Status.SUCCESS.getCode().equals(res.getStatus()) && res.getData() != null) {
                for (Map<String, Object> f : res.getData()) {
                    String code = (String) f.get("windfarm");
                    if (code != null) windfarmCache.put(code, f);
                }
                log.info("[QueryTools] 风场信息缓存加载完成, {} 个风场", windfarmCache.size());
            }
        } catch (Exception e) {
            log.warn("[QueryTools] 风场缓存加载失败: {}", e.getMessage());
        }
    }

    // ---- 工具0: 风场名称→编号查询 (应优先调用) ----

    @Tool("根据风场中文名称模糊查询对应的风场编号和基本信息。" +
          "用户输入'围场'、'塞罕坝'、'黑龙江'等中文名称时使用此工具查询编号。" +
          "返回: 风场编号(windfarm)、中文名(name)、最大风机数(windturbine_count)、省份(province)。" +
          "查到的编号可直接用于 queryRealtimeData 和 queryAllWindturbineStatus。")
    public String lookupWindfarm(
            @P("风场中文名称或关键词，如'围场'、'黑龙江'、'广东'、'塞罕坝'等") String keyword) {
        log.info("🔍 [Query] 风场名称查询: keyword={}", keyword);
        String blocked = hooks.checkBlocked("风场查询");
        if (blocked != null) return blocked;
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                hooks.recordFailureOnly();
                return "请提供风场名称关键词。";
            }
            if (windfarmCache.isEmpty()) {
                loadWindfarmCache();
                if (windfarmCache.isEmpty()) {
                    hooks.recordFailureOnly();
                    return "风场信息暂不可用，请稍后重试或直接使用风场编号查询（如'10001'）。";
                }
            }

            String kw = keyword.trim().toLowerCase();
            List<Map<String, Object>> matches = new ArrayList<>();

            for (Map<String, Object> f : windfarmCache.values()) {
                String name = ((String) f.getOrDefault("name", "")).toLowerCase();
                String code = ((String) f.getOrDefault("windfarm", "")).toLowerCase();
                String province = ((String) f.getOrDefault("province", "")).toLowerCase();
                // 模糊匹配: 名称、编号、省份任一包含关键词
                if (name.contains(kw) || code.contains(kw) || province.contains(kw)) {
                    matches.add(f);
                }
            }

            if (matches.isEmpty()) {
                hooks.recordSuccess();
                return "未找到匹配 '" + keyword + "' 的风场。已知风场: " +
                    windfarmCache.values().stream()
                        .map(f -> f.get("name") + "(" + f.get("windfarm") + ")")
                        .collect(Collectors.joining(", "));
            }

            hooks.recordSuccess();
            StringBuilder sb = new StringBuilder("找到 " + matches.size() + " 个匹配风场:\n\n");
            sb.append("| 编号 | 名称 | 最大风机数 | 省份 |\n");
            sb.append("|------|------|-----------|------|\n");
            for (Map<String, Object> f : matches) {
                sb.append(String.format("| %s | %s | %s | %s |\n",
                    f.get("windfarm"), f.get("name"), f.get("windturbineCount"), f.get("province")));
            }
            sb.append("\n提示: 使用'编号'列的值作为 queryRealtimeData 或 queryAllWindturbineStatus 的 windfarm 参数。");
            return sb.toString();
        } catch (Exception e) {
            return hooks.recordFailure("风场查询", e);
        }
    }

    // ---- 工具1: 多条件实时数据查询 ----

    @Tool("查询风机实时监测数据 (hm_realtime表)。" +
          "数据关系: hm_realtime.windfarm 对应 hm_windfarm_info.windfarm(编号); " +
          "hm_realtime.windturbine 为风机编号(1~对应风场的windturbine_count); " +
          "hm_realtime.status: 0=正常 1=故障 9=未连接。 " +
          "重要: 如果用户输入的是中文风场名称，请先用 lookupWindfarm 查询对应编号，再调用此工具。")
    public String queryRealtimeData(
            @P("风场编号，字符串如'10001'。如用户输入中文名，先用 lookupWindfarm 查编号") String windfarm,
            @P(value = "风机编号，整数如1、2。有效范围:1到该风场的windturbine_count", required = false) String windturbine,
            @P(value = "状态码过滤: 0正常/1故障/9未连接", required = false) String status,
            @P(value = "开始时间 yyyy-MM-dd HH:mm:ss", required = false) String startTime,
            @P(value = "结束时间 yyyy-MM-dd HH:mm:ss", required = false) String endTime,
            @P(value = "返回条数上限，默认50", required = false) String limit) {
        log.info("📊 [Query] 实时数据: windfarm={}, turbine={}, status={}", windfarm, windturbine, status);
        String blocked = hooks.checkBlocked("数据查询");
        if (blocked != null) return blocked;
        try {
            // 参数校验
            if (windfarm == null || windfarm.trim().isEmpty()) {
                hooks.recordFailureOnly();
                return "请提供风场编号。如不确定编号，请先调用 lookupWindfarm 工具查询。";
            }
            Integer limitVal = parseOrNull(limit);
            if (limitVal == null || limitVal <= 0) limitVal = defaultLimit;
            Integer wtVal = parseOrNull(windturbine);
            Integer stVal = parseOrNull(status);

            // 风机编号范围校验
            Map<String, Object> farmInfo = windfarmCache.get(windfarm.trim());
            if (farmInfo != null && wtVal != null) {
                Integer maxTurbine = (Integer) farmInfo.get("windturbineCount");
                if (maxTurbine != null && (wtVal < 1 || wtVal > maxTurbine)) {
                    hooks.recordFailureOnly();
                    return String.format("风机编号 %d 无效。风场[%s]的风机编号范围为 1~%d。",
                        wtVal, farmInfo.get("name"), maxTurbine);
                }
            }

            Result<List<RealtimeDO>> result = realtimeFeignClient.queryByConditions(
                windfarm.trim(), wtVal, stVal, startTime, endTime, limitVal);
            if (result == null || !Result.Status.SUCCESS.getCode().equals(result.getStatus())) {
                hooks.recordSuccess();
                return "查询失败：" + (result != null ? result.getMessage() : "服务无响应");
            }
            List<RealtimeDO> records = result.getData();
            if (records == null || records.isEmpty()) {
                hooks.recordSuccess();
                return buildEmptyMsg(windfarm, wtVal, stVal, startTime, endTime);
            }
            hooks.recordSuccess();
            String text = formatTable(records);
            return summarizer.process("实时数据查询", text);
        } catch (Exception e) {
            return hooks.recordFailure("数据查询", e);
        }
    }

    // ---- 工具2: 风场全部风机状态 ----

    @Tool("查询风场所有风机的当前健康状态 (hm_windturbine_info + hm_windfarm_info)。" +
          "返回每台风机的状态: 0=正常 1=故障 9=未连接，以及统计汇总。" +
          "重要: 如果用户输入的是中文风场名称，请先用 lookupWindfarm 查询对应编号。")
    public String queryAllWindturbineStatus(
            @P("风场编号，如'10001'。如用户输入中文名，先用 lookupWindfarm 查编号") String windfarm) {
        log.info("📊 [Query] 风机状态: windfarm={}", windfarm);
        String blocked = hooks.checkBlocked("状态查询");
        if (blocked != null) return blocked;
        try {
            if (windfarm == null || windfarm.trim().isEmpty()) {
                hooks.recordFailureOnly();
                return "请提供风场编号。如不确定编号，请先调用 lookupWindfarm 工具查询。";
            }
            String wf = windfarm.trim();
            Result<Map<String, Integer>> result = windturbineFeignClient.queryAllWindturbineStatus(wf);
            if (result == null || !Result.Status.SUCCESS.getCode().equals(result.getStatus())) {
                hooks.recordSuccess();
                return "查询失败：" + (result != null ? result.getMessage() : "服务无响应");
            }
            Map<String, Integer> statusMap = result.getData();
            if (statusMap == null || statusMap.isEmpty()) {
                hooks.recordSuccess();
                return "风场 [" + wf + "] 未找到任何风机数据。";
            }

            // 附加风场基本信息
            Map<String, Object> farmInfo = windfarmCache.get(wf);
            String farmName = farmInfo != null ? (String) farmInfo.get("name") : wf;

            int normal = 0, fault = 0, offline = 0;
            StringBuilder sb = new StringBuilder("风场 [").append(farmName).append("] (").append(wf).append(") 共 ")
                .append(statusMap.size()).append(" 台风机：\n\n");
            sb.append("| 风机编号 | 状态 | 说明 |\n|----------|------|------|\n");
            for (Map.Entry<String, Integer> e : statusMap.entrySet()) {
                String desc = switch (e.getValue()) {
                    case 0 -> "正常"; case 1 -> "故障"; case 9 -> "未连接"; default -> "未知(" + e.getValue() + ")";
                };
                sb.append(String.format("| %s | %d | %s |\n", e.getKey(), e.getValue(), desc));
                switch (e.getValue()) { case 0 -> normal++; case 1 -> fault++; case 9 -> offline++; }
            }
            sb.append("\n统计: 总计").append(statusMap.size()).append(" 台 | 正常:").append(normal)
              .append(" | 故障:").append(fault).append(" | 未连接:").append(offline);

            if (farmInfo != null) {
                sb.append("\n风场信息: ").append(farmInfo.get("name"))
                  .append(", 最大风机数:").append(farmInfo.get("windturbineCount"))
                  .append(", 省份:").append(farmInfo.get("province"));
            }
            hooks.recordSuccess();
            String text = sb.toString();
            return summarizer.process("风机状态查询", text);
        } catch (Exception e) {
            return hooks.recordFailure("状态查询", e);
        }
    }

    // ---- 工具3: 风场列表 ----

    @Tool("列出所有风场的基本信息，包括编号、中文名称、风机数量、省份。")
    public String listAllWindfarms() {
        String blocked = hooks.checkBlocked("风场列表");
        if (blocked != null) return blocked;
        try {
            if (windfarmCache.isEmpty()) loadWindfarmCache();
            if (windfarmCache.isEmpty()) {
                hooks.recordFailureOnly();
                return "风场信息暂不可用。";
            }
            hooks.recordSuccess();
            StringBuilder sb = new StringBuilder("共 " + windfarmCache.size() + " 个风场:\n\n");
            sb.append("| 编号 | 名称 | 风机数 | 省份 |\n|------|------|--------|------|\n");
            for (Map<String, Object> f : windfarmCache.values()) {
                sb.append(String.format("| %s | %s | %s | %s |\n",
                    f.get("windfarm"), f.get("name"), f.get("windturbineCount"), f.get("province")));
            }
            return sb.toString();
        } catch (Exception e) {
            return hooks.recordFailure("风场列表", e);
        }
    }

    // ---- helpers ----

    private Integer parseOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private String formatTable(List<RealtimeDO> records) {
        StringBuilder sb = new StringBuilder("查询到 ").append(records.size()).append(" 条记录：\n\n");
        sb.append("| 接收时间 | 风场 | 风机 | 状态 | 特征1 | 特征2 | 特征3 |\n");
        sb.append("|----------|------|------|------|-------|-------|-------|\n");
        for (RealtimeDO d : records) {
            String statusDesc = d.getStatus() != null ? switch (d.getStatus()) {
                case 0 -> "正常"; case 1 -> "故障"; case 9 -> "未连接"; default -> d.getStatus().toString();
            } : "-";
            sb.append(String.format("| %s | %s | %d | %s | %.2f | %.2f | %.2f |\n",
                d.getGmtReceived() != null ? d.getGmtReceived().toString() : "-",
                d.getWindfarm() != null ? d.getWindfarm() : "-",
                d.getWindturbine() != null ? d.getWindturbine() : 0,
                statusDesc,
                d.getFeature1() != null ? d.getFeature1() : 0,
                d.getFeature2() != null ? d.getFeature2() : 0,
                d.getFeature3() != null ? d.getFeature3() : 0));
        }
        return sb.toString();
    }

    private String buildEmptyMsg(String wf, Integer wt, Integer st, String start, String end) {
        StringBuilder sb = new StringBuilder("未查询到符合条件的数据。查询条件: windfarm=").append(wf);
        if (wt != null) sb.append(", windturbine=").append(wt);
        if (st != null) sb.append(", status=").append(switch(st) {
            case 0 -> "正常"; case 1 -> "故障"; case 9 -> "未连接"; default -> st.toString();
        });
        if (start != null) sb.append(", start=").append(start);
        if (end != null) sb.append(", end=").append(end);
        Map<String, Object> farm = windfarmCache.get(wf);
        if (farm != null) {
            sb.append("\n提示: 风场[").append(farm.get("name")).append("] 共 ")
              .append(farm.get("windturbineCount")).append(" 台风机。");
        }
        return sb.toString();
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
            var allLines = Files.readAllLines(path);
            int total = allLines.size(), s = 1, e = total;
            try { if (startLine != null && !startLine.isEmpty()) s = Integer.parseInt(startLine); } catch (NumberFormatException ignored) {}
            try { if (endLine != null && !endLine.isEmpty()) e = Integer.parseInt(endLine); } catch (NumberFormatException ignored) {}
            s = Math.max(1, Math.min(s, total));
            e = Math.max(s, Math.min(e, total));
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件: %s | 总行数: %d | 读取: %d-%d 行\n\n", filePath, total, s, e));
            for (int i = s - 1; i < e; i++) sb.append(String.format("%6d| %s\n", i + 1, allLines.get(i)));
            hooks.recordSuccess();
            return sb.toString();
        } catch (Exception e2) {
            return hooks.recordFailure("文件读取", e2);
        }
    }

}
