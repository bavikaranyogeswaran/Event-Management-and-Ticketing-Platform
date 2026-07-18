package com.ticketing.ticket;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Builds the short code printed on a ticket, for when a QR will not scan and staff type it instead. */
@Component
class TicketCodeGenerator {

    // omits 0/O and 1/I/L so a code read aloud or typed by hand stays unambiguous
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private final String prefix;
    private final int groups;
    private final int groupLength;

    TicketCodeGenerator(AppProperties properties) {
        AppProperties.Ticket config = properties.ticket();
        this.prefix = config.codePrefix();
        this.groups = config.codeGroups();
        this.groupLength = config.codeGroupLength();
    }

    String next() {
        String body = IntStream.range(0, groups)
                .mapToObj(group -> randomGroup())
                .collect(Collectors.joining("-"));
        return prefix + "-" + body;
    }

    private String randomGroup() {
        StringBuilder group = new StringBuilder(groupLength);
        for (int i = 0; i < groupLength; i++) {
            group.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return group.toString();
    }
}
