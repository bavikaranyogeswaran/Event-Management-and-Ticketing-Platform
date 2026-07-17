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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AuthTokenRepository authTokenRepository;
    @Autowired
    TokenService tokenService;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void resetKillsExistingSessionsAndAllowsNewPassword() throws Exception {
        User user = new User(UUID.randomUUID(), "flow@example.com",
                passwordEncoder.encode("password123"), "Flow User");
        user.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(user);

        // log in and confirm the session works
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk());

        // reset the password with a valid token
        String rawToken = tokenService.generateRawToken();
        authTokenRepository.saveAndFlush(new AuthToken(UUID.randomUUID(), user.getId(),
                tokenService.hash(rawToken), AuthTokenPurpose.PASSWORD_RESET,
                java.time.Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isNoContent());

        // the old session is gone
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());

        // the new password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@example.com\",\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isOk());
    }
}
