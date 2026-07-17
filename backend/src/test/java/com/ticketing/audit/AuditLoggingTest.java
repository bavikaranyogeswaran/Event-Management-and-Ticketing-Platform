package com.ticketing.audit;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AuditLoggingTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registrationIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"audit@example.com\",\"password\":\"password123\",\"displayName\":\"Audit\"}"))
                .andExpect(status().isCreated());

        AuditLog log = auditLogRepository.findFirstByActionOrderByCreatedAtDesc(AuditActions.USER_REGISTERED)
                .orElseThrow();
        assertThat(log.getActorUserId()).isNotNull();
        assertThat(log.getRequestId()).isNotNull();
    }

    @Test
    void failedLoginIsAuditedWithoutActor() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@example.com\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());

        AuditLog log = auditLogRepository.findFirstByActionOrderByCreatedAtDesc(AuditActions.LOGIN_FAILED)
                .orElseThrow();
        assertThat(log.getActorUserId()).isNull();
        assertThat(log.getDetail()).contains("ghost@example.com");
    }

    @Test
    void successfulLoginIsAudited() throws Exception {
        User user = new User(UUID.randomUUID(), "success@example.com",
                passwordEncoder.encode("password123"), "Success");
        user.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"success@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.countByAction(AuditActions.LOGIN_SUCCEEDED)).isPositive();
    }
}
