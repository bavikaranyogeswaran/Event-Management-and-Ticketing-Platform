package com.ticketing.notification;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Reads the id off the email queue and hands it to the dispatcher; an unreadable id is dead-lettered. */
@Component
class EmailConsumer {

    private final EmailDispatcher dispatcher;

    EmailConsumer(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = "${app.messaging.email-queue}")
    void onMessage(String jobId) {
        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            // a message we can't even read: send it to the dead-letter queue instead of looping on it
            throw new AmqpRejectAndDontRequeueException("Unreadable outbox job id: " + jobId, e);
        }
        dispatcher.deliver(id);
    }
}
