package com.mario.backend.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AUDIT_EXCHANGE = "audit.exchange";
    public static final String AUDIT_DLX = "audit.dlx";
    public static final String AUDIT_DLQ = "audit.dlq";

    public static final String AUDIT_PERSIST_QUEUE = "audit.persist.queue";
    public static final String AUDIT_SEARCH_QUEUE = "audit.search.queue";
    public static final String AUDIT_ALERT_QUEUE = "audit.alert.queue";

    // Dead letter exchange and queue
    @Bean
    public FanoutExchange auditDlx() {
        return new FanoutExchange(AUDIT_DLX, true, false);
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDlq()).to(auditDlx());
    }

    // Main fanout exchange
    @Bean
    public FanoutExchange auditExchange() {
        return new FanoutExchange(AUDIT_EXCHANGE, true, false);
    }

    // Persist queue (MySQL)
    @Bean
    public Queue auditPersistQueue() {
        return QueueBuilder.durable(AUDIT_PERSIST_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_DLX)
                .build();
    }

    @Bean
    public Binding auditPersistBinding() {
        return BindingBuilder.bind(auditPersistQueue()).to(auditExchange());
    }

    // Search queue (Elasticsearch)
    @Bean
    public Queue auditSearchQueue() {
        return QueueBuilder.durable(AUDIT_SEARCH_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_DLX)
                .build();
    }

    @Bean
    public Binding auditSearchBinding() {
        return BindingBuilder.bind(auditSearchQueue()).to(auditExchange());
    }

    // Alert queue (Email)
    @Bean
    public Queue auditAlertQueue() {
        return QueueBuilder.durable(AUDIT_ALERT_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_DLX)
                .build();
    }

    @Bean
    public Binding auditAlertBinding() {
        return BindingBuilder.bind(auditAlertQueue()).to(auditExchange());
    }

    // Message converter
    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
