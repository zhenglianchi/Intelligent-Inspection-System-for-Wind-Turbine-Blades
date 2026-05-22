package com.itheima.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 基于阿里云 DashScope Tokenizer API 的精确 Token 计数器
 *
 * API: POST https://dashscope.aliyuncs.com/api/v1/tokenizer
 * 文档: https://help.aliyun.com/zh/model-studio/getting-started/tokenizer
 *
 * 比估算方式 (chars/2.5) 精确, 单次 API 调用可批量计算多条消息的 token。
 */
@Slf4j
@Service
public class TokenCounter {

    private static final String TOKENIZER_URL = "https://dashscope.aliyuncs.com/api/v1/tokenizer";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:qwen-plus}")
    private String modelName;

    public TokenCounter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 精确计算消息列表的 token 数 (调用 DashScope Tokenizer API)
     */
    public int countTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        try {
            List<Map<String, Object>> apiMessages = new ArrayList<>();
            for (ChatMessage msg : messages) {
                String content = extractText(msg);
                if (content == null || content.isBlank()) continue; // 跳过空消息
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", toApiRole(msg));
                m.put("content", content);
                apiMessages.add(m);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("messages", apiMessages);
            body.put("input", input);

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENIZER_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                Object usage = result.get("usage");
                if (usage instanceof Map) {
                    Object totalTokens = ((Map<?, ?>) usage).get("total_tokens");
                    if (totalTokens instanceof Number) {
                        int tokens = ((Number) totalTokens).intValue();
                        log.debug("[TokenCounter] {} 条消息 = {} tokens (API 精确)", messages.size(), tokens);
                        return tokens;
                    }
                }
            } else {
                log.warn("[TokenCounter] API 返回非 200: {} body={}", response.statusCode(),
                        response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
            }

        } catch (Exception e) {
            log.warn("[TokenCounter] API 调用失败, 回退到估算: {}", e.getMessage());
        }

        // 回退到估算
        return estimateTokens(messages);
    }

    /**
     * 精确计算单段文本的 token 数
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return countTokens(List.of(UserMessage.from(text)));
    }

    /**
     * 回退估算: 中文 ~1.5 char/token, 混合文本 ~2.5 char/token
     */
    public int estimateTokens(List<ChatMessage> messages) {
        int totalChars = 0;
        for (ChatMessage msg : messages) {
            totalChars += extractText(msg).length();
        }
        return (int) (totalChars / 2.5) + messages.size() * 4;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) (text.length() / 2.5) + 4;
    }

    private String toApiRole(ChatMessage msg) {
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        if (msg instanceof SystemMessage) return "system";
        // DashScope Tokenizer 不支持 "tool" role → 转为 "user"
        if (msg instanceof ToolExecutionResultMessage) return "user";
        return "user";
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof UserMessage um) {
            return um.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .reduce("", (a, b) -> a + b);
        }
        if (msg instanceof AiMessage am) {
            String text = am.text();
            return text != null ? text : "";
        }
        if (msg instanceof ToolExecutionResultMessage tm) {
            return tm.text();
        }
        if (msg instanceof SystemMessage sm) {
            return sm.text();
        }
        return msg.toString();
    }
}
