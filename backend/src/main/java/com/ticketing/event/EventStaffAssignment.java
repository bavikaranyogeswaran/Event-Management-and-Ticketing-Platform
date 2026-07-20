package com.ticketing.event;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Permission to check tickets in at one event, and nothing beyond that event. */
@Entity
@Table(name = "event_staff_assignments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventStaffAssignment {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // kept for the audit trail: who put this person on the door
    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EventStaffAssignment(UUID id, UUID eventId, UUID userId, UUID assignedBy) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.assignedBy = assignedBy;
    }
}
