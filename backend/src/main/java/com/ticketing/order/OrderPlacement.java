package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketIssueCommand;
import com.ticketing.ticket.TicketIssuer;
import com.ticketing.ticket.TicketRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;

/** The transactional half of placing an order. */
@Service
class OrderPlacement {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final TicketRepository tickets;
    private final OrderValidator validator;
    private final OrderNumberGenerator orderNumbers;
    private final TicketIssuer ticketIssuer;
    private final OutboxJobService outbox;
    private final IdGenerator idGenerator;
    private final Clock clock;

    OrderPlacement(OrderRepository orders, OrderItemRepository orderItems, EventRepository events,
            TicketTypeRepository ticketTypes, TicketRepository tickets, OrderValidator validator,
            OrderNumberGenerator orderNumbers, TicketIssuer ticketIssuer, OutboxJobService outbox,
            IdGenerator idGenerator, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.tickets = tickets;
        this.validator = validator;
        this.orderNumbers = orderNumbers;
        this.ticketIssuer = ticketIssuer;
        this.outbox = outbox;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Inventory, order, items, tickets and the confirmation job all commit together;
     * any failure rolls back the reservation with everything else.
     */
    @Transactional
    OrderResult create(UUID buyerId, String idempotencyKey, String fingerprint, OrderCommand command) {
        Event event = events.findById(command.eventId()).orElseThrow(ResourceNotFoundException::new);
        Map<UUID, TicketType> catalogue = ticketTypes.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
        Instant now = Instant.now(clock);

        PricedOrder priced = validator.validate(event, catalogue, command, now);
        claimInventory(priced);

        Order order = new Order(idGenerator.newId(), orderNumbers.next(), buyerId, event.getId(),
                priced.subtotal(), priced.fees(), priced.grandTotal(), idempotencyKey, fingerprint);
        order.confirm(now);
        orders.saveAndFlush(order);

        List<OrderItem> items = new ArrayList<>();
        List<Ticket> issued = new ArrayList<>();
        Iterator<String> attendees = command.attendees().iterator();
        for (PricedLine line : priced.lines()) {
            OrderItem item = orderItems.saveAndFlush(new OrderItem(idGenerator.newId(), order.getId(),
                    line.ticketType().getId(), line.ticketType().getName(),
                    line.unitPrice(), line.quantity(), line.lineTotal()));
            items.add(item);
            issued.addAll(ticketIssuer.issue(new TicketIssueCommand(order.getId(), item.getId(),
                    event.getId(), line.ticketType().getId(), buyerId,
                    namesFor(attendees, line.quantity()), now)));
        }

        outbox.enqueue(JobTypes.EMAIL, JobTypes.orderConfirmationKey(order.getId()),
                new OrderConfirmationJob(order.getId(), order.getOrderNumber(), buyerId,
                        event.getId(), issued.size()));

        return new OrderResult(order, items, issued, false);
    }

    @Transactional(readOnly = true)
    OrderResult load(Order order, boolean replay) {
        return new OrderResult(order,
                orderItems.findByOrderIdOrderByCreatedAtAsc(order.getId()),
                tickets.findByOrderIdOrderByIssuedAtAsc(order.getId()),
                replay);
    }

    private void claimInventory(PricedOrder priced) {
        for (PricedLine line : priced.lines()) {
            if (ticketTypes.reserve(line.ticketType().getId(), line.quantity()) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.TICKET_INVENTORY_EXHAUSTED,
                        "There are not enough %s tickets left.".formatted(line.ticketType().getName()));
            }
        }
    }

    // names are handed out in the order they were sent, one per ticket
    private List<String> namesFor(Iterator<String> attendees, int quantity) {
        List<String> names = new ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            names.add(attendees.next());
        }
        return names;
    }
}
