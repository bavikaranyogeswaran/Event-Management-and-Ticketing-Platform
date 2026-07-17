package com.ticketing.user;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.auth.AuthToken;
import com.ticketing.auth.AuthTokenPurpose;
import com.ticketing.auth.AuthTokenRepository;
import com.ticketing.shared.security.Role;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional // each test rolls back, leaving ticketing_test clean
class UserPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuthTokenRepository authTokenRepository;

    @Test
    void savesAndLoadsUserWithRoles() {
        User user = new User(UUID.randomUUID(), "asha@example.com", "hash", "Asha");
        user.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(user);

        User loaded = userRepository.findByEmail("asha@example.com").orElseThrow();
        assertThat(loaded.getDisplayName()).isEqualTo("Asha");
        assertThat(loaded.hasRole(Role.ATTENDEE)).isTrue();
        assertThat(loaded.isEmailVerified()).isFalse();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void addRoleIsIdempotent() {
        User user = new User(UUID.randomUUID(), "dup@example.com", "hash", "Dup");
        user.addRole(Role.ATTENDEE);
        user.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByEmail("dup@example.com").orElseThrow().getRoles()).hasSize(1);
    }

    @Test
    void savesAndValidatesAuthToken() {
        User user = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "tok@example.com", "hash", "Tok"));
        Instant now = Instant.now();
        AuthToken token = new AuthToken(UUID.randomUUID(), user.getId(), "tokenhash",
                AuthTokenPurpose.EMAIL_VERIFICATION, now.plusSeconds(3600));
        authTokenRepository.saveAndFlush(token);

        AuthToken loaded = authTokenRepository.findByTokenHash("tokenhash").orElseThrow();
        assertThat(loaded.isUsable(now)).isTrue();
        assertThat(loaded.isUsable(now.plusSeconds(7200))).isFalse(); // expired
    }
}
