package com.ticketing.ticket;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import com.ticketing.event.Event;

/**
 * Draws the printable ticket. Everything on it is either public or already known to the
 * holder; the QR carries the only value that matters at the door.
 */
@Component
class TicketPdfRenderer {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font CODE = FontFactory.getFont(FontFactory.COURIER_BOLD, 16);

    private final TicketQrRenderer qrRenderer;

    TicketPdfRenderer(TicketQrRenderer qrRenderer) {
        this.qrRenderer = qrRenderer;
    }

    byte[] render(TicketView view) {
        Ticket ticket = view.ticket();
        Event event = view.event();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(event.getTitle(), TITLE));
            document.add(spacer());
            document.add(new Paragraph(whenAndWhere(event), BODY));
            document.add(spacer());

            document.add(new Paragraph("Admits", HEADING));
            document.add(new Paragraph(ticket.getAttendeeName(), BODY));
            document.add(new Paragraph(view.ticketType().getName(), BODY));
            document.add(spacer());

            document.add(qrImage(ticket));
            document.add(spacer());

            document.add(new Paragraph(ticket.getPublicCode(), CODE));
            // the readable code is the way in when a screen is too cracked or dim to scan
            document.add(new Paragraph("Show this code if the QR will not scan.", BODY));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render the ticket PDF", e);
        }
    }

    private String whenAndWhere(Event event) {
        String when = WHEN.format(event.getStartsAt().atZone(ZoneId.of(event.getTimezone())));
        if (event.getVenueName() == null) {
            return when;
        }
        String where = event.getCity() == null
                ? event.getVenueName()
                : event.getVenueName() + ", " + event.getCity();
        return when + "\n" + where;
    }

    private Image qrImage(Ticket ticket) {
        try {
            Image qr = Image.getInstance(qrRenderer.renderPng(ticket.getId()));
            qr.setAlignment(Element.ALIGN_LEFT);
            return qr;
        } catch (Exception e) {
            throw new IllegalStateException("Could not place the ticket QR", e);
        }
    }

    private Paragraph spacer() {
        return new Paragraph(" ", BODY);
    }
}
