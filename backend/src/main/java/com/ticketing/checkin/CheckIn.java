package com.ticketing.checkin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One admission of one ticket. The unique ticket_id makes a second one impossible. */
@Entity
@Table(name = "check_ins")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    // denormalized onto the row so attendance queries never join back to tickets
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInMethod method;

    @Column(name = "device_ref")
    private String deviceRef;

    @CreatedDate
    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    public CheckIn(UUID id, UUID ticketId, UUID eventId, UUID staffUserId, CheckInMethod method) {
        this.id = id;
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.staffUserId = staffUserId;
        this.method = method;
    }
}
