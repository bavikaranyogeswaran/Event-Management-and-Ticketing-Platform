package com.ticketing.organizer;

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
import lombok.Setter;

@Entity
@Table(name = "organizer_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizerProfile extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Setter
    @Column(name = "org_name", nullable = false)
    private String orgName;

    @Setter
    private String description;

    @Setter
    @Column(name = "contact_email")
    private String contactEmail;

    @Setter
    @Column(name = "image_file_id")
    private UUID imageFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizerStatus status = OrganizerStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    public OrganizerProfile(UUID id, UUID userId, String orgName, String description, String contactEmail) {
        this.id = id;
        this.userId = userId;
        this.orgName = orgName;
        this.description = description;
        this.contactEmail = contactEmail;
    }
}
