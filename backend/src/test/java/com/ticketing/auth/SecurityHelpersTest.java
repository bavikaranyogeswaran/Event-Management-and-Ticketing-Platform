package com.ticketing.auth;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SecurityHelpersTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie loginAs(String email, Role role) throws Exception {
        return loginAs(email, role, false);
    }

    private Cookie loginAs(String email, Role role, boolean emailVerified) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Helper User");
        user.addRole(role);
        if (emailVerified) {
            user.setEmailVerifiedAt(java.time.Instant.now());
        }
        userRepository.saveAndFlush(user);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("SESSION");
    }

    @Test
    void currentUserIsInjectedFromSession() throws Exception {
        Cookie cookie = loginAs("attendee@example.com", Role.ATTENDEE);

        mockMvc.perform(get("/api/v1/test-security/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("attendee@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void attendeeIsForbiddenFromAdminEndpoint() throws Exception {
        Cookie cookie = loginAs("notadmin@example.com", Role.ATTENDEE);

        mockMvc.perform(get("/api/v1/test-security/admin-only").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminReachesAdminEndpoint() throws Exception {
        Cookie cookie = loginAs("admin@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/v1/test-security/admin-only").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void verifiedGuardBlocksUnverifiedUser() throws Exception {
        Cookie cookie = loginAs("unverified@example.com", Role.ATTENDEE, false);

        mockMvc.perform(get("/api/v1/test-security/verified-only").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void verifiedGuardAllowsVerifiedUser() throws Exception {
        Cookie cookie = loginAs("verified@example.com", Role.ATTENDEE, true);

        mockMvc.perform(get("/api/v1/test-security/verified-only").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
