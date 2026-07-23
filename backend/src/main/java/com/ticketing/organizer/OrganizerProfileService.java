package com.ticketing.organizer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.file.FileService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

@Service
public class OrganizerProfileService {

    private final OrganizerProfileRepository organizerProfileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final FileService fileService;
    private final IdGenerator idGenerator;

    OrganizerProfileService(OrganizerProfileRepository organizerProfileRepository, UserRepository userRepository,
            AuditService auditService, FileService fileService, IdGenerator idGenerator) {
        this.organizerProfileRepository = organizerProfileRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.fileService = fileService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public OrganizerProfile create(UUID userId, String orgName, String description, String contactEmail) {
        if (organizerProfileRepository.existsByUserId(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, OrganizerErrorCodes.ORGANIZER_PROFILE_EXISTS,
                    "You already have an organizer profile.");
        }

        OrganizerProfile profile = new OrganizerProfile(idGenerator.newId(), userId, orgName.trim(),
                trimToNull(description), trimToNull(contactEmail));
        organizerProfileRepository.save(profile);

        User user = userRepository.findById(userId).orElseThrow(ResourceNotFoundException::new);
        user.addRole(Role.ORGANIZER);

        auditService.record(AuditActions.ORGANIZER_PROFILE_CREATED, userId, null);
        return profile;
    }

    @Transactional(readOnly = true)
    public OrganizerProfile getByUser(UUID userId) {
        return organizerProfileRepository.findByUserId(userId).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public OrganizerProfile update(UUID userId, String orgName, String description, String contactEmail) {
        OrganizerProfile profile = organizerProfileRepository.findByUserId(userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (orgName != null) {
            profile.setOrgName(orgName.trim());
        }
        if (description != null) {
            profile.setDescription(trimToNull(description));
        }
        if (contactEmail != null) {
            profile.setContactEmail(trimToNull(contactEmail));
        }
        return profile;
    }

    /** Points the profile at a ready logo image the caller uploaded, replacing any earlier one. */
    @Transactional
    public OrganizerProfile setLogo(UUID userId, UUID fileId) {
        OrganizerProfile profile = organizerProfileRepository.findByUserId(userId)
                .orElseThrow(ResourceNotFoundException::new);
        fileService.confirmProfileImage(userId, fileId);
        profile.setImageFileId(fileId);
        return profile;
    }

    @Transactional
    public OrganizerProfile clearLogo(UUID userId) {
        OrganizerProfile profile = organizerProfileRepository.findByUserId(userId)
                .orElseThrow(ResourceNotFoundException::new);
        profile.setImageFileId(null);
        return profile;
    }

    /** The logo's public URL for a response, or null when the profile has none to show. */
    @Transactional(readOnly = true)
    public String logoUrl(UUID imageFileId) {
        return fileService.imageUrl(imageFileId).orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
