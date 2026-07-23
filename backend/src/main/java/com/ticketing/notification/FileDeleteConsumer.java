package com.ticketing.notification;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Reads the id off the file-delete queue and hands it to the dispatcher; an unreadable id is dead-lettered. */
@Component
class FileDeleteConsumer {

    private final FileDeleteDispatcher dispatcher;

    FileDeleteConsumer(FileDeleteDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = "${app.messaging.file-delete-queue}")
    void onMessage(String jobId) {
        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Unreadable outbox job id: " + jobId, e);
        }
        dispatcher.deliver(id);
    }
}
