package com.ticketing.organizer;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class OrganizerProfileApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie login(String email, boolean verified) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Org User");
        user.addRole(Role.ATTENDEE);
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        userRepository.saveAndFlush(user);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void unverifiedUserCannotBecomeOrganizer() throws Exception {
        Cookie cookie = login("unverified.org@example.com", false);

        mockMvc.perform(post("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgName\":\"Cool Events\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void createGrantsOrganizerRoleImmediatelyInSameSession() throws Exception {
        Cookie cookie = login("organizer@example.com", true);

        mockMvc.perform(post("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgName\":\"Cool Events\",\"contactEmail\":\"hello@coolevents.lk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgName").value("Cool Events"));

        // GET requires the ORGANIZER role; success proves the session picked it up without re-login
        mockMvc.perform(get("/api/v1/organizer/profile").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("hello@coolevents.lk"));
    }

    @Test
    void duplicateProfileIsRejected() throws Exception {
        Cookie cookie = login("dupe.org@example.com", true);
        String body = "{\"orgName\":\"First\"}";

        mockMvc.perform(post("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZER_PROFILE_EXISTS"));
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        Cookie cookie = login("edit.org@example.com", true);
        mockMvc.perform(post("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orgName\":\"Old Name\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/organizer/profile").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orgName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgName").value("New Name"));
    }

    @Test
    void nonOrganizerCannotReadProfile() throws Exception {
        Cookie cookie = login("plain.attendee@example.com", true);

        mockMvc.perform(get("/api/v1/organizer/profile").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
