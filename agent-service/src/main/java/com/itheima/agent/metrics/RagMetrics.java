package com.itheima.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central metrics registry for RAG agent operations.
 * <p>
 * Exposes Micrometer-backed metrics for monitoring retrieval-augmented generation
 * pipeline performance, tool execution, and task lifecycle in the agent service.
 * All metrics are tagged with {@code application: agent-service} for multi-service
 * dashboard filtering.
 */
@Component
public class RagMetrics {

    /** Common tag applied to every metric for service-level identification. */
    private static final List<Tag> APP_TAG = List.of(Tag.of("application", "agent-service"));

    // -----------------------------------------------------------------------
    // Timers
    // -----------------------------------------------------------------------

    /**
     * Time to first token (TTFT) — measures how long the RAG pipeline takes to
     * begin producing output after receiving a query.
     */
    private final Timer ttftTimer;

    /**
     * End-to-end latency — measures total time from query receipt to final
     * response delivery.
     */
    private final Timer e2eTimer;

    /**
     * Task duration — wall-clock time for a complete agent task lifecycle
     * (planning, retrieval, generation, tool calls).
     */
    private final Timer taskDurationTimer;

    /**
     * Query duration — latency of the underlying query execution path.
     * Previously registered but never sampled; now wired for use.
     */
    private final Timer queryDurationTimer;

    // ---- RAG pipeline stage timers ----

    /** Query → embedding vector (text-embedding-v3 API). */
    private final Timer embeddingTimer;

    /** Vector similarity search (RediSearch HNSW). */
    private final Timer vectorSearchTimer;

    /** BM25 keyword search (HanLP + Redis inverted index). */
    private final Timer bm25SearchTimer;

    /** Rerank model call (gte-rerank-v2 API). */
    private final Timer rerankTimer;

    /** Semantic cache lookup (cosine similarity check). */
    private final Timer cacheCheckTimer;

    /** Total end-to-end retrieval latency (embedding → results). */
    private final Timer totalRetrieveTimer;

    // -----------------------------------------------------------------------
    // Gauges
    // -----------------------------------------------------------------------

    /**
     * Tokens-per-second generation rate, backed by an {@link AtomicReference}
     * so the value can be atomically updated at runtime.
     */
    private final AtomicReference<Double> tpsGauge;

    // -----------------------------------------------------------------------
    // Counters
    // -----------------------------------------------------------------------

    /** Count of successfully completed agent tasks. */
    private final Counter taskSuccessCounter;

    /** Count of failed agent tasks. */
    private final Counter taskFailureCounter;

    /** Count of successful tool invocations. */
    private final Counter toolSuccessCounter;

    /** Count of failed tool invocations. */
    private final Counter toolFailureCounter;

    // -----------------------------------------------------------------------
    // Distribution summaries
    // -----------------------------------------------------------------------

    /**
     * Distribution summary tracking the number of tokens consumed per
     * conversation turn (prompt + completion).
     */
    private final DistributionSummary tokenUsageHistogram;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        // --- Timers ---
        this.ttftTimer = Timer.builder("rag.ttft")
                .description("Time-to-first-token for RAG pipeline responses")
                .tags(APP_TAG)
                .register(registry);

        this.e2eTimer = Timer.builder("rag.e2e")
                .description("End-to-end latency from query receipt to final response")
                .tags(APP_TAG)
                .register(registry);

        this.taskDurationTimer = Timer.builder("rag.task.duration")
                .description("Wall-clock duration of an entire agent task lifecycle")
                .tags(APP_TAG)
                .register(registry);

        this.queryDurationTimer = Timer.builder("rag.query.duration")
                .description("Latency of the underlying query execution path")
                .tags(APP_TAG)
                .register(registry);

        // ---- RAG pipeline stages ----
        this.embeddingTimer = Timer.builder("rag.pipeline.embedding")
                .description("Query embedding latency (text-embedding-v3)")
                .tags(APP_TAG).register(registry);
        this.vectorSearchTimer = Timer.builder("rag.pipeline.vector.search")
                .description("Vector similarity search latency (RediSearch HNSW)")
                .tags(APP_TAG).register(registry);
        this.bm25SearchTimer = Timer.builder("rag.pipeline.bm25.search")
                .description("BM25 keyword search latency (HanLP + Redis)")
                .tags(APP_TAG).register(registry);
        this.rerankTimer = Timer.builder("rag.pipeline.rerank")
                .description("Rerank model call latency (gte-rerank-v2)")
                .tags(APP_TAG).register(registry);
        this.cacheCheckTimer = Timer.builder("rag.pipeline.cache.check")
                .description("Semantic cache lookup latency")
                .tags(APP_TAG).register(registry);
        this.totalRetrieveTimer = Timer.builder("rag.pipeline.total.retrieve")
                .description("Total end-to-end retrieval latency (embedding → final results)")
                .tags(APP_TAG).register(registry);

        // --- Gauge ---
        this.tpsGauge = new AtomicReference<>(0.0);
        io.micrometer.core.instrument.Gauge.builder("rag.tps", tpsGauge, AtomicReference::get)
                .description("Token generation rate in tokens per second")
                .tags(APP_TAG)
                .register(registry);

        // --- Counters ---
        this.taskSuccessCounter = Counter.builder("rag.task.completed")
                .description("Number of agent tasks that completed successfully")
                .tags(APP_TAG)
                .register(registry);

        this.taskFailureCounter = Counter.builder("rag.task.failed")
                .description("Number of agent tasks that failed")
                .tags(APP_TAG)
                .register(registry);

        this.toolSuccessCounter = Counter.builder("rag.tool.calls")
                .description("Number of successful tool invocations")
                .tags(APP_TAG)
                .register(registry);

        this.toolFailureCounter = Counter.builder("rag.tool.failures")
                .description("Number of failed tool invocations")
                .tags(APP_TAG)
                .register(registry);

        // --- Distribution summary ---
        this.tokenUsageHistogram = DistributionSummary.builder("rag.token.usage")
                .description("Distribution of tokens consumed per conversation turn")
                .tags(APP_TAG)
                .register(registry);
    }

    // -----------------------------------------------------------------------
    // Sampler methods
    // -----------------------------------------------------------------------

    /**
     * Start a new {@link Timer.Sample} bound to this registry. Callers should
     * pass the returned sample to one of the {@code record*} methods once the
     * measured operation completes.
     */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    /** Record end-to-end latency. */
    public void recordE2E(Timer.Sample sample) {
        sample.stop(e2eTimer);
    }

    /** Record time to first token. */
    public void recordTTFT(Timer.Sample sample) {
        sample.stop(ttftTimer);
    }

    /**
     * Record a successful task completion, including the task duration and
     * the number of tokens consumed.
     *
     * @param sample     timer sample representing the task duration
     * @param tokenCount number of tokens consumed during the task
     */
    public void recordTaskSuccess(Timer.Sample sample, long tokenCount) {
        sample.stop(taskDurationTimer);
        taskSuccessCounter.increment();
        tokenUsageHistogram.record(tokenCount);
    }

    /** Record a failed task (no token recording). */
    public void recordTaskFailure() {
        taskFailureCounter.increment();
    }

    /**
     * Record a tool call outcome.
     *
     * @param success {@code true} if the tool completed without error
     */
    public void recordToolCall(boolean success) {
        if (success) {
            toolSuccessCounter.increment();
        } else {
            toolFailureCounter.increment();
        }
    }

    /** Atomically update the tokens-per-second gauge value. */
    public void setTPS(double tps) {
        this.tpsGauge.set(tps);
    }

    /** Record query execution duration. */
    public void recordQueryDuration(Timer.Sample sample) {
        sample.stop(queryDurationTimer);
    }

    // ---- RAG pipeline stage recorders ----

    public void recordEmbedding(Timer.Sample sample)   { sample.stop(embeddingTimer); }
    public void recordVectorSearch(Timer.Sample sample) { sample.stop(vectorSearchTimer); }
    public void recordBM25Search(Timer.Sample sample)   { sample.stop(bm25SearchTimer); }
    public void recordRerank(Timer.Sample sample)       { sample.stop(rerankTimer); }
    public void recordCacheCheck(Timer.Sample sample)   { sample.stop(cacheCheckTimer); }
    public void recordTotalRetrieve(Timer.Sample sample) { sample.stop(totalRetrieveTimer); }

    /** Expose the registry so controllers can build custom metric views. */
    public MeterRegistry getRegistry() {
        return registry;
    }

    private final MeterRegistry registry;
}
