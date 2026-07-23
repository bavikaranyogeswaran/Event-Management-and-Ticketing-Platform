package com.ticketing.file;

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
 * RabbitMQ topology for async file deletion: jobs land on the file-delete queue;
 * rejected ones go to the file-delete DLX/DLQ instead of looping.
 */
@Configuration
class FileMessagingConfig {

    private final AppProperties.Messaging messaging;

    FileMessagingConfig(AppProperties properties) {
        this.messaging = properties.messaging();
    }

    @Bean
    Queue fileDeleteQueue() {
        return QueueBuilder.durable(messaging.fileDeleteQueue())
                .withArgument("x-dead-letter-exchange", messaging.fileDeleteDeadLetterExchange())
                .build();
    }

    @Bean
    Binding fileDeleteBinding(DirectExchange emailExchange) {
        return BindingBuilder.bind(fileDeleteQueue()).to(emailExchange).with(messaging.fileDeleteRoutingKey());
    }

    @Bean
    FanoutExchange fileDeleteDeadLetterExchange() {
        return new FanoutExchange(messaging.fileDeleteDeadLetterExchange(), true, false);
    }

    @Bean
    Queue fileDeleteDeadLetterQueue() {
        return QueueBuilder.durable(messaging.fileDeleteDeadLetterQueue()).build();
    }

    @Bean
    Binding fileDeleteDeadLetterBinding() {
        return BindingBuilder.bind(fileDeleteDeadLetterQueue()).to(fileDeleteDeadLetterExchange());
    }
}
