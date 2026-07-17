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

import jakarta.servlet.http.Cookie;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.user.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;
import com.ticketing.user.UserStatus;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class LoginLogoutTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private void createUser(String email, String rawPassword, UserStatus status) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), "Test User");
        user.addRole(Role.ATTENDEE);
        user.setStatus(status);
        userRepository.saveAndFlush(user);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginThenSessionReturnsIdentity() throws Exception {
        createUser("login@example.com", "password123", UserStatus.ACTIVE);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ATTENDEE"))
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void wrongPasswordIsRejectedGenerically() throws Exception {
        createUser("wrong@example.com", "password123", UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("wrong@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void suspendedAccountCannotLogIn() throws Exception {
        createUser("suspended@example.com", "password123", UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("suspended@example.com", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void sessionWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        createUser("logout@example.com", "password123", UserStatus.ACTIVE);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("logout@example.com", "password123")))
                .andExpect(status().isOk())
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
