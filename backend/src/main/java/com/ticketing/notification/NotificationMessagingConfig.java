package com.ticketing.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticketing.shared.config.AppProperties;

/**
 * The RabbitMQ topology the email pipeline runs on: one exchange the relay publishes to,
 * one queue the consumer reads. A rejected message goes to a dead-letter queue instead of
 * being redelivered forever; retries themselves live in Postgres, not here.
 */
@Configuration
class NotificationMessagingConfig {

    private final AppProperties.Messaging messaging;

    NotificationMessagingConfig(AppProperties properties) {
        this.messaging = properties.messaging();
    }

    @Bean
    DirectExchange emailExchange() {
        return new DirectExchange(messaging.exchange(), true, false);
    }

    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(messaging.emailQueue())
                .withArgument("x-dead-letter-exchange", messaging.deadLetterExchange())
                .build();
    }

    @Bean
    Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(emailExchange()).with(messaging.emailRoutingKey());
    }

    @Bean
    FanoutExchange emailDeadLetterExchange() {
        return new FanoutExchange(messaging.deadLetterExchange(), true, false);
    }

    @Bean
    Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(messaging.deadLetterQueue()).build();
    }

    @Bean
    Binding emailDeadLetterBinding() {
        return BindingBuilder.bind(emailDeadLetterQueue()).to(emailDeadLetterExchange());
    }
}
