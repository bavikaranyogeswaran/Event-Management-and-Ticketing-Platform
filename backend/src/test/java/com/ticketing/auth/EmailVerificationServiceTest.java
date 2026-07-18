package com.ticketing.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.security.TokenService;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EmailVerificationServiceTest extends AbstractIntegrationTest {

    @Autowired
    EmailVerificationService verificationService;
    @Autowired
    AuthTokenRepository authTokenRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    TokenService tokenService;
    @Autowired
    Clock clock;

    private User newUser() {
        return userRepository.saveAndFlush(new User(UUID.randomUUID(), "verify@example.com", "hash", "Verify"));
    }

    private String storeToken(User user, Instant expiresAt) {
        String raw = tokenService.generateRawToken();
        authTokenRepository.saveAndFlush(new AuthToken(UUID.randomUUID(), user.getId(),
                tokenService.hash(raw), AuthTokenPurpose.EMAIL_VERIFICATION, expiresAt));
        return raw;
    }

    @Test
    void verifiesUserWithValidToken() {
        User user = newUser();
        String raw = storeToken(user, Instant.now(clock).plusSeconds(3600));

        verificationService.verify(raw);

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void rejectsReusedToken() {
        User user = newUser();
        String raw = storeToken(user, Instant.now(clock).plusSeconds(3600));
        verificationService.verify(raw);

        assertThatThrownBy(() -> verificationService.verify(raw))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TOKEN_EXPIRED"));
    }

    @Test
    void rejectsExpiredToken() {
        User user = newUser();
        String raw = storeToken(user, Instant.now(clock).minusSeconds(1));

        assertThatThrownBy(() -> verificationService.verify(raw))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TOKEN_EXPIRED"));
    }

    @Test
    void rejectsUnknownToken() {
        assertThatThrownBy(() -> verificationService.verify("not-a-real-token"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TOKEN_INVALID"));
    }
}
