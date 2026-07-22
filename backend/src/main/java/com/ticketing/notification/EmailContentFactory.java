package com.ticketing.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.config.AppProperties;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Turns an outbox job into a ready-to-send email. The renderer keeps its own view of each
 * payload — agreeing with the producer only on JSON field names — so notification and the
 * producing modules stay decoupled across the outbox boundary.
 */
@Component
class EmailContentFactory {

    // shown in the reader's local wording, rendered in the event's own timezone
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private final ObjectMapper objectMapper;
    private final UserRepository users;
    private final OrganizerProfileRepository organizerProfiles;
    private final EventRepository events;
    private final String baseUrl;

    EmailContentFactory(ObjectMapper objectMapper, UserRepository users,
            OrganizerProfileRepository organizerProfiles, EventRepository events, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.users = users;
        this.organizerProfiles = organizerProfiles;
        this.events = events;
        this.baseUrl = properties.baseUrl();
    }

    EmailMessage render(String jobKey, String payloadJson) {
        return switch (JobTypes.kindOf(jobKey)) {
            case JobTypes.EMAIL_VERIFICATION -> verification(read(payloadJson, LinkPayload.class));
            case JobTypes.PASSWORD_RESET -> passwordReset(read(payloadJson, LinkPayload.class));
            case JobTypes.ORDER_CONFIRMATION -> orderConfirmation(read(payloadJson, OrderPayload.class));
            case JobTypes.EVENT_DECISION -> eventDecision(read(payloadJson, DecisionPayload.class));
            case JobTypes.EVENT_CANCELLATION -> eventCancellation(read(payloadJson, CancellationPayload.class));
            case JobTypes.REMINDER -> reminder(read(payloadJson, ReminderPayload.class));
            default -> throw new IllegalStateException("No email template for job key " + jobKey);
        };
    }

    // ---- per-kind templates ----

    private EmailMessage verification(LinkPayload p) {
        return new EmailMessage(p.to(), "Verify your email address", """
                Hi %s,

                Confirm your email address to finish setting up your account:
                %s

                If you did not create an account, you can ignore this message."""
                .formatted(p.displayName(), p.link()));
    }

    private EmailMessage passwordReset(LinkPayload p) {
        return new EmailMessage(p.to(), "Reset your password", """
                Hi %s,

                We received a request to reset your password:
                %s

                If you did not ask for this, your password is unchanged and you can ignore this email."""
                .formatted(p.displayName(), p.link()));
    }

    private EmailMessage orderConfirmation(OrderPayload p) {
        User buyer = requireUser(p.buyerId());
        String eventTitle = eventTitle(p.eventId());
        String body = """
                Hi %s,

                Your order %s is confirmed. %d ticket(s) for %s are ready to view:
                %s/orders/%s

                Bring the QR code to the door."""
                .formatted(buyer.getDisplayName(), p.orderNumber(), p.ticketCount(), eventTitle,
                        baseUrl, p.orderId());
        return new EmailMessage(buyer.getEmail(), "Your tickets for " + eventTitle, body);
    }

    private EmailMessage eventDecision(DecisionPayload p) {
        User organizer = requireOrganizer(p.organizerId());
        boolean approved = "APPROVED".equals(p.decision());
        String subject = approved
                ? "Your event \"%s\" is approved".formatted(p.eventTitle())
                : "Your event \"%s\" needs changes".formatted(p.eventTitle());
        String body = approved
                ? "Hi %s,\n\nGood news — \"%s\" has been approved and is now published."
                        .formatted(organizer.getDisplayName(), p.eventTitle())
                : "Hi %s,\n\n\"%s\" was not approved.\n\nReason: %s\n\nYou can make changes and resubmit it."
                        .formatted(organizer.getDisplayName(), p.eventTitle(), p.reason());
        return new EmailMessage(organizer.getEmail(), subject, body);
    }

    private EmailMessage eventCancellation(CancellationPayload p) {
        User holder = requireUser(p.holderUserId());
        String body = """
                Hi %s,

                We're sorry to say that "%s" has been cancelled. Your ticket(s) for it are no longer valid.

                If you paid for a ticket, the organizer will be in touch about a refund."""
                .formatted(holder.getDisplayName(), p.eventTitle());
        return new EmailMessage(holder.getEmail(), "\"%s\" has been cancelled".formatted(p.eventTitle()), body);
    }

    private EmailMessage reminder(ReminderPayload p) {
        User holder = requireUser(p.holderUserId());
        Event event = requireEvent(p.eventId());
        String when = WHEN.format(event.getStartsAt().atZone(ZoneId.of(event.getTimezone())));
        String body = """
                Hi %s,

                A quick reminder that "%s" is coming up:
                %s

                Your tickets are here:
                %s/tickets

                See you there."""
                .formatted(holder.getDisplayName(), event.getTitle(), when, baseUrl);
        return new EmailMessage(holder.getEmail(), "Reminder: \"%s\" is coming up".formatted(event.getTitle()), body);
    }

    // ---- resolution helpers ----

    private <T> T read(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No recipient user " + userId));
    }

    private User requireOrganizer(UUID organizerProfileId) {
        // the decision job carries the organizer profile id, so resolve it to the owning user
        OrganizerProfile profile = organizerProfiles.findById(organizerProfileId)
                .orElseThrow(() -> new IllegalStateException("No organizer profile " + organizerProfileId));
        return requireUser(profile.getUserId());
    }

    private String eventTitle(UUID eventId) {
        return events.findById(eventId).map(Event::getTitle).orElse("your event");
    }

    private Event requireEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("No event " + eventId));
    }

    // ---- payload views (match the producers' JSON field names, nothing more) ----

    private record LinkPayload(String to, String displayName, String link) {
    }

    private record OrderPayload(UUID orderId, String orderNumber, UUID buyerId, UUID eventId, int ticketCount) {
    }

    private record DecisionPayload(UUID eventId, String eventTitle, UUID organizerId, String decision, String reason) {
    }

    private record CancellationPayload(UUID eventId, String eventTitle, UUID holderUserId) {
    }

    private record ReminderPayload(UUID eventId, UUID holderUserId) {
    }
}
