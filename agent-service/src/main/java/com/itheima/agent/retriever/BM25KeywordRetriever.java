package com.itheima.agent.retriever;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.itheima.agent.service.HistoryRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索器
 *
 * BM25 公式: score(q,d) = SUM( IDF(t) * TF(t,d) * (k1+1) / (TF(t,d) + k1*(1-b+b*len/avgLen)) )
 * IDF(t) = log( (N - df + 0.5) / (df + 0.5) + 1 )
 *
 * 索引结构 (Redis):
 *   rag:bm25:term:{term}    → JSON {df: N, postings: [{chunkId, tf, docLen}]}
 *   rag:bm25:stats           → JSON {totalDocs: N, avgDocLen: L}
 */
@Slf4j
@Component
public class BM25KeywordRetriever {

    private static final double K1 = 1.5;
    private static final double B  = 0.75;
    private static final String TERM_PREFIX = "rag:bm25:term:";
    private static final String STATS_KEY  = "rag:bm25:stats";
    private static final Pattern ALPHANUMERIC = Pattern.compile("[a-zA-Z0-9]+(?:[-_][a-zA-Z0-9]+)*");

    private final StringRedisTemplate redisTemplate;

    @Value("${rag.bm25.min-score:0.1}")
    private double minScore;

    public BM25KeywordRetriever(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ---- 离线建索引 (由 KnowledgeBaseIngestionService 调用) ----

    /**
     * 为文档 chunk 列表构建 BM25 索引
     */
    public void buildIndex(List<String> chunkTexts) {
        Map<String, int[]> termDocFreq = new HashMap<>(); // term → [df, totalTF]
        int[] docLengths = new int[chunkTexts.size()];
        int totalDocs = chunkTexts.size();

        // 逐文档统计 TF，累积 DF
        for (int i = 0; i < chunkTexts.size(); i++) {
            List<String> tokens = tokenize(chunkTexts.get(i));
            docLengths[i] = tokens.size();
            Map<String, Integer> tfMap = new HashMap<>();
            for (String t : tokens) tfMap.merge(t, 1, Integer::sum);

            // 存储当前文档对各 term 的 TF
            for (Map.Entry<String, Integer> e : tfMap.entrySet()) {
                String term = e.getKey();
                int tf = e.getValue();
                // 用 Redis Hash field 存每个文档的 TF: rag:bm25:term:{t} → hset {chunkId} {tf_bytes}
                redisTemplate.opsForHash().put(TERM_PREFIX + term, String.valueOf(i),
                        tf + "," + docLengths[i]);
            }
        }

        // 计算 stats
        long totalLen = 0;
        for (int len : docLengths) totalLen += len;
        double avgLen = totalDocs > 0 ? (double) totalLen / totalDocs : 1.0;
        redisTemplate.opsForValue().set(STATS_KEY, totalDocs + "," + avgLen);

        log.info("BM25 index built: {} docs, avgDocLen={:.1f}", totalDocs, avgLen);
    }

    // ---- 在线检索 (由 HybridRerankRetriever 调用) ----

    /**
     * BM25 检索，返回 (chunkIndex, score) 列表
     */
    public List<Map.Entry<Integer, Double>> search(String query, int topK) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return Collections.emptyList();

        String statsStr = redisTemplate.opsForValue().get(STATS_KEY);
        if (statsStr == null) return Collections.emptyList();
        String[] parts = statsStr.split(",");
        int totalDocs = Integer.parseInt(parts[0]);
        double avgDocLen = Double.parseDouble(parts[1]);

        // 对每个 term, 计算 IDF, 然后查各文档的 TF
        Map<String, Double> idfCache = new HashMap<>();
        Map<Integer, Double> docScores = new HashMap<>(); // docIdx → accumulated score

        for (String term : tokens) {
            double idf = idfCache.computeIfAbsent(term, t -> computeIDF(totalDocs, t));
            if (idf == 0) continue;

            // 取出该 term 下所有文档的 tf,docLen
            Map<Object, Object> postings = redisTemplate.opsForHash().entries(TERM_PREFIX + term);
            for (Map.Entry<Object, Object> e : postings.entrySet()) {
                int docIdx = Integer.parseInt((String) e.getKey());
                String[] v = ((String) e.getValue()).split(",");
                int tf    = Integer.parseInt(v[0]);
                int docLen = Integer.parseInt(v[1]);

                double numerator   = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * docLen / avgDocLen);
                double score = idf * numerator / denominator;

                docScores.merge(docIdx, score, Double::sum);
            }
        }

        // 排序返回 topK
        return docScores.entrySet().stream()
                .filter(e -> e.getValue() >= minScore)
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public void clearIndex() {
        var keys = redisTemplate.keys(TERM_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        redisTemplate.delete(STATS_KEY);
    }

    // ---- 内部算法 ----

    private double computeIDF(int N, String term) {
        Long df = redisTemplate.opsForHash().size(TERM_PREFIX + term);
        if (df == null || df == 0) return 0;
        return Math.log((N - df + 0.5) / (df + 0.5) + 1);
    }

    /**
     * 中文+英文混合分词，保护故障代码不被分割
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = ALPHANUMERIC.matcher(text);
        StringBuilder withoutAlnum = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            withoutAlnum.append(text, lastEnd, m.start());
            String alnum = m.group();
            tokens.add(alnum);
            tokens.add(alnum.toLowerCase()); // 大小写变体
            lastEnd = m.end();
        }
        withoutAlnum.append(text.substring(lastEnd));

        try {
            for (Term t : HanLP.segment(withoutAlnum.toString())) {
                String w = t.word.trim();
                if (w.length() <= 1 && !Character.isLetterOrDigit(w.charAt(0))) continue;
                tokens.add(w);
            }
        } catch (NoClassDefFoundError e) {
            for (String w : withoutAlnum.toString().split("[\\s，。！？、；：\\.\\?!;:]+")) {
                if (!w.isEmpty()) tokens.add(w.trim());
            }
        }
        return tokens;
    }
}
