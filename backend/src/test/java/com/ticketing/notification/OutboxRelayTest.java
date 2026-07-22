package com.ticketing.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

// the consumer is silenced here so the relay's published ids stay on the queue for this test to read
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class OutboxRelayTest extends AbstractIntegrationTest {

    @Autowired
    OutboxRelay relay;
    @Autowired
    OutboxClaimer claimer;
    @Autowired
    OutboxJobRepository jobs;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    AppProperties properties;
    @Autowired
    Clock clock;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM outbox_jobs");
        amqpAdmin.purgeQueue(emailQueue(), false);
        amqpAdmin.purgeQueue(properties.messaging().deadLetterQueue(), false);
    }

    private String emailQueue() {
        return properties.messaging().emailQueue();
    }

    private OutboxJob enqueue(String key, Instant dueAt) {
        return jobs.saveAndFlush(new OutboxJob(UUID.randomUUID(), "EMAIL", key, "{}", dueAt));
    }

    private OutboxJob enqueuePublishing(String key) {
        OutboxJob job = new OutboxJob(UUID.randomUUID(), "EMAIL", key, "{}", Instant.now(clock));
        job.markPublishing();
        return jobs.saveAndFlush(job);
    }

    @Test
    void aDueJobIsMarkedPublishingAndItsIdLandsOnTheQueue() {
        OutboxJob job = enqueue("ORDER_CONFIRMATION:" + UUID.randomUUID(), Instant.now(clock).minusSeconds(1));

        int published = relay.publishDueJobs();

        assertThat(published).isEqualTo(1);
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(rabbitTemplate.receiveAndConvert(emailQueue(), 5000)).isEqualTo(job.getId().toString());
    }

    @Test
    void aJobNotYetDueIsLeftAlone() {
        OutboxJob job = enqueue("x:" + UUID.randomUUID(), Instant.now(clock).plusSeconds(300));

        int published = relay.publishDueJobs();

        assertThat(published).isZero();
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(rabbitTemplate.receiveAndConvert(emailQueue())).isNull();
    }

    @Test
    void aPublishThatNeverReachesTheBrokerReturnsToPendingWithoutSpendingAnAttempt() {
        OutboxJob job = enqueue("y:" + UUID.randomUUID(), Instant.now(clock).minusSeconds(1));
        OutboxPublisher failing = mock(OutboxPublisher.class);
        doThrow(new RuntimeException("broker down")).when(failing).publish(any());
        OutboxRelay failingRelay = new OutboxRelay(claimer, failing, clock, properties);

        int published = failingRelay.publishDueJobs();

        assertThat(published).isZero();
        OutboxJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
        assertThat(rabbitTemplate.receiveAndConvert(emailQueue())).isNull();
    }

    @Test
    void aStalePublishingJobIsRecoveredButAFreshOneIsLeftInFlight() {
        OutboxJob stale = enqueuePublishing("stale:" + UUID.randomUUID());
        // simulate the claim having committed well before the grace period
        jdbc.update("UPDATE outbox_jobs SET updated_at = now() - interval '10 minutes' WHERE id = ?", stale.getId());
        OutboxJob fresh = enqueuePublishing("fresh:" + UUID.randomUUID());

        int recovered = relay.recoverStuckJobs();

        assertThat(recovered).isEqualTo(1);
        assertThat(jobs.findById(stale.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(jobs.findById(fresh.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
    }
}
