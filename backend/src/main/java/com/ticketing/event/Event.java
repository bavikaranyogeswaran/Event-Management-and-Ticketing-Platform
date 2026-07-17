package com.ticketing.event;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Event extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Setter
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String slug; // generated once at creation, never changed

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Setter
    @Column(name = "venue_name")
    private String venueName;

    @Setter
    @Column(name = "address_line")
    private String addressLine;

    @Setter
    private String city;

    @Setter
    @Column(name = "online_url")
    private String onlineUrl;

    @Setter
    @Column(nullable = false)
    private String timezone;

    @Setter
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Setter
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Setter
    @Column(name = "registration_opens_at", nullable = false)
    private Instant registrationOpensAt;

    @Setter
    @Column(name = "registration_closes_at", nullable = false)
    private Instant registrationClosesAt;

    @Setter
    @Column(nullable = false)
    private int capacity;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Setter
    @Column(name = "banner_file_id")
    private UUID bannerFileId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    // --- lifecycle transitions; the service checks the source state before calling these ---

    public void markSubmitted(Instant now) {
        this.status = EventStatus.PENDING_REVIEW;
        this.submittedAt = now;
    }

    public void markWithdrawn() {
        this.status = EventStatus.DRAFT;
    }

    public void markPublished(Instant now) {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void markRejected(String reason) {
        this.status = EventStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void markCancelled(Instant now) {
        this.status = EventStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void markCompleted() {
        this.status = EventStatus.COMPLETED;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public boolean isPublished() {
        return status == EventStatus.PUBLISHED;
    }
}
