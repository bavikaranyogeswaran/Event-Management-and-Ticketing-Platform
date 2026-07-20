package com.ticketing.ticket;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.Event;
import com.ticketing.event.EventStatus;
import com.ticketing.event.EventType;
import com.ticketing.tickettype.TicketType;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads the rendered document back, so the ticket is checked by its text rather than its size. */
class TicketPdfRendererTest extends AbstractIntegrationTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TYPE_ID = UUID.randomUUID();

    @Autowired
    TicketPdfRenderer renderer;

    private TicketView view;

    @BeforeEach
    void setUp() {
        Instant starts = Instant.parse("2026-08-20T12:30:00Z");
        Event event = Event.builder()
                .id(EVENT_ID)
                .organizerId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .slug("colombo-jazz-night")
                .title("Colombo Jazz Night")
                .eventType(EventType.PHYSICAL)
                .venueName("Trace Expert City")
                .city("Colombo")
                .timezone("Asia/Colombo")
                .startsAt(starts)
                .endsAt(starts.plus(3, ChronoUnit.HOURS))
                .registrationOpensAt(starts.minus(30, ChronoUnit.DAYS))
                .registrationClosesAt(starts.minus(1, ChronoUnit.DAYS))
                .capacity(150)
                .status(EventStatus.PUBLISHED)
                .build();

        TicketType type = new TicketType(TYPE_ID, EVENT_ID, "Balcony", null, new BigDecimal("1500.00"),
                100, 4, starts.minus(30, ChronoUnit.DAYS), starts.minus(1, ChronoUnit.DAYS));

        Ticket ticket = new Ticket(UUID.randomUUID(), "TCK-7F3K-9Q2M", UUID.randomUUID(), UUID.randomUUID(),
                EVENT_ID, TYPE_ID, UUID.randomUUID(), "Asha Perera", "hash", Instant.now());

        view = new TicketView(ticket, event, type);
    }

    private String textOf(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void theOutputIsARealPdf() throws Exception {
        byte[] pdf = renderer.render(view);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void theTicketNamesTheEventAndTheAttendee() throws Exception {
        String text = textOf(renderer.render(view));

        assertThat(text).contains("Colombo Jazz Night");
        assertThat(text).contains("Asha Perera");
        assertThat(text).contains("Balcony");
    }

    @Test
    void theReadableCodeIsPrintedForWhenScanningFails() throws Exception {
        String text = textOf(renderer.render(view));

        assertThat(text).contains("TCK-7F3K-9Q2M");
    }

    @Test
    void theTimeIsShownInTheEventsOwnZone() throws Exception {
        // 12:30 UTC is 6:00 PM in Colombo; printing UTC would send people at the wrong hour
        String text = textOf(renderer.render(view));

        assertThat(text).contains("6:00 PM");
        assertThat(text).contains("20 August 2026");
    }

    @Test
    void theVenueIsShown() throws Exception {
        String text = textOf(renderer.render(view));

        assertThat(text).contains("Trace Expert City");
        assertThat(text).contains("Colombo");
    }

    @Test
    void theStoredTokenHashNeverAppearsOnThePage() throws Exception {
        String text = textOf(renderer.render(view));

        // the QR carries the token; nothing about it belongs in readable text
        assertThat(text).doesNotContain("hash");
    }
}
