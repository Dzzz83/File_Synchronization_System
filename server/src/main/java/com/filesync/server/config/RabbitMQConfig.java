package com.filesync.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig
{
    public static final String SYNC_QUEUE = "sync.queue";
    public static final String SYNC_EXCHANGE = "sync.exchange";
    public static final String SYNC_ROUTING_KEY = "sync.routing";

    @Bean
    public Queue syncQueue()
    {
        return new Queue(SYNC_QUEUE, true);
    }

    @Bean
    public DirectExchange syncExchange()
    {
        return new DirectExchange(SYNC_EXCHANGE);
    }

    @Bean
    public Binding syncBinding(Queue syncQueue, DirectExchange syncExchange)
    {
        return BindingBuilder.bind(syncQueue).to(syncExchange).with(SYNC_ROUTING_KEY);
    }
}
