package com.ticketing.admin;

import java.time.Instant;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminUserApiTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Cookie loginAsAdmin(String email) throws Exception {
        User admin = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Admin");
        admin.addRole(Role.ADMIN);
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(admin);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("SESSION");
    }

    private UUID createRegularUser(String email) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "User");
        user.addRole(Role.ATTENDEE);
        return userRepository.saveAndFlush(user).getId();
    }

    @Test
    void listUsersReturnsPageWithItems() throws Exception {
        Cookie admin = loginAsAdmin("admin.list@example.com");
        createRegularUser("target.list@example.com");

        mockMvc.perform(get("/api/v1/admin/users").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.email == 'target.list@example.com')]").exists());
    }

    @Test
    void suspendUserChangesStatusAndReturnsUpdatedSummary() throws Exception {
        Cookie admin = loginAsAdmin("admin.suspend@example.com");
        UUID targetId = createRegularUser("to.suspend@example.com");

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetId.toString()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void reactivateSuspendedUserRestoresActiveStatus() throws Exception {
        Cookie admin = loginAsAdmin("admin.reactivate@example.com");
        User user = new User(UUID.randomUUID(), "suspended@example.com", "hash", "User");
        user.addRole(Role.ATTENDEE);
        user.setStatus(com.ticketing.user.UserStatus.SUSPENDED);
        UUID targetId = userRepository.saveAndFlush(user).getId();

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void settingStatusToDeletedIsRejected() throws Exception {
        Cookie admin = loginAsAdmin("admin.delete@example.com");
        UUID targetId = createRegularUser("nodelete@example.com");

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonExistentUserReturns404() throws Exception {
        Cookie admin = loginAsAdmin("admin.404@example.com");

        mockMvc.perform(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        User regular = new User(UUID.randomUUID(), "regular@example.com", passwordEncoder.encode("password123"), "R");
        regular.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(regular);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"regular@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie session = r.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/v1/admin/users").cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsersLimitAndCursorPagination() throws Exception {
        Cookie admin = loginAsAdmin("admin.page@example.com");
        for (int i = 0; i < 3; i++) {
            createRegularUser("pageuser" + i + "@example.com");
        }

        // fetch one item, expect nextCursor for remaining
        MvcResult first = mockMvc.perform(get("/api/v1/admin/users?limit=1").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        String body = first.getResponse().getContentAsString();
        assertThat(body).contains("nextCursor");
    }
}
