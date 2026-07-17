package com.ticketing.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.shared.security.CurrentUser;
import com.ticketing.user.dto.ChangePasswordRequest;
import com.ticketing.user.dto.DeleteAccountRequest;
import com.ticketing.user.dto.UpdateProfileRequest;
import com.ticketing.user.dto.UserProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
class UserController {

    private final UserProfileService userProfileService;

    UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    UserProfileResponse getMe(CurrentUser currentUser) {
        return UserProfileResponse.from(userProfileService.getProfile(currentUser.userId()));
    }

    @PatchMapping("/me")
    UserProfileResponse updateMe(CurrentUser currentUser, @Valid @RequestBody UpdateProfileRequest request) {
        return UserProfileResponse.from(
                userProfileService.updateProfile(currentUser.userId(), request.displayName(), request.phone()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(CurrentUser currentUser, @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(currentUser.userId(), request.currentPassword(), request.newPassword());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe(CurrentUser currentUser, @Valid @RequestBody DeleteAccountRequest request) {
        userProfileService.deleteAccount(currentUser.userId(), request.currentPassword());
    }
}
