package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Builds the order number an attendee quotes in support requests. */
@Component
class OrderNumberGenerator {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String prefix;
    private final int padding;
    private final ZoneId zone;

    OrderNumberGenerator(JdbcTemplate jdbc, Clock clock, AppProperties properties) {
        AppProperties.Order config = properties.order();
        this.jdbc = jdbc;
        this.clock = clock;
        this.prefix = config.numberPrefix();
        this.padding = config.numberPadding();
        this.zone = ZoneId.of(config.numberZone()); // fails at startup if the configured zone is invalid
    }

    String next() {
        Long sequence = jdbc.queryForObject("SELECT nextval('order_number_seq')", Long.class);
        int year = Instant.now(clock).atZone(zone).getYear();
        return String.format("%s-%d-%0" + padding + "d", prefix, year, sequence);
    }
}
