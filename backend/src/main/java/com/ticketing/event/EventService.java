package com.ticketing.event;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.Ownership;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.tickettype.TicketTypeStatus;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CategoryRepository categoryRepository;
    private final SlugGenerator slugGenerator;
    private final AuditService auditService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    EventService(EventRepository eventRepository, TicketTypeRepository ticketTypeRepository,
            CategoryRepository categoryRepository, SlugGenerator slugGenerator, AuditService auditService,
            IdGenerator idGenerator, Clock clock) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.categoryRepository = categoryRepository;
        this.slugGenerator = slugGenerator;
        this.auditService = auditService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    // ---- creation & editing ----

    @Transactional
    public Event createDraft(UUID organizerId, EventDraftCommand cmd) {
        requireCategory(cmd.categoryId());
        validateDetails(cmd);

        String slug = slugGenerator.generate(cmd.title(), eventRepository::existsBySlug);
        Event event = Event.builder()
                .id(idGenerator.newId())
                .organizerId(organizerId)
                .categoryId(cmd.categoryId())
                .slug(slug)
                .title(cmd.title().trim())
                .description(cmd.description())
                .eventType(cmd.eventType())
                .venueName(cmd.venueName())
                .addressLine(cmd.addressLine())
                .city(cmd.city())
                .onlineUrl(cmd.onlineUrl())
                .timezone(cmd.timezone())
                .startsAt(cmd.startsAt())
                .endsAt(cmd.endsAt())
                .registrationOpensAt(cmd.registrationOpensAt())
                .registrationClosesAt(cmd.registrationClosesAt())
                .capacity(cmd.capacity())
                .status(EventStatus.DRAFT)
                .build();
        return eventRepository.save(event);
    }

    /** Draft/rejected events allow a full edit; published events allow only descriptive changes. */
    @Transactional
    public Event updateEvent(UUID eventId, UUID organizerId, EventDraftCommand cmd) {
        Event event = ownedEvent(eventId, organizerId);
        switch (event.getStatus()) {
            case DRAFT, REJECTED -> applyFullEdit(event, cmd);
            case PUBLISHED -> applyDescriptiveEdit(event, cmd);
            default -> throw notEditable();
        }
        return event;
    }

    private void applyFullEdit(Event event, EventDraftCommand cmd) {
        requireCategory(cmd.categoryId());
        validateDetails(cmd);
        event.setCategoryId(cmd.categoryId());
        event.setTitle(cmd.title().trim());
        event.setDescription(cmd.description());
        event.setEventType(cmd.eventType());
        event.setVenueName(cmd.venueName());
        event.setAddressLine(cmd.addressLine());
        event.setCity(cmd.city());
        event.setOnlineUrl(cmd.onlineUrl());
        event.setTimezone(cmd.timezone());
        event.setStartsAt(cmd.startsAt());
        event.setEndsAt(cmd.endsAt());
        event.setRegistrationOpensAt(cmd.registrationOpensAt());
        event.setRegistrationClosesAt(cmd.registrationClosesAt());
        event.setCapacity(cmd.capacity());
    }

    private void applyDescriptiveEdit(Event event, EventDraftCommand cmd) {
        // dates, venue, capacity and type stay locked once published
        if (cmd.title() != null) {
            event.setTitle(cmd.title().trim());
        }
        event.setDescription(cmd.description());
    }

    @Transactional(readOnly = true)
    public Event getOwnedEvent(UUID eventId, UUID organizerId) {
        return ownedEvent(eventId, organizerId);
    }

    @Transactional(readOnly = true)
    public List<Event> listOrganizerEvents(UUID organizerId, KeysetCursor.Position cursor, int pageSize) {
        var limit = Paging.fetchLimit(pageSize);
        if (cursor == null) {
            return eventRepository.findFirstOrganizerEvents(organizerId, limit);
        }
        return eventRepository.findOrganizerEventsAfter(organizerId, cursor.timestamp(), cursor.id(), limit);
    }

    @Transactional(readOnly = true)
    public List<TicketType> listTicketTypes(UUID eventId, UUID organizerId) {
        ownedEvent(eventId, organizerId); // ownership check
        return ticketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    // ---- lifecycle transitions ----

    @Transactional
    public Event submitForReview(UUID eventId, UUID organizerId, UUID actingUserId) {
        Event event = ownedEvent(eventId, organizerId);
        requireState(event, EventStatus.DRAFT, EventStatus.REJECTED);
        if (ticketTypeRepository.countByEventIdAndStatus(eventId, TicketTypeStatus.ACTIVE) < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, EventErrorCodes.PUBLICATION_RULES_FAILED,
                    "Add at least one active ticket type before submitting.");
        }
        event.markSubmitted(Instant.now(clock));
        auditService.record(AuditActions.EVENT_SUBMITTED, actingUserId, "EVENT", eventId, null);
        return event;
    }

    @Transactional
    public Event withdraw(UUID eventId, UUID organizerId) {
        Event event = ownedEvent(eventId, organizerId);
        requireState(event, EventStatus.PENDING_REVIEW);
        event.markWithdrawn();
        return event;
    }

    @Transactional
    public void cancelByOrganizer(UUID eventId, UUID organizerId, UUID actingUserId) {
        Event event = ownedEvent(eventId, organizerId);
        requireState(event, EventStatus.PUBLISHED, EventStatus.PENDING_REVIEW);
        event.markCancelled(Instant.now(clock));
        auditService.record(AuditActions.EVENT_CANCELLED, actingUserId, "EVENT", eventId, null);
    }

    @Transactional
    public void softDeleteDraft(UUID eventId, UUID organizerId) {
        Event event = ownedEvent(eventId, organizerId);
        requireState(event, EventStatus.DRAFT, EventStatus.REJECTED);
        event.markDeleted(Instant.now(clock));
    }

    // ---- admin review ----

    @Transactional
    public Event approve(UUID eventId, UUID adminUserId) {
        Event event = anyEvent(eventId);
        requireState(event, EventStatus.PENDING_REVIEW);
        event.markPublished(Instant.now(clock));
        auditService.record(AuditActions.EVENT_APPROVED, adminUserId, "EVENT", eventId, null);
        return event;
    }

    @Transactional
    public Event reject(UUID eventId, UUID adminUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, EventErrorCodes.REASON_REQUIRED,
                    "A reason is required to reject an event.");
        }
        Event event = anyEvent(eventId);
        requireState(event, EventStatus.PENDING_REVIEW);
        event.markRejected(reason.trim());
        auditService.record(AuditActions.EVENT_REJECTED, adminUserId, "EVENT", eventId, null);
        return event;
    }

    /** Marks published events past their end time as completed (invoked by a scheduler later). */
    @Transactional
    public int completePastEvents() {
        List<Event> due = eventRepository.findByStatusAndEndsAtBefore(EventStatus.PUBLISHED, Instant.now(clock));
        due.forEach(Event::markCompleted);
        return due.size();
    }

    // ---- ticket types (governed by the owning event) ----

    @Transactional
    public TicketType addTicketType(UUID eventId, UUID organizerId, TicketTypeCommand cmd) {
        Event event = ownedEvent(eventId, organizerId);
        requireTicketEditable(event);
        validateTicketWindow(cmd);
        TicketType ticketType = new TicketType(idGenerator.newId(), eventId, cmd.name().trim(), cmd.description(),
                cmd.price(), cmd.quantityTotal(), cmd.maxPerOrder(), cmd.salesStartAt(), cmd.salesEndAt());
        return ticketTypeRepository.save(ticketType);
    }

    @Transactional
    public TicketType updateTicketType(UUID eventId, UUID ticketTypeId, UUID organizerId, TicketTypeCommand cmd) {
        Event event = ownedEvent(eventId, organizerId);
        requireTicketEditable(event);
        TicketType ticketType = Ownership.require(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId));
        validateTicketWindow(cmd);

        if (ticketType.hasSales()) {
            // once sold: price is locked and total can only grow, never below what's already sold
            if (cmd.price().compareTo(ticketType.getPrice()) != 0) {
                throw new ApiException(HttpStatus.CONFLICT, EventErrorCodes.TICKET_TYPE_PRICE_LOCKED,
                        "Price cannot change after tickets have been sold.");
            }
            if (cmd.quantityTotal() < ticketType.getQuantityTotal()) {
                throw new ApiException(HttpStatus.CONFLICT, EventErrorCodes.TICKET_TYPE_QUANTITY_BELOW_SOLD,
                        "Total quantity can only be increased after sales have started.");
            }
        }
        ticketType.setName(cmd.name().trim());
        ticketType.setDescription(cmd.description());
        ticketType.setPrice(cmd.price());
        ticketType.setQuantityTotal(cmd.quantityTotal());
        ticketType.setMaxPerOrder(cmd.maxPerOrder());
        ticketType.setSalesStartAt(cmd.salesStartAt());
        ticketType.setSalesEndAt(cmd.salesEndAt());
        return ticketType;
    }

    // ---- helpers ----

    private Event ownedEvent(UUID eventId, UUID organizerId) {
        return Ownership.require(eventRepository.findByIdAndOrganizerIdAndDeletedAtIsNull(eventId, organizerId));
    }

    private Event anyEvent(UUID eventId) {
        return Ownership.require(eventRepository.findById(eventId));
    }

    private void requireCategory(UUID categoryId) {
        if (categoryId == null || !categoryRepository.existsById(categoryId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, EventErrorCodes.CATEGORY_NOT_FOUND,
                    "The selected category does not exist.");
        }
    }

    private void validateDetails(EventDraftCommand cmd) {
        if (!cmd.endsAt().isAfter(cmd.startsAt())) {
            throw datesInvalid("Event end must be after its start.");
        }
        if (cmd.registrationClosesAt().isAfter(cmd.startsAt())) {
            throw datesInvalid("Registration must close on or before the event start.");
        }
        if (cmd.registrationOpensAt().isAfter(cmd.registrationClosesAt())) {
            throw datesInvalid("Registration must open before it closes.");
        }
        if (cmd.capacity() <= 0) {
            throw datesInvalid("Capacity must be greater than zero.");
        }
        if (cmd.eventType() == EventType.PHYSICAL && (cmd.venueName() == null || cmd.venueName().isBlank())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, EventErrorCodes.VENUE_REQUIRED,
                    "A venue name is required for physical events.");
        }
    }

    private void validateTicketWindow(TicketTypeCommand cmd) {
        if (!cmd.salesEndAt().isAfter(cmd.salesStartAt())) {
            throw datesInvalid("Ticket sales end must be after the sales start.");
        }
    }

    private void requireTicketEditable(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw notEditable();
        }
    }

    private void requireState(Event event, EventStatus... allowed) {
        for (EventStatus status : allowed) {
            if (event.getStatus() == status) {
                return;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, EventErrorCodes.INVALID_STATE_TRANSITION,
                "This action is not allowed for an event in state " + event.getStatus() + ".");
    }

    private ApiException datesInvalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, EventErrorCodes.EVENT_DATES_INVALID, message);
    }

    private ApiException notEditable() {
        return new ApiException(HttpStatus.CONFLICT, EventErrorCodes.EVENT_NOT_EDITABLE,
                "This event can no longer be edited in its current state.");
    }
}
