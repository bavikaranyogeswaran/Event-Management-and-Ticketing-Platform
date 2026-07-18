package com.ticketing.order;

import java.math.BigDecimal;
import java.util.List;

/** Server-computed totals for a validated basket. */
record PricedOrder(List<PricedLine> lines, BigDecimal subtotal, BigDecimal fees, BigDecimal grandTotal) {

    int ticketCount() {
        return lines.stream().mapToInt(PricedLine::quantity).sum();
    }
}
