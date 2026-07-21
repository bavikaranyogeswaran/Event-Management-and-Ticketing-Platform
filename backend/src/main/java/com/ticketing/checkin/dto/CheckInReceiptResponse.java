package com.ticketing.checkin.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.checkin.CheckInReceipt;

public record CheckInReceiptResponse(UUID ticketId, String attendeeName, Instant checkedInAt, String method) {

    public static CheckInReceiptResponse from(CheckInReceipt receipt) {
        return new CheckInReceiptResponse(receipt.ticketId(), receipt.attendeeName(),
                receipt.checkedInAt(), receipt.method().name());
    }
}
