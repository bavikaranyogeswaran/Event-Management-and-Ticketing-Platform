package com.ticketing.organizer;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.auth.SessionRefreshService;
import com.ticketing.organizer.dto.CreateOrganizerProfileRequest;
import com.ticketing.organizer.dto.OrganizerProfileResponse;
import com.ticketing.organizer.dto.SetLogoRequest;
import com.ticketing.organizer.dto.UpdateOrganizerProfileRequest;
import com.ticketing.shared.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/organizer/profile")
class OrganizerProfileController {

    private final OrganizerProfileService organizerProfileService;
    private final SessionRefreshService sessionRefreshService;

    OrganizerProfileController(OrganizerProfileService organizerProfileService,
            SessionRefreshService sessionRefreshService) {
        this.organizerProfileService = organizerProfileService;
        this.sessionRefreshService = sessionRefreshService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizerProfileResponse create(CurrentUser currentUser,
            @Valid @RequestBody CreateOrganizerProfileRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        currentUser.requireVerifiedEmail();
        OrganizerProfile profile = organizerProfileService.create(
                currentUser.userId(), request.orgName(), request.description(), request.contactEmail());
        // give the current session the new ORGANIZER role immediately
        sessionRefreshService.refresh(currentUser.email(), httpRequest, httpResponse);
        return withLogo(profile);
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    OrganizerProfileResponse get(CurrentUser currentUser) {
        return withLogo(organizerProfileService.getByUser(currentUser.userId()));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    OrganizerProfileResponse update(CurrentUser currentUser,
            @Valid @RequestBody UpdateOrganizerProfileRequest request) {
        return withLogo(organizerProfileService.update(
                currentUser.userId(), request.orgName(), request.description(), request.contactEmail()));
    }

    @PutMapping("/logo")
    @PreAuthorize("hasRole('ORGANIZER')")
    OrganizerProfileResponse setLogo(CurrentUser currentUser, @Valid @RequestBody SetLogoRequest request) {
        return withLogo(organizerProfileService.setLogo(currentUser.userId(), request.fileId()));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('ORGANIZER')")
    OrganizerProfileResponse clearLogo(CurrentUser currentUser) {
        return withLogo(organizerProfileService.clearLogo(currentUser.userId()));
    }

    private OrganizerProfileResponse withLogo(OrganizerProfile profile) {
        return OrganizerProfileResponse.from(profile, organizerProfileService.logoUrl(profile.getImageFileId()));
    }
}
