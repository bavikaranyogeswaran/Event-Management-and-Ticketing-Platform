package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.event.Event;
import com.ticketing.event.EventStatus;
import com.ticketing.event.EventType;
import com.ticketing.shared.api.ApiException;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderValidatorTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TYPE_ID = UUID.randomUUID();
    private static final UUID OTHER_TYPE_ID = UUID.randomUUID();

    private final OrderValidator validator = new OrderValidator();

    private Event event(EventStatus status, Instant opens, Instant closes) {
        return Event.builder()
                .id(EVENT_ID)
                .organizerId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .slug("test-event")
                .title("Test Event")
                .eventType(EventType.PHYSICAL)
                .venueName("Trace Expert City")
                .city("Colombo")
                .timezone("Asia/Colombo")
                .startsAt(NOW.plus(30, ChronoUnit.DAYS))
                .endsAt(NOW.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .registrationOpensAt(opens)
                .registrationClosesAt(closes)
                .capacity(500)
                .status(status)
                .build();
    }

    private Event openEvent() {
        return event(EventStatus.PUBLISHED, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
    }

    private TicketType ticketType(BigDecimal price, int maxPerOrder, TicketTypeStatus status,
            Instant salesStart, Instant salesEnd) {
        TicketType type = new TicketType(TYPE_ID, EVENT_ID, "General", null, price,
                100, maxPerOrder, salesStart, salesEnd);
        type.setStatus(status);
        return type;
    }

    private TicketType freeTicketType() {
        return ticketType(new BigDecimal("0.00"), 4, TicketTypeStatus.ACTIVE,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
    }

    private Map<UUID, TicketType> catalogue(TicketType type) {
        return Map.of(type.getId(), type);
    }

    private OrderCommand order(int quantity, List<String> attendees) {
        return new OrderCommand(EVENT_ID, List.of(new OrderLine(TYPE_ID, quantity)), attendees);
    }

    private String codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    @Test
    void validFreeOrderIsPricedAtZero() {
        PricedOrder priced = validator.validate(openEvent(), catalogue(freeTicketType()),
                order(2, List.of("Asha", "Nuwan")), NOW);

        assertThat(priced.ticketCount()).isEqualTo(2);
        assertThat(priced.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(priced.lines()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(2));
    }

    @Test
    void emptyBasketIsRejected() {
        OrderCommand empty = new OrderCommand(EVENT_ID, List.of(), List.of());
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()), empty, NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void zeroQuantityIsRejected() {
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()),
                order(0, List.of()), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void repeatingTheSameTicketTypeIsRejected() {
        OrderCommand duplicated = new OrderCommand(EVENT_ID,
                List.of(new OrderLine(TYPE_ID, 1), new OrderLine(TYPE_ID, 1)), List.of("Asha", "Nuwan"));
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()), duplicated, NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void unpublishedEventIsNotOnSale() {
        Event draft = event(EventStatus.DRAFT, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(draft, catalogue(freeTicketType()),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("EVENT_NOT_ON_SALE"));
    }

    @Test
    void cancelledEventIsNotOnSale() {
        Event cancelled = event(EventStatus.CANCELLED, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(cancelled, catalogue(freeTicketType()),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("EVENT_NOT_ON_SALE"));
    }

    @Test
    void registrationNotYetOpenIsRejected() {
        Event notOpen = event(EventStatus.PUBLISHED, NOW.plus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(notOpen, catalogue(freeTicketType()),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("EVENT_NOT_ON_SALE"));
    }

    @Test
    void registrationAlreadyClosedIsRejected() {
        Event closed = event(EventStatus.PUBLISHED, NOW.minus(10, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(closed, catalogue(freeTicketType()),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("EVENT_NOT_ON_SALE"));
    }

    @Test
    void unknownTicketTypeIsNotAvailable() {
        OrderCommand foreignType = new OrderCommand(EVENT_ID,
                List.of(new OrderLine(OTHER_TYPE_ID, 1)), List.of("Asha"));
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()), foreignType, NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_TYPE_NOT_AVAILABLE"));
    }

    @Test
    void inactiveTicketTypeIsNotAvailable() {
        TicketType inactive = ticketType(new BigDecimal("0.00"), 4, TicketTypeStatus.INACTIVE,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(inactive),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_TYPE_NOT_AVAILABLE"));
    }

    @Test
    void ticketTypeOutsideItsSalesWindowIsNotAvailable() {
        TicketType ended = ticketType(new BigDecimal("0.00"), 4, TicketTypeStatus.ACTIVE,
                NOW.minus(10, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(ended),
                order(1, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_TYPE_NOT_AVAILABLE"));
    }

    @Test
    void exceedingMaxPerOrderIsRejected() {
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()),
                order(5, List.of("A", "B", "C", "D", "E")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("ORDER_LIMIT_EXCEEDED"));
    }

    @Test
    void orderingExactlyMaxPerOrderIsAllowed() {
        assertThatCode(() -> validator.validate(openEvent(), catalogue(freeTicketType()),
                order(4, List.of("A", "B", "C", "D")), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void attendeeCountMustMatchTicketCount() {
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()),
                order(2, List.of("Asha")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void blankAttendeeNameIsRejected() {
        assertThatThrownBy(() -> validator.validate(openEvent(), catalogue(freeTicketType()),
                order(2, List.of("Asha", "  ")), NOW))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void paidTicketsArePricedFromTheStoredPrice() {
        TicketType paid = ticketType(new BigDecimal("1500.00"), 4, TicketTypeStatus.ACTIVE,
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(10, ChronoUnit.DAYS));

        PricedOrder priced = validator.validate(openEvent(), catalogue(paid),
                order(2, List.of("Asha", "Nuwan")), NOW);

        assertThat(priced.subtotal()).isEqualByComparingTo("3000.00");
        assertThat(priced.grandTotal()).isEqualByComparingTo("3000.00");
        assertThat(priced.lines()).singleElement()
                .satisfies(line -> assertThat(line.unitPrice()).isEqualByComparingTo("1500.00"));
    }
}
