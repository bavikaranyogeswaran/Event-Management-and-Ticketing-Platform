package com.ticketing.order;

import java.util.UUID;

public record OrderLine(UUID ticketTypeId, int quantity) {
}
