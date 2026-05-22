package com.itheima.agent.service;

import com.aliyun.docmind_api20220711.Client;
import com.aliyun.docmind_api20220711.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AliyunDocParserService {

    @Value("${aliyun.docmind.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.docmind.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.docmind.enabled:true}")
    private boolean enabled;

    @Value("${aliyun.docmind.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${aliyun.docmind.poll-interval-seconds:1}")
    private int pollIntervalSeconds;

    private Client client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws Exception {
        if (enabled && accessKeyId != null && !accessKeyId.isEmpty()) {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "docmind-api.cn-hangzhou.aliyuncs.com";
            client = new Client(config);
            log.info("阿里云文档智能服务初始化成功");
        } else {
            log.warn("阿里云文档智能服务未配置或未启用");
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }

    public DocParseResult parseDocument(Path filePath) {
        if (!isEnabled()) return null;

        try {
            log.info("[阿里云文档解析] 开始解析: {}", filePath.getFileName());

            String jobId = submitJob(filePath);
            if (jobId == null) {
                log.error("提交任务失败");
                return null;
            }
            log.info("[阿里云文档解析] 任务已提交, JobId: {}", jobId);

            DocParseResult result = pollForResult(jobId);
            if (result != null) {
                log.info("[阿里云文档解析] 解析完成, {} 个段落", result.paragraphs.size());
            }
            return result;

        } catch (Exception e) {
            log.error("[阿里云文档解析] 解析失败", e);
            return null;
        }
    }

    /** SubmitDocParserJobAdvance 大模型版文档解析，上传本地文件 */
    private String submitJob(Path filePath) {
        try {
            File file = filePath.toFile();
            log.info("[DocParser] 提交文件: {} ({} bytes)", file.getName(), file.length());
            try (FileInputStream fis = new FileInputStream(file)) {
                SubmitDocParserJobAdvanceRequest req = new SubmitDocParserJobAdvanceRequest();
                req.fileUrlObject = fis;
                req.fileName = file.getName();
                RuntimeOptions runtime = new RuntimeOptions();
                SubmitDocParserJobResponse resp = client.submitDocParserJobAdvance(req, runtime);
                log.info("[DocParser] submit response body: {}", objectMapper.writeValueAsString(resp.getBody()));
                if (resp.getBody() != null && resp.getBody().getData() != null) {
                    String jobId = resp.getBody().getData().getId();
                    log.info("[DocParser] jobId={}", jobId);
                    return jobId;
                }
                log.error("[DocParser] body 或 body.data 为 null");
            }
        } catch (Exception e) {
            log.error("提交文档解析任务失败", e);
        }
        return null;
    }

    /** 轮询 QueryDocParserStatus，超时后仍尝试 getDocParserResult */
    private DocParseResult pollForResult(String jobId) throws InterruptedException {
        int maxRetries = timeoutSeconds / pollIntervalSeconds;
        int pollMs = pollIntervalSeconds * 1000;

        for (int i = 0; i < maxRetries; i++) {
            Thread.sleep(pollMs);
            try {
                QueryDocParserStatusRequest req = new QueryDocParserStatusRequest();
                req.setId(jobId);
                QueryDocParserStatusResponse resp = client.queryDocParserStatus(req);

                if (resp.getBody() != null && resp.getBody().getData() != null) {
                    String status = resp.getBody().getData().getStatus();
                    if (status == null) continue;

                    // 匹配所有成功/处理中/失败状态变体
                    String s = status.trim().toLowerCase();
                    if (s.contains("success") || s.contains("succeeded")) {
                        log.info("任务完成, 耗时 {}s", (i + 1) * pollIntervalSeconds);
                        return getResult(jobId);
                    }
                    if (s.contains("fail")) {
                        log.error("任务处理失败, status={}", status);
                        return null;
                    }
                    // processing / running: 继续等待
                }
            } catch (Exception e) {
                log.warn("查询状态异常 (重试 {}/{}): {}", i + 1, maxRetries, e.getMessage());
            }
        }
        log.warn("轮询超时，尝试最后获取结果");
        return getResult(jobId);
    }

    private DocParseResult getResult(String jobId) {
        try {
            GetDocParserResultRequest req = new GetDocParserResultRequest();
            req.setId(jobId);
            req.setLayoutNum(0);
            req.setLayoutStepSize(200);
            GetDocParserResultResponse resp = client.getDocParserResult(req);
            if (resp.getBody() == null) {
                log.error("[DocParser] getResult body 为 null");
                return null;
            }
            Object data = resp.getBody().getData();
            if (data != null) {
                String json = objectMapper.writeValueAsString(data);
                log.info("[DocParser] result JSON (first 500 chars): {}", json.length() > 500 ? json.substring(0, 500) : json);
                return parseResult(data);
            }
            log.error("[DocParser] getResult data 为 null, body json: {}",
                    objectMapper.writeValueAsString(resp.getBody()));
        } catch (Exception e) {
            log.error("获取结果失败", e);
        }
        return null;
    }

    /** 解析 DocParser 大模型版返回 (layouts 数组) */
    private DocParseResult parseResult(Object data) {
        try {
            String jsonStr = objectMapper.writeValueAsString(data);
            JsonNode root = objectMapper.readTree(jsonStr);
            DocParseResult result = new DocParseResult();
            result.rawJson = jsonStr;

            // 关键: API 返回的 key 是 "layouts" (复数)
            JsonNode layouts = root.path("layouts");
            if (layouts.isArray()) {
                for (JsonNode node : layouts) {
                    Paragraph para = new Paragraph();
                    para.text = node.path("text").asText("");
                    para.type = node.path("type").asText("text");
                    String subType = node.path("subType").asText("");
                    para.pageNum = node.path("pageNum").asInt(0);

                    // 标题判断基于 type 和 subType
                    if ("title".equalsIgnoreCase(para.type) || "doc_title".equalsIgnoreCase(subType)) {
                        para.isTitle = true;
                        para.level = detectTitleLevel(para.text);
                    } else if ("table".equalsIgnoreCase(para.type)) {
                        para.isTable = true;
                    }
                    if (para.text != null && !para.text.trim().isEmpty()) {
                        result.paragraphs.add(para);
                    }
                }
            }
            JsonNode outlineNode = root.path("outline");
            if (outlineNode.isArray()) {
                for (JsonNode node : outlineNode) {
                    OutlineItem item = new OutlineItem();
                    item.uniqueId = node.path("uniqueId").asText("");
                    item.level = node.path("level").asInt(0);
                    result.outline.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("解析结果转换失败", e);
            return null;
        }
    }

    private int detectTitleLevel(String text) {
        if (text == null || text.isEmpty()) return 3;
        if (text.matches("^第[一二三四五六七八九十]+[章节篇部].*")) return 1;
        if (text.matches("^第\\d+[章节篇部].*")) return 1;
        if (text.matches("^\\d+\\.\\d+\\.\\d+.*")) return 3;
        if (text.matches("^\\d+\\.\\d+.*")) return 2;
        if (text.matches("^[一二三四五六七八九十]+[、.．].*")) return 2;
        if (text.matches("^\\d+[、.．].*")) return 2;
        return 3;
    }

    public static class DocParseResult {
        public String rawJson;
        public List<Paragraph> paragraphs = new ArrayList<>();
        public List<OutlineItem> outline = new ArrayList<>();
    }

    public static class Paragraph {
        public String text;
        public String type;
        public int pageNum;
        public boolean isTitle;
        public boolean isTable;
        public int level;
        public String llmResult;
    }

    public static class OutlineItem {
        public String uniqueId;
        public int level;
    }
}
