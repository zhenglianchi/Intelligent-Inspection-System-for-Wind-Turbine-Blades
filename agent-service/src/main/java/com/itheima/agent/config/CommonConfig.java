package com.itheima.agent.config;

import com.itheima.agent.retriever.HybridRerankRetriever;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {

    @Bean
    public ContentRetriever contentRetriever(HybridRerankRetriever hybridRerankRetriever) {
        return hybridRerankRetriever;
    }
}
