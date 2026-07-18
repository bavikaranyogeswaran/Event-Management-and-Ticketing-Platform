package com.ticketing.ticket;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.shared.jpa.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends AuditableEntity {

    @Id
    private UUID id;

    // short human-enterable code, used when a QR will not scan
    @Column(name = "public_code", nullable = false)
    private String publicCode;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "attendee_name", nullable = false)
    private String attendeeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    // only the hash is stored; the raw token exists solely inside the QR
    @Column(name = "validation_token_hash", nullable = false)
    private String validationTokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    public Ticket(UUID id, String publicCode, UUID orderId, UUID orderItemId, UUID eventId,
            UUID ticketTypeId, UUID ownerUserId, String attendeeName,
            String validationTokenHash, Instant issuedAt) {
        this.id = id;
        this.publicCode = publicCode;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
        this.ownerUserId = ownerUserId;
        this.attendeeName = attendeeName;
        this.validationTokenHash = validationTokenHash;
        this.issuedAt = issuedAt;
    }
}
