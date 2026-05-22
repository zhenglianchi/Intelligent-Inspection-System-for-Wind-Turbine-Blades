package com.itheima.agent.service;

import com.itheima.agent.metrics.RagMetrics;
import com.itheima.agent.metrics.SessionMetricsTracker;
import com.itheima.agent.pojo.MemoryIdContext;
import com.itheima.agent.retriever.HybridRerankRetriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EnhancedRAGService {

    @Autowired private QueryRewriteService queryRewriteService;
    @Autowired private HyDEService hydeService;
    @Autowired private HybridRerankRetriever hybridRerankRetriever;
    @Autowired private DegradationService degradationService;
    @Autowired private RagResultCacheService ragCache;
    @Autowired private EmbeddingModel embeddingModel;
    @Autowired private RagMetrics ragMetrics;
    @Autowired private SessionMetricsTracker sessionMetrics;

    @Value("${rag.enhanced.enabled:true}") private boolean enabled;
    @Value("${rag.enhanced.use-query-rewrite:true}") private boolean useQueryRewrite;
    @Value("${rag.enhanced.use-hyde:true}") private boolean useHyde;
    @Value("${rag.enhanced.merge-strategy:UNION}") private String mergeStrategy;

    public List<Content> retrieve(String originalQuery) {
        log.info("[增强RAG] 检索: {}", originalQuery);
        if (!degradationService.isRagAvailable()) {
            log.warn("[降级] RAG 已禁用");
            return Collections.emptyList();
        }

        // 缓存检查 (带计时)
        Timer.Sample cacheSample = Timer.start();
        if (ragCache.isEnabled()) {
            try {
                String queryMd5 = ragCache.md5(originalQuery);
                float[] queryEmbedding = embeddingModel.embed(originalQuery).content().vector();
                String cached = ragCache.findSimilarQuery(queryEmbedding, queryMd5);
                if (cached != null) {
                    ragMetrics.recordCacheCheck(cacheSample);
                    log.info("[增强RAG] 缓存命中");
                    return List.of(Content.from(cached));
                }
            } catch (Exception e) { log.debug("缓存查询失败: {}", e.getMessage()); }
        }
        ragMetrics.recordCacheCheck(cacheSample);

        if (!enabled) return hybridRerankRetriever.retrieve(new Query(originalQuery));

        List<String> searchQueries = new ArrayList<>();
        searchQueries.add(originalQuery);

        if (useQueryRewrite) {
            QueryRewriteService.RewriteResult rewriteResult = queryRewriteService.process(originalQuery);
            if (rewriteResult.expandedQueries() != null) searchQueries.addAll(rewriteResult.expandedQueries());
        }
        if (useHyde) {
            HyDEService.HyDEResult hydeResult = hydeService.process(originalQuery);
            if (hydeResult.hasHypotheticalDoc()) searchQueries.addAll(hydeResult.hypotheticalDocuments());
        }

        searchQueries = searchQueries.stream().distinct().toList();

        // 向量检索 (含 embedding + vector search + BM25 + rerank)
        Timer.Sample retrieveSample = Timer.start();
        List<Content> allContents = new ArrayList<>();
        Map<String, Content> contentMap = new LinkedHashMap<>();
        for (String query : searchQueries) {
            try {
                List<Content> contents = hybridRerankRetriever.retrieve(new Query(query));
                switch (mergeStrategy.toUpperCase()) {
                    case "UNION" -> contents.forEach(c -> contentMap.putIfAbsent(c.textSegment().text(), c));
                    case "INTERSECTION" -> {
                        if (allContents.isEmpty()) contents.forEach(c -> contentMap.put(c.textSegment().text(), c));
                        else {
                            Set<String> keys = contents.stream().map(c -> c.textSegment().text()).collect(Collectors.toSet());
                            contentMap.keySet().retainAll(keys);
                        }
                    }
                    case "FIRST_ONLY" -> { if (allContents.isEmpty()) contents.forEach(c -> contentMap.put(c.textSegment().text(), c)); }
                    default -> contents.forEach(c -> contentMap.put(c.textSegment().text(), c));
                }
            } catch (Exception e) { log.error("[增强RAG] '{}' 检索失败", query, e); }
        }
        allContents = new ArrayList<>(contentMap.values());
        ragMetrics.recordTotalRetrieve(retrieveSample);

        // 写入缓存
        if (ragCache.isEnabled() && !allContents.isEmpty()) {
            try {
                String queryMd5 = ragCache.md5(originalQuery);
                String resultText = allContents.stream().map(c -> c.textSegment().text()).collect(Collectors.joining("\n---\n"));
                float[] queryEmbedding = embeddingModel.embed(originalQuery).content().vector();
                ragCache.cacheResult(queryMd5, originalQuery, resultText, queryEmbedding);
            } catch (Exception e) { log.warn("缓存写入失败: {}", e.getMessage()); }
        }

        return allContents;
    }

    /** 增强RAG检索 + 详细结果 (被 @Tool searchKnowledgeBase 调用) */
    public EnhancedRAGResult retrieveWithDetails(String originalQuery) {
        long t0 = System.nanoTime();

        // 查询改写计时
        long embMs = 0;
        String rewrittenQuery = originalQuery;
        List<String> expandedQueries = List.of();
        if (useQueryRewrite) {
            long t1 = System.nanoTime();
            QueryRewriteService.RewriteResult rewriteResult = queryRewriteService.process(originalQuery);
            rewrittenQuery = rewriteResult.rewrittenQuery();
            expandedQueries = rewriteResult.expandedQueries();
            embMs += (System.nanoTime() - t1) / 1_000_000;
        }

        // HyDE 计时
        List<String> hypotheticalDocs = List.of();
        if (useHyde) {
            long t2 = System.nanoTime();
            HyDEService.HyDEResult hydeResult = hydeService.process(originalQuery);
            hypotheticalDocs = hydeResult.hypotheticalDocuments();
            embMs += (System.nanoTime() - t2) / 1_000_000;
        }

        // 总检索计时 (内含 embedding + vector + BM25 + rerank)
        long t3 = System.nanoTime();
        List<Content> contents = retrieve(originalQuery);
        long retrieveMs = (System.nanoTime() - t3) / 1_000_000;
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        // 记录到 session 级指标
        String sid = MemoryIdContext.get();
        if (sid != null) {
            sessionMetrics.recordRagPipeline(sid, embMs, 0, 0, 0, 0, totalMs);
        }

        return new EnhancedRAGResult(originalQuery, rewrittenQuery, expandedQueries, hypotheticalDocs, contents, totalMs);
    }

    public record EnhancedRAGResult(
            String originalQuery, String rewrittenQuery,
            List<String> expandedQueries, List<String> hypotheticalDocuments,
            List<Content> contents, long durationMs) {
        public int totalContents() { return contents != null ? contents.size() : 0; }
        public boolean hasRewrite() { return !originalQuery.equals(rewrittenQuery); }
        public boolean hasHyDE() { return hypotheticalDocuments != null && !hypotheticalDocuments.isEmpty(); }
    }
}
