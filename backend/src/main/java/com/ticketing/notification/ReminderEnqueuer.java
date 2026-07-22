package com.ticketing.notification;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.ticket.TicketRepository;
import com.ticketing.ticket.TicketStatus;

/** Writes one reminder job per valid ticket holder of an event, skipping any holder already queued. */
@Service
class ReminderEnqueuer {

    private final TicketRepository tickets;
    private final OutboxJobRepository jobs;
    private final OutboxJobService outbox;

    ReminderEnqueuer(TicketRepository tickets, OutboxJobRepository jobs, OutboxJobService outbox) {
        this.tickets = tickets;
        this.jobs = jobs;
        this.outbox = outbox;
    }

    @Transactional
    int enqueueForEvent(UUID eventId) {
        int enqueued = 0;
        for (UUID holderId : tickets.findDistinctOwnerIdsByEventIdAndStatus(eventId, TicketStatus.VALID)) {
            String key = JobTypes.reminderKey(eventId, holderId);
            // the key is unique in the outbox; skipping known holders keeps the repeating sweep from clashing with it
            if (jobs.findByJobKey(key).isEmpty()) {
                outbox.enqueue(JobTypes.EMAIL, key, new ReminderJob(eventId, holderId));
                enqueued++;
            }
        }
        return enqueued;
    }
}
