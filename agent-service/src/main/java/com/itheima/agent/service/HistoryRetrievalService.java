package com.itheima.agent.service;

import com.hankcs.hanlp.HanLP;
import com.itheima.agent.repository.RedisChatMemoryProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 历史上下文智能召回服务
 * 根据用户当前问题，从完整对话历史中检索相关内容，召回命中对话及其上下两段对话
 * 采用关键字匹配策略，保持对话上下文的连贯性
 */
@Slf4j
@Component
public class HistoryRetrievalService {

    @Autowired
    private RedisChatMemoryProvider chatMemoryProvider;

    /**
     * 是否启用历史上下文召回
     */
    @Value("${rag.memory.retrieval.enabled:true}")
    private boolean enabled;

    /**
     * 最终返回的最大对话轮数（每轮包含用户+AI各一条）
     */
    @Value("${rag.memory.retrieval.max-rounds:5}")
    private int maxRounds;

    /**
     * 关键词匹配最小命中数，低于此分数不召回
     */
    @Value("${rag.memory.retrieval.min-score:0.1}")
    private double minScore;

    /**
     * 是否召回命中消息的上下各两段对话
     * 开启后可以保留更好的对话上下文连贯性
     */
    @Value("${rag.memory.retrieval.expand-context:true}")
    private boolean expandContext;

    /**
     * 上下扩展的对话数（前后各N段）
     */
    @Value("${rag.memory.retrieval.expand-radius:2}")
    private int expandRadius;

    /**
     * 正则表达式：匹配英文/数字组合（保护故障代码不被分词拆分）
     */
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[a-zA-Z0-9]+(?:[-_][a-zA-Z0-9]+)*");

    /**
     * 根据用户查询从历史对话中召回相关上下文
     * @param memoryId 对话会话ID
     * @param query 用户当前查询
     * @return 召回的相关对话历史，格式化后的字符串
     */
    public String retrieveRelevantHistory(String memoryId, String query) {
        if (!enabled) {
            return "";
        }

        // 获取完整对话历史
        List<ChatMessage> fullHistory = chatMemoryProvider.getFullHistory(memoryId);
        if (fullHistory == null || fullHistory.size() <= 2) {
            // 历史太短，不需要召回
            return "";
        }

        log.info("🔍 [历史召回] 开始检索相关历史 | 总消息数: {} | 查询: {}", fullHistory.size(), query);

        // 对查询进行分词，提取关键词
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            log.info("⚠️ [历史召回] 未提取到有效关键词，跳过召回");
            return "";
        }

        // 对每条消息计算匹配分数
        List<ScoredMessage> scoredMessages = scoreMessages(fullHistory, keywords);

        // 过滤掉分数低于阈值的消息
        scoredMessages = scoredMessages.stream()
                .filter(sm -> sm.score >= minScore)
                .sorted(Comparator.comparingDouble(ScoredMessage::getScore).reversed())
                .collect(Collectors.toList());

        if (scoredMessages.isEmpty()) {
            log.info("📭 [历史召回] 未找到匹配度高于阈值的历史消息");
            return "";
        }

        // 收集需要召回的消息索引
        Set<Integer> targetIndices = collectTargetIndices(scoredMessages, fullHistory.size());

        // 根据索引获取最终召回的消息，并按原始顺序排序
        List<ChatMessage> retrievedMessages = targetIndices.stream()
                .sorted()
                .map(fullHistory::get)
                .collect(Collectors.toList());

        // 格式化输出
        String formattedResult = formatRetrievedHistory(retrievedMessages);

        log.info("✅ [历史召回] 完成检索 | 关键词: {} | 命中消息数: {} | 最终召回: {} 条",
                keywords.size(), scoredMessages.size(), retrievedMessages.size());

        return formattedResult;
    }

    /**
     * 提取查询中的关键词
     * 复用混合检索中的分词策略，保护英文/数字组合不被拆分
     */
    private List<String> extractKeywords(String query) {
        if (StringUtils.isBlank(query)) {
            return Collections.emptyList();
        }

        Set<String> keywords = new LinkedHashSet<>();

        // 1. 提取所有连续的英文/数字串（保护故障代码如E-204不被拆分）
        Matcher matcher = ALPHANUMERIC_PATTERN.matcher(query);
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() >= 2 || term.matches("[A-Z]")) {
                keywords.add(term);
                keywords.add(term.toLowerCase());
            }
        }

        // 2. 中文分词
        try {
            List<String> hanlpTerms = HanLP.segment(query).stream()
                    .map(term -> term.word.trim())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());

            for (String term : hanlpTerms) {
                if (isStopWord(term)) {
                    continue;
                }
                keywords.add(term);
            }
        } catch (NoClassDefFoundError e) {
            // 降级：按空格/标点分割
            String cleaned = query.replaceAll("[，。！？、；：\"'（）()\\[\\]{}<>\\s]+", " ");
            Arrays.stream(cleaned.split("\\s+"))
                    .filter(StringUtils::isNotBlank)
                    .forEach(keywords::add);
        }

        // 过滤掉长度为1的中文停用词
        List<String> result = keywords.stream()
                .filter(k -> k.length() > 1 || k.matches("[a-zA-Z0-9]"))
                .collect(Collectors.toList());

        log.debug("🔍 [历史召回分词] 原始: {} | 关键词: {}", query, result);
        return result;
    }

    /**
     * 给每条消息打分，计算关键词匹配程度
     * 分数 = 匹配关键词数量 / 总关键词数量
     */
    private List<ScoredMessage> scoreMessages(List<ChatMessage> history, List<String> keywords) {
        List<ScoredMessage> result = new ArrayList<>();

        for (int i = 0; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            String content = getMessageText(message);
            if (StringUtils.isBlank(content)) {
                continue;
            }

            // 跳过系统消息
            if (message instanceof SystemMessage) {
                continue;
            }

            // 计算匹配的关键词数量
            int matchCount = 0;
            String lowerContent = content.toLowerCase();
            for (String keyword : keywords) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    matchCount++;
                }
            }

            // 计算分数
            double score = (double) matchCount / keywords.size();
            if (score > 0) {
                result.add(new ScoredMessage(i, message, score));
            }
        }

        return result;
    }

    /**
     *收集目标索引，如果开启了上下文扩展，则同时收集命中消息前后各N条
     */
    private Set<Integer> collectTargetIndices(List<ScoredMessage> scoredMessages, int totalSize) {
        Set<Integer> indices = new LinkedHashSet<>();

        // 先取分数最高的前N个命中
        int takeCount = Math.min(scoredMessages.size(), maxRounds * 2);
        List<ScoredMessage> topHits = scoredMessages.subList(0, takeCount);

        for (ScoredMessage hit : topHits) {
            int hitIndex = hit.index;

            if (expandContext) {
                // 扩展前后各expandRadius条消息
                int start = Math.max(0, hitIndex - expandRadius);
                int end = Math.min(totalSize - 1, hitIndex + expandRadius);

                for (int i = start; i <= end; i++) {
                    indices.add(i);
                }
            } else {
                // 不扩展，只添加命中消息本身
                indices.add(hitIndex);
            }
        }

        // 如果数量过多，裁剪
        int maxTotal = maxRounds * 2; // 每轮两条消息（user + ai）
        if (indices.size() > maxTotal) {
            // 保留最早的消息（保持对话顺序）
            List<Integer> sorted = new ArrayList<>(indices);
            Collections.sort(sorted);
            indices = new LinkedHashSet<>(sorted.subList(0, maxTotal));
        }

        return indices;
    }

    /**
     * 格式化召回的历史对话
     */
    private String formatRetrievedHistory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 相关历史对话 ---\n");

        int round = 1;
        for (ChatMessage message : messages) {
            String type;
            if (message instanceof UserMessage) {
                type = "用户";
            } else if (message instanceof AiMessage) {
                type = "助手";
            } else {
                continue; // 跳过系统消息
            }
            String text = getMessageText(message);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            sb.append("[").append(type).append("] ").append(text).append("\n");
            round++;
        }

        sb.append("--- 历史对话结束 ---\n\n");
        return sb.toString();
    }

    /**
     * 获取消息文本内容
     */
    private String getMessageText(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) message;
            return userMsg.contents().stream()
                    .filter(c -> c instanceof dev.langchain4j.data.message.TextContent)
                    .map(c -> ((dev.langchain4j.data.message.TextContent) c).text())
                    .collect(Collectors.joining("\n"));
        } else if (message instanceof SystemMessage) {
            return ((SystemMessage) message).text();
        }
        return message.toString();
    }

    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String term) {
        if (term.length() == 1 && !term.matches("[a-zA-Z0-9]")) {
            return true;
        }
        return Arrays.asList("的", "了", "是", "在", "就", "都", "而", "及", "与", "着",
                "吗", "呢", "啊", "哦", "这个", "那个", "请问", "谢谢", "你好", "请问").contains(term);
    }

    /**
     * 带分数的消息内部类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ScoredMessage {
        private final int index;
        private final ChatMessage message;
        private final double score;
    }
}
