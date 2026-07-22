package com.ticketing.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.shared.config.AppProperties;

/** Looks for events entering the reminder window and queues a reminder for each of their holders. */
@Component
class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final EventRepository events;
    private final ReminderEnqueuer enqueuer;
    private final Clock clock;
    private final Duration leadTime;

    ReminderScheduler(EventRepository events, ReminderEnqueuer enqueuer, Clock clock, AppProperties properties) {
        this.events = events;
        this.enqueuer = enqueuer;
        this.clock = clock;
        this.leadTime = properties.messaging().reminderLeadTime();
    }

    @Scheduled(fixedDelayString = "${app.messaging.reminder-interval}")
    void scheduledSweep() {
        enqueueDueReminders();
    }

    /** Queues reminders for every event now within the lead time of starting; safe to run repeatedly. */
    int enqueueDueReminders() {
        Instant now = Instant.now(clock);
        Instant until = now.plus(leadTime);
        int enqueued = 0;
        for (Event event : events.findPublishedStartingBetween(now, until)) {
            // each event stands alone, so one failure cannot strand reminders for the others
            enqueued += enqueuer.enqueueForEvent(event.getId());
        }
        if (enqueued > 0) {
            log.info("Queued {} event reminder(s)", enqueued);
        }
        return enqueued;
    }
}
