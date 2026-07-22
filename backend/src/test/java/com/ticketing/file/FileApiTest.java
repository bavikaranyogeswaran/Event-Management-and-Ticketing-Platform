package com.ticketing.file;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(TestObjectStorageConfig.class)
class FileApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    FakeObjectStorage storage;
    @Autowired
    ObjectMapper objectMapper;

    private Cookie login(String email) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Uploader");
        user.addRole(Role.ATTENDEE);
        users.saveAndFlush(user);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void aProfileImageRequestReturnsSignedParams() throws Exception {
        Cookie cookie = login("uploader@example.com");

        mockMvc.perform(post("/api/v1/files/upload-requests").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"PROFILE_IMAGE\",\"mime\":\"image/png\",\"sizeBytes\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.publicId").exists())
                .andExpect(jsonPath("$.signature").exists());
    }

    @Test
    void anUnsupportedTypeIsRejected() throws Exception {
        Cookie cookie = login("gif@example.com");

        mockMvc.perform(post("/api/v1/files/upload-requests").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"PROFILE_IMAGE\",\"mime\":\"image/gif\",\"sizeBytes\":1000}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void anAnonymousUploadRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/files/upload-requests").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"PROFILE_IMAGE\",\"mime\":\"image/png\",\"sizeBytes\":1000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestingThenCompletingAnUploadReturnsAReadyAsset() throws Exception {
        Cookie cookie = login("complete@example.com");

        MvcResult requested = mockMvc.perform(post("/api/v1/files/upload-requests").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"PROFILE_IMAGE\",\"mime\":\"image/png\",\"sizeBytes\":1000}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(requested.getResponse().getContentAsString(), Map.class);
        // the browser's direct upload to the provider, stood in for by the fake
        storage.simulateUpload((String) body.get("publicId"), "image/png", 900);

        mockMvc.perform(post("/api/v1/files/" + body.get("fileId") + "/complete").cookie(cookie).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.url").exists());
    }
}
