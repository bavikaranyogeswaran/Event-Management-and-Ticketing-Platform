package com.ticketing.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.ticket.dto.TicketResponse;

@RestController
class TicketController {

    private static final int DEFAULT_PAGE = 20;
    private static final int MAX_PAGE = 50;

    private final TicketService ticketService;

    TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/users/me/tickets")
    PageResponse<TicketResponse> myTickets(CurrentUser currentUser,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        List<TicketView> rows = ticketService.listOwnedTickets(
                currentUser.userId(), KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize,
                        view -> KeysetCursor.encode(view.ticket().getIssuedAt(), view.ticket().getId()))
                .map(TicketResponse::from);
    }

    @GetMapping("/tickets/{ticketId}")
    TicketResponse get(CurrentUser currentUser, @PathVariable UUID ticketId) {
        return TicketResponse.from(ticketService.getOwnedTicket(ticketId, currentUser.userId()));
    }

    @GetMapping(value = "/tickets/{ticketId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> qr(CurrentUser currentUser, @PathVariable UUID ticketId) {
        byte[] png = ticketService.renderQr(ticketId, currentUser.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // the image is as good as the ticket, so it must not linger in a shared cache
                .cacheControl(CacheControl.noStore())
                .body(png);
    }
}
