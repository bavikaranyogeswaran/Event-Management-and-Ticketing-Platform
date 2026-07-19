package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Returns seats from checkouts that were never completed. */
@Component
class OrderExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirySweeper.class);

    private final OrderRepository orders;
    private final OrderExpiry orderExpiry;
    private final Clock clock;
    private final int batchSize;

    OrderExpirySweeper(OrderRepository orders, OrderExpiry orderExpiry, Clock clock,
            AppProperties properties) {
        this.orders = orders;
        this.orderExpiry = orderExpiry;
        this.clock = clock;
        this.batchSize = properties.order().expiryBatchSize();
    }

    @Scheduled(fixedDelayString = "${app.order.expiry-sweep-interval}")
    void scheduledSweep() {
        sweepOnce();
    }

    /** Expires one batch of overdue holds; running it again is always safe. */
    int sweepOnce() {
        List<Order> due = orders.findDueForExpiry(Instant.now(clock), Limit.of(batchSize));
        int expired = 0;
        for (Order order : due) {
            // each order stands alone, so one failure cannot strand the rest of the batch
            if (orderExpiry.expire(order.getId())) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid order(s) and returned their seats", expired);
        }
        if (due.size() == batchSize) {
            log.warn("Expiry batch was full; more overdue orders wait for the next run");
        }
        return expired;
    }
}
