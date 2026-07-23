package com.ticketing.notification;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** Drives a message all the way through the real listener and broker. */
class EmailConsumerTest extends AbstractIntegrationTest {

    private static final String PAYLOAD =
            "{\"to\":\"asha@example.com\",\"displayName\":\"Asha\",\"link\":\"https://app/verify?token=x\"}";

    @MockitoBean
    EmailSender emailSender;
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    OutboxJobRepository jobs;
    @Autowired
    AppProperties properties;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM outbox_jobs");
        amqpAdmin.purgeQueue(properties.messaging().emailQueue(), false);
        amqpAdmin.purgeQueue(properties.messaging().deadLetterQueue(), false);
    }

    @Test
    void aPublishedJobIsConsumedRenderedAndMarkedSent() {
        OutboxJob job = new OutboxJob(UUID.randomUUID(), "EMAIL",
                JobTypes.emailVerificationKey(UUID.randomUUID()), PAYLOAD, Instant.now());
        job.markPublishing();
        jobs.saveAndFlush(job);

        publisher.publish(job.getId(), JobTypes.EMAIL);

        awaitStatus(job.getId(), OutboxStatus.SENT);
        verify(emailSender).send(any());
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

    @Test
    void anUnreadableMessageIsRoutedToTheDeadLetterQueue() {
        rabbitTemplate.convertAndSend(properties.messaging().exchange(),
                properties.messaging().emailRoutingKey(), "not-a-uuid");

        Object deadLettered = rabbitTemplate.receiveAndConvert(properties.messaging().deadLetterQueue(), 5000);
        assertThat(deadLettered).isEqualTo("not-a-uuid");
    }
}
