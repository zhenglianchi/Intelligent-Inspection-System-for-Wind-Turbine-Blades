package com.itheima.agent.splitter;

import com.itheima.agent.service.AliyunDocParserService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 父-子文档分割器 (Parent-Child Splitter)
 *
 * 两级分割:
 *   Parent (AliyunSmartSplitter, 2000字符) → 存入 Redis doc:parent:{md5}, 不过期
 *   Child  (滑动窗口, ~600字符, 100重叠)   → 存入向量索引，带 parent_id 元数据
 *
 * 检索: 向量命中 child → metadata.parent_id → Redis doc:parent:{id} → 返回父块全文
 *
 * 父块生命周期: 仅在知识库重建时通过 KnowledgeRebuildConsumer 清理 doc:parent:* keys。
 */
@Slf4j
@Component
public class ParentChildSplitter implements DocumentSplitter {

    private static final String REDIS_PARENT_KEY_PREFIX = "doc:parent:";
    private static final int MAX_CHILDREN_PER_PARENT = 30;

    private final DocumentSplitter parentSplitter;
    private final StringRedisTemplate redisTemplate;
    private final int childSize;
    private final int childOverlap;

    @Autowired
    public ParentChildSplitter(
            AliyunDocParserService docParserService,
            StringRedisTemplate redisTemplate,
            @Value("${rag.splitter.parent-child.child-size:600}")
            int childSize,
            @Value("${rag.splitter.parent-child.child-overlap:100}")
            int childOverlap) {
        this.parentSplitter = new AliyunSmartSplitter(docParserService, 2000, 0, true);
        this.redisTemplate = redisTemplate;
        this.childSize = Math.max(1, childSize);
        this.childOverlap = Math.max(0, childOverlap);
        log.info("[父-子分割器] childSize={}, childOverlap={}", this.childSize, this.childOverlap);
    }

    @Override
    public List<TextSegment> split(Document document) {
        log.info("[父-子分割] source={}", document.metadata().getString("source"));

        List<TextSegment> parents = parentSplitter.split(document);
        log.info("[父-子分割] {} 个父块", parents.size());
        if (parents.isEmpty()) return new ArrayList<>();

        List<TextSegment> allChildren = new ArrayList<>();
        for (TextSegment parent : parents) {
            if (parent.text() == null || parent.text().trim().isEmpty()) continue;

            String parentId = DigestUtils.md5DigestAsHex(
                    parent.text().getBytes(StandardCharsets.UTF_8));

            // 父块存入 Redis — 不过期，仅重建时清理
            redisTemplate.opsForValue().set(REDIS_PARENT_KEY_PREFIX + parentId, parent.text());

            List<TextSegment> children = splitToChildren(parent, parentId);
            allChildren.addAll(children);
        }

        log.info("[父-子分割] 完成: {} 子块 (来自 {} 父块)", allChildren.size(), parents.size());
        return allChildren;
    }

    private List<TextSegment> splitToChildren(TextSegment parent, String parentId) {
        List<TextSegment> children = new ArrayList<>();
        String text = parent.text();
        int textLength = text.length();
        if (textLength == 0) return children;

        int start = 0, idx = 0;
        while (start < textLength && idx < MAX_CHILDREN_PER_PARENT) {
            int end = Math.min(start + childSize, textLength);
            if (end < textLength) {
                int boundary = findSentenceBoundary(text, start, end);
                if (boundary > start + childSize / 2) end = boundary + 1;
            }
            if (end <= start) end = Math.min(start + childSize, textLength);

            String childText = text.substring(start, end).trim();
            if (!childText.isEmpty()) {
                Metadata md = new Metadata(parent.metadata().toMap());
                md.put("parent_id", parentId);
                md.put("child_index", idx++);
                children.add(TextSegment.from(childText, md));
            }
            if (end >= textLength) break;
            start = Math.max(end - childOverlap, start + 1);
        }
        return children;
    }

    private int findSentenceBoundary(String text, int start, int end) {
        return Math.max(text.lastIndexOf("。", end),
               Math.max(text.lastIndexOf("\n", end),
                        text.lastIndexOf(". ", end)));
    }
}
