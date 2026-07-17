package com.ticketing.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.user.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class RegistrationServiceTest extends AbstractIntegrationTest {

    @Autowired
    RegistrationService registrationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OutboxJobRepository outboxJobRepository;

    @Test
    void registersUserWithAttendeeRoleAndVerificationJob() {
        User user = registrationService.register("New.User@Example.com", "password123", "New User");

        assertThat(user.getEmail()).isEqualTo("new.user@example.com"); // normalized
        assertThat(user.hasRole(Role.ATTENDEE)).isTrue();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(outboxJobRepository.findByJobKey(JobTypes.emailVerificationKey(user.getId()))).isPresent();
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() {
        registrationService.register("dupe@example.com", "password123", "First");

        assertThatThrownBy(() -> registrationService.register("DUPE@example.com", "password123", "Second"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
    }
}
