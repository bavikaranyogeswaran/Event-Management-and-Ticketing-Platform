package com.ticketing.organizer.dto;

import java.util.UUID;

import com.ticketing.organizer.OrganizerProfile;

public record OrganizerProfileResponse(
        UUID id,
        String orgName,
        String description,
        String contactEmail,
        String status,
        UUID imageFileId,
        String logoUrl) {

    public static OrganizerProfileResponse from(OrganizerProfile profile, String logoUrl) {
        return new OrganizerProfileResponse(profile.getId(), profile.getOrgName(), profile.getDescription(),
                profile.getContactEmail(), profile.getStatus().name(), profile.getImageFileId(), logoUrl);
    }
}
