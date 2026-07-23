package com.ticketing.notification;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.file.FakeObjectStorage;
import com.ticketing.file.TestObjectStorageConfig;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives a FILE_DELETE message all the way through the real listener and broker. */
@Import(TestObjectStorageConfig.class)
class FileDeleteConsumerTest extends AbstractIntegrationTest {

    @Autowired OutboxPublisher publisher;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired OutboxJobRepository jobs;
    @Autowired AppProperties properties;
    @Autowired FakeObjectStorage storage;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        storage.reset();
        jdbc.update("DELETE FROM outbox_jobs");
        amqpAdmin.purgeQueue(properties.messaging().fileDeleteQueue(), false);
        amqpAdmin.purgeQueue(properties.messaging().fileDeleteDeadLetterQueue(), false);
    }

    @Test
    void aPublishedFileDeleteJobIsConsumedAndDestroysTheProviderCopy() {
        String publicId = "banners/" + UUID.randomUUID();
        String payload = "{\"publicId\":\"" + publicId + "\"}";
        OutboxJob job = new OutboxJob(UUID.randomUUID(), JobTypes.FILE_DELETE,
                JobTypes.fileDeleteKey(UUID.randomUUID()), payload, Instant.now());
        job.markPublishing();
        jobs.saveAndFlush(job);

        publisher.publish(job.getId(), JobTypes.FILE_DELETE);

        awaitStatus(job.getId(), OutboxStatus.SENT);
        assertThat(storage.wasDestroyed(publicId)).isTrue();
    }

    @Test
    void anUnreadableMessageIsRoutedToTheFileDeleteDeadLetterQueue() {
        rabbitTemplate.convertAndSend(properties.messaging().exchange(),
                properties.messaging().fileDeleteRoutingKey(), "not-a-uuid");

        Object deadLettered = rabbitTemplate.receiveAndConvert(
                properties.messaging().fileDeleteDeadLetterQueue(), 5000);
        assertThat(deadLettered).isEqualTo("not-a-uuid");
    }

    private void awaitStatus(UUID id, OutboxStatus expected) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            if (jobs.findById(id).map(OutboxJob::getStatus).filter(expected::equals).isPresent()) {
                return;
            }
            sleep();
        }
        assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo(expected);
    }

    private void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
