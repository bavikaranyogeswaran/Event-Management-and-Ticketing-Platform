package com.ticketing.reporting;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Reads a job id off the export queue and hands it to the dispatcher. */
@Component
class CsvExportConsumer {

    private final CsvExportDispatcher dispatcher;

    CsvExportConsumer(CsvExportDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = "${app.messaging.export-queue}")
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
