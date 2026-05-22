package com.itheima.agent.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String KNOWLEDGE_REBUILD_EXCHANGE = "knowledge.rebuild.exchange";
    public static final String KNOWLEDGE_REBUILD_QUEUE = "knowledge.rebuild.queue";
    public static final String KNOWLEDGE_REBUILD_ROUTING_KEY = "knowledge.rebuild.task";
    public static final String KNOWLEDGE_STATUS_EXCHANGE = "knowledge.status.exchange";
    public static final String KNOWLEDGE_STATUS_QUEUE = "knowledge.status.queue";
    public static final String KNOWLEDGE_STATUS_ROUTING_KEY = "knowledge.status.update";

    @Bean
    public DirectExchange knowledgeRebuildExchange() {
        return ExchangeBuilder
                .directExchange(KNOWLEDGE_REBUILD_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange knowledgeStatusExchange() {
        return ExchangeBuilder
                .directExchange(KNOWLEDGE_STATUS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue knowledgeRebuildQueue() {
        return QueueBuilder
                .durable(KNOWLEDGE_REBUILD_QUEUE)
                .build();
    }

    @Bean
    public Queue knowledgeStatusQueue() {
        return QueueBuilder
                .durable(KNOWLEDGE_STATUS_QUEUE)
                .build();
    }

    @Bean
    public Binding knowledgeRebuildBinding() {
        return BindingBuilder
                .bind(knowledgeRebuildQueue())
                .to(knowledgeRebuildExchange())
                .with(KNOWLEDGE_REBUILD_ROUTING_KEY);
    }

    @Bean
    public Binding knowledgeStatusBinding() {
        return BindingBuilder
                .bind(knowledgeStatusQueue())
                .to(knowledgeStatusExchange())
                .with(KNOWLEDGE_STATUS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
