package com.ticketing.user;

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

import jakarta.servlet.http.Cookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class UserProfileApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private Cookie createUserAndLogin(String email) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Profile User");
        user.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(user);
        return login(email);
    }

    @Test
    void returnsAndUpdatesProfile() throws Exception {
        Cookie cookie = createUserAndLogin("profile@example.com");

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@example.com"))
                .andExpect(jsonPath("$.displayName").value("Profile User"));

        mockMvc.perform(patch("/api/v1/users/me").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Name\",\"phone\":\"0771234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("0771234567"));
    }

    @Test
    void writeWithoutCsrfTokenIsForbidden() throws Exception {
        Cookie cookie = createUserAndLogin("nocsrf@example.com");

        mockMvc.perform(patch("/api/v1/users/me").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hacker\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() throws Exception {
        Cookie cookie = createUserAndLogin("changepw@example.com");

        mockMvc.perform(post("/api/v1/users/me/password").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changePasswordKillsSessionsAndSetsNewPassword() throws Exception {
        Cookie cookie = createUserAndLogin("newpw@example.com");

        mockMvc.perform(post("/api/v1/users/me/password").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newpw@example.com\",\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccountBlocksLoginAndReleasesEmail() throws Exception {
        Cookie cookie = createUserAndLogin("gone@example.com");

        mockMvc.perform(delete("/api/v1/users/me").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\"}"))
                .andExpect(status().isNoContent());

        // the deleted account can no longer log in
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gone@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        // the email is free to register again
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gone@example.com\",\"password\":\"password123\",\"displayName\":\"Again\"}"))
                .andExpect(status().isCreated());
    }
}
