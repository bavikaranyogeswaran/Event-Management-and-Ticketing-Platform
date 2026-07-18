package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ticketing.event.Event;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeStatus;

/** Checks a basket against the event, its ticket types, and the sales rules, then prices it. */
@Component
class OrderValidator {

    PricedOrder validate(Event event, Map<UUID, TicketType> ticketTypesById, OrderCommand command, Instant now) {
        requireBasket(command);
        requireEventOnSale(event, now);

        List<PricedLine> lines = new ArrayList<>();
        for (OrderLine item : command.items()) {
            TicketType ticketType = requirePurchasable(ticketTypesById.get(item.ticketTypeId()), now);
            requireWithinOrderLimit(item, ticketType);
            BigDecimal unitPrice = ticketType.getPrice();
            lines.add(new PricedLine(ticketType, item.quantity(), unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(item.quantity()))));
        }

        BigDecimal subtotal = lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fees = BigDecimal.ZERO; // the current pricing model adds no platform fee
        PricedOrder priced = new PricedOrder(lines, subtotal, fees, subtotal.add(fees));

        requireOneAttendeePerTicket(command, priced.ticketCount());
        requireNothingToPay(priced);
        return priced;
    }

    private void requireBasket(OrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw invalid("An order needs at least one ticket.");
        }
        if (command.items().stream().anyMatch(item -> item.quantity() <= 0)) {
            throw invalid("Ticket quantity must be at least one.");
        }
        long distinctTypes = command.items().stream().map(OrderLine::ticketTypeId).distinct().count();
        if (distinctTypes != command.items().size()) {
            throw invalid("Each ticket type may appear only once; combine the quantities instead.");
        }
    }

    private void requireEventOnSale(Event event, Instant now) {
        boolean onSale = event.isPublished()
                && event.getDeletedAt() == null
                && !now.isBefore(event.getRegistrationOpensAt())
                && !now.isAfter(event.getRegistrationClosesAt());
        if (!onSale) {
            throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.EVENT_NOT_ON_SALE,
                    "This event is not open for registration.");
        }
    }

    // an unknown type gets the same answer as an unavailable one, so buyers cannot probe for ids
    private TicketType requirePurchasable(TicketType ticketType, Instant now) {
        boolean purchasable = ticketType != null
                && ticketType.getStatus() == TicketTypeStatus.ACTIVE
                && !now.isBefore(ticketType.getSalesStartAt())
                && !now.isAfter(ticketType.getSalesEndAt());
        if (!purchasable) {
            throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.TICKET_TYPE_NOT_AVAILABLE,
                    "That ticket type is not on sale.");
        }
        return ticketType;
    }

    private void requireWithinOrderLimit(OrderLine item, TicketType ticketType) {
        if (item.quantity() > ticketType.getMaxPerOrder()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, OrderErrorCodes.ORDER_LIMIT_EXCEEDED,
                    "You can order at most %d of %s at a time."
                            .formatted(ticketType.getMaxPerOrder(), ticketType.getName()));
        }
    }

    private void requireOneAttendeePerTicket(OrderCommand command, int ticketCount) {
        if (command.attendees() == null || command.attendees().size() != ticketCount) {
            throw invalid("Provide exactly one attendee name per ticket.");
        }
        if (command.attendees().stream().anyMatch(name -> name == null || name.isBlank())) {
            throw invalid("Every attendee needs a name.");
        }
    }

    private void requireNothingToPay(PricedOrder priced) {
        if (priced.grandTotal().signum() != 0) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, OrderErrorCodes.PAYMENTS_NOT_ENABLED,
                    "Paid ticketing is not available yet; only free tickets can be ordered.");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, message);
    }
}
