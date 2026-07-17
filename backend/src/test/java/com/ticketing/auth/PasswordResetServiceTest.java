package com.ticketing.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PasswordResetServiceTest extends AbstractIntegrationTest {

    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AuthTokenRepository authTokenRepository;
    @Autowired
    TokenService tokenService;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Clock clock;

    private User createUser(String email) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Reset User");
        user.addRole(Role.ATTENDEE);
        return userRepository.saveAndFlush(user);
    }

    private String createResetToken(User user, Instant expiresAt) {
        String raw = tokenService.generateRawToken();
        authTokenRepository.saveAndFlush(new AuthToken(UUID.randomUUID(), user.getId(),
                tokenService.hash(raw), AuthTokenPurpose.PASSWORD_RESET, expiresAt));
        return raw;
    }

    @Test
    void requestResetIssuesTokenForKnownEmail() {
        createUser("known@example.com");

        passwordResetService.requestReset("Known@example.com");

        assertThat(authTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void requestResetIsSilentForUnknownEmail() {
        passwordResetService.requestReset("nobody@example.com");

        assertThat(authTokenRepository.count()).isZero();
    }

    @Test
    void resetChangesPasswordAndConsumesToken() {
        User user = createUser("change@example.com");
        String raw = createResetToken(user, Instant.now(clock).plusSeconds(3600));

        passwordResetService.reset(raw, "brand-new-pass");

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-pass", updated.getPasswordHash())).isTrue();
        assertThat(authTokenRepository.findByTokenHash(tokenService.hash(raw)).orElseThrow().isUsable(Instant.now(clock)))
                .isFalse();
    }

    @Test
    void reusedTokenIsRejected() {
        User user = createUser("reuse@example.com");
        String raw = createResetToken(user, Instant.now(clock).plusSeconds(3600));
        passwordResetService.reset(raw, "brand-new-pass");

        assertThatThrownBy(() -> passwordResetService.reset(raw, "another-pass"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TOKEN_EXPIRED"));
    }
}
