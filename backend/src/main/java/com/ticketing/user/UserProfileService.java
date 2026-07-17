package com.ticketing.user;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.session.UserSessionService;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;
    private final Clock clock;

    UserProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            UserSessionService userSessionService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSessionService = userSessionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User getProfile(UUID userId) {
        return loadActive(userId);
    }

    @Transactional
    public User updateProfile(UUID userId, String displayName, String phone) {
        User user = loadActive(userId);
        if (displayName != null) {
            user.setDisplayName(displayName.trim());
        }
        if (phone != null) {
            user.setPhone(phone.isBlank() ? null : phone.trim());
        }
        return user;
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = loadActive(userId);
        requireCurrentPassword(user, currentPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userSessionService.invalidateAll(user.getEmail());
    }

    @Transactional
    public void deleteAccount(UUID userId, String currentPassword) {
        User user = loadActive(userId);
        requireCurrentPassword(user, currentPassword);

        String loginName = user.getEmail();
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now(clock));
        // release the real email so the person can sign up again later
        user.setEmail("deleted+" + user.getId() + "@deleted.invalid");
        userSessionService.invalidateAll(loginName);
    }

    private User loadActive(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, UserErrorCodes.INVALID_CURRENT_PASSWORD,
                    "The current password is incorrect.");
        }
    }
}
