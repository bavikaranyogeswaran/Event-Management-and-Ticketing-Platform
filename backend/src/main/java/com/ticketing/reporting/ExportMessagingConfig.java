package com.ticketing.reporting;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticketing.shared.config.AppProperties;

/** Declares the RabbitMQ topology for the CSV export pipeline. */
@Configuration
class ExportMessagingConfig {

    private final AppProperties.Messaging messaging;

    ExportMessagingConfig(AppProperties properties) {
        this.messaging = properties.messaging();
    }

    @Bean
    Queue exportQueue() {
        return QueueBuilder.durable(messaging.exportQueue())
                .withArgument("x-dead-letter-exchange", messaging.exportDeadLetterExchange())
                .build();
    }

    @Bean
    FanoutExchange exportDeadLetterExchange() {
        return new FanoutExchange(messaging.exportDeadLetterExchange(), true, false);
    }

    @Bean
    Queue exportDeadLetterQueue() {
        return QueueBuilder.durable(messaging.exportDeadLetterQueue()).build();
    }

    @Bean
    Binding exportDeadLetterBinding() {
        return BindingBuilder.bind(exportDeadLetterQueue()).to(exportDeadLetterExchange());
    }

    @Bean
    Binding exportBinding() {
        return new Binding(messaging.exportQueue(), Binding.DestinationType.QUEUE,
                messaging.exchange(), messaging.exportRoutingKey(), null);
    }
}
