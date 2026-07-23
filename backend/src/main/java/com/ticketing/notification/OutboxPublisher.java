package com.ticketing.notification;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Puts a job's id on the shared exchange, routing to the right queue by job type. */
@Component
class OutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String emailRoutingKey;
    private final String fileDeleteRoutingKey;

    OutboxPublisher(RabbitTemplate rabbitTemplate, AppProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = properties.messaging().exchange();
        this.emailRoutingKey = properties.messaging().emailRoutingKey();
        this.fileDeleteRoutingKey = properties.messaging().fileDeleteRoutingKey();
    }

    void publish(UUID jobId, String jobType) {
        String routingKey = JobTypes.FILE_DELETE.equals(jobType) ? fileDeleteRoutingKey : emailRoutingKey;
        rabbitTemplate.convertAndSend(exchange, routingKey, jobId.toString());
    }
}
