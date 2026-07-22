package com.ticketing.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class EmailDispatcherTest extends AbstractIntegrationTest {

    // verification jobs carry the recipient inline, so rendering needs no other fixtures
    private static final String PAYLOAD =
            "{\"to\":\"asha@example.com\",\"displayName\":\"Asha\",\"link\":\"https://app/verify?token=x\"}";

    @MockitoBean
    EmailSender emailSender;
    @Autowired
    EmailDispatcher dispatcher;
    @Autowired
    OutboxJobRepository jobs;
    @Autowired
    AppProperties properties;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private OutboxJob publishingJob() {
        OutboxJob job = new OutboxJob(UUID.randomUUID(), "EMAIL",
                JobTypes.emailVerificationKey(UUID.randomUUID()), PAYLOAD, Instant.now());
        job.markPublishing();
        return jobs.saveAndFlush(job);
    }

    // returns the job to the state the relay would set before the next attempt
    private void reclaim(UUID id) {
        OutboxJob job = jobs.findById(id).orElseThrow();
        job.markPublishing();
        jobs.saveAndFlush(job);
    }

    @Test
    void aSuccessfulSendMarksTheJobSent() {
        OutboxJob job = publishingJob();

        dispatcher.deliver(job.getId());

        OutboxJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(reloaded.getSentAt()).isNotNull();
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("asha@example.com");
        assertThat(sent.getValue().subject()).isEqualTo("Verify your email address");
    }

    @Test
    void aJobIsNeverSentTwice() {
        OutboxJob job = publishingJob();

        dispatcher.deliver(job.getId());
        dispatcher.deliver(job.getId()); // a redelivery of the same job

        verify(emailSender, times(1)).send(any());
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void aFailingSendBacksOffThroughEveryStepThenDies() {
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(any());
        OutboxJob job = publishingJob();
        List<Duration> steps = properties.messaging().retryBackoff();

        for (int attempt = 0; attempt < steps.size(); attempt++) {
            Instant before = Instant.now();

            dispatcher.deliver(job.getId());

            Instant after = Instant.now();
            Duration step = steps.get(attempt);
            OutboxJob retried = jobs.findById(job.getId()).orElseThrow();
            assertThat(retried.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(retried.getAttempts()).isEqualTo(attempt + 1);
            // the next attempt is one backoff step out; a second of slack absorbs storage rounding
            assertThat(retried.getNextAttemptAt())
                    .isBetween(before.plus(step).minusSeconds(1), after.plus(step).plusSeconds(1));
            assertThat(retried.getLastError()).isEqualTo("smtp down");
            reclaim(job.getId());
        }

        dispatcher.deliver(job.getId()); // one failure past the last step

        OutboxJob dead = jobs.findById(job.getId()).orElseThrow();
        assertThat(dead.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(steps.size() + 1);
        verify(emailSender, times(steps.size() + 1)).send(any());
    }

    @Test
    void aMessageForAnUnknownJobIsRejectedForDeadLettering() {
        assertThatThrownBy(() -> dispatcher.deliver(UUID.randomUUID()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
