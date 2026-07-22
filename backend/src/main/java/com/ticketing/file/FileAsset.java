package com.ticketing.file;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.shared.jpa.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Metadata for a file stored in Cloudinary; the bytes live there, only the reference lives here. */
@Entity
@Table(name = "file_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileAsset extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    // set for an event banner; null for a profile image or export
    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FilePurpose purpose;

    // random provider key, never the original filename
    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(nullable = false)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status = FileStatus.PENDING;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public FileAsset(UUID id, UUID ownerUserId, UUID eventId, FilePurpose purpose,
            String publicId, String mime, long sizeBytes) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.eventId = eventId;
        this.purpose = purpose;
        this.publicId = publicId;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
    }

    /** The upload landed and was verified; store the confirmed type and size. */
    public void markReady(String verifiedMime, long verifiedSizeBytes) {
        this.mime = verifiedMime;
        this.sizeBytes = verifiedSizeBytes;
        this.status = FileStatus.READY;
    }

    /** No longer referenced; kept only until the provider copy is destroyed. */
    public void markDeleted(Instant now) {
        this.status = FileStatus.DELETED;
        this.deletedAt = now;
    }

    public boolean isReady() {
        return status == FileStatus.READY;
    }
}
