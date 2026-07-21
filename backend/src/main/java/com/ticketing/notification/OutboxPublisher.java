package com.ticketing.notification;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Puts a job's id on the email exchange; the body is only a pointer, the job itself stays in the database. */
@Component
class OutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    OutboxPublisher(RabbitTemplate rabbitTemplate, AppProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = properties.messaging().exchange();
        this.routingKey = properties.messaging().emailRoutingKey();
    }

    void publish(UUID jobId) {
        rabbitTemplate.convertAndSend(exchange, routingKey, jobId.toString());
    }
}
