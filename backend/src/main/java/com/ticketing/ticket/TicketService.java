package com.ticketing.ticket;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final TicketQrRenderer qrRenderer;
    private final TicketPdfRenderer pdfRenderer;

    TicketService(TicketRepository tickets, EventRepository events, TicketTypeRepository ticketTypes,
            TicketQrRenderer qrRenderer, TicketPdfRenderer pdfRenderer) {
        this.tickets = tickets;
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.qrRenderer = qrRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional(readOnly = true)
    public List<TicketView> listOwnedTickets(UUID ownerUserId, KeysetCursor.Position cursor, int pageSize) {
        Limit limit = Paging.fetchLimit(pageSize);
        List<Ticket> rows = cursor == null
                ? tickets.findFirstOwnerTickets(ownerUserId, limit)
                : tickets.findOwnerTicketsAfter(ownerUserId, cursor.timestamp(), cursor.id(), limit);
        return withContext(rows);
    }

    @Transactional(readOnly = true)
    public TicketView getOwnedTicket(UUID ticketId, UUID ownerUserId) {
        Ticket ticket = tickets.findByIdAndOwnerUserId(ticketId, ownerUserId)
                .orElseThrow(ResourceNotFoundException::new);
        return withContext(List.of(ticket)).get(0);
    }

    /** The scannable image for a ticket, rendered on demand and only for its owner. */
    @Transactional(readOnly = true)
    public byte[] renderQr(UUID ticketId, UUID ownerUserId) {
        Ticket ticket = tickets.findByIdAndOwnerUserId(ticketId, ownerUserId)
                .orElseThrow(ResourceNotFoundException::new);
        return qrRenderer.renderPng(ticket.getId());
    }

    /** The printable ticket, built on demand and only for its owner. */
    @Transactional(readOnly = true)
    public byte[] renderPdf(UUID ticketId, UUID ownerUserId) {
        return pdfRenderer.render(getOwnedTicket(ticketId, ownerUserId));
    }

    // events and types are fetched in one batch each, so page size never drives the query count
    private List<TicketView> withContext(List<Ticket> rows) {
        Map<UUID, Event> eventsById = byId(events.findAllById(distinct(rows, Ticket::getEventId)), Event::getId);
        Map<UUID, TicketType> typesById =
                byId(ticketTypes.findAllById(distinct(rows, Ticket::getTicketTypeId)), TicketType::getId);
        return rows.stream()
                .map(ticket -> new TicketView(ticket,
                        eventsById.get(ticket.getEventId()),
                        typesById.get(ticket.getTicketTypeId())))
                .toList();
    }

    private Set<UUID> distinct(List<Ticket> rows, Function<Ticket, UUID> idOf) {
        return rows.stream().map(idOf).collect(Collectors.toSet());
    }

    private <T> Map<UUID, T> byId(List<T> rows, Function<T, UUID> idOf) {
        return rows.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}
