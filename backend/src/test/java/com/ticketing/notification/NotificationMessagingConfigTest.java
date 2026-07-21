package com.ticketing.notification;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMessagingConfigTest extends AbstractIntegrationTest {

    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    AppProperties properties;
    @Autowired
    Queue emailQueue;

    @Test
    void theEmailAndDeadLetterQueuesExistOnTheBroker() {
        AppProperties.Messaging messaging = properties.messaging();

        // getQueueProperties returns null unless the queue was actually declared on the broker
        assertThat(amqpAdmin.getQueueProperties(messaging.emailQueue())).isNotNull();
        assertThat(amqpAdmin.getQueueProperties(messaging.deadLetterQueue())).isNotNull();
    }

    @Test
    void theEmailQueueDeadLettersRejectedMessages() {
        assertThat(emailQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", properties.messaging().deadLetterExchange());
    }
}
