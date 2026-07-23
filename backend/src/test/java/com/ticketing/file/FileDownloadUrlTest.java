package com.ticketing.file;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.ticketing.file.dto.ExportAsset;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(TestObjectStorageConfig.class)
class FileDownloadUrlTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FileService fileService;
    @Autowired FileAssetRepository fileAssets;

    private UUID userId;
    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        String email = "dl." + UUID.randomUUID() + "@example.com";
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Downloader");
        user.addRole(Role.ATTENDEE);
        users.saveAndFlush(user);
        userId = user.getId();
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        session = r.getResponse().getCookie("SESSION");
    }

    /** Creates a PENDING slot then flips it to READY, simulating what the export dispatcher does. */
    private UUID readyExport() {
        ExportAsset asset = fileService.createExportRecord(userId);
        FileAsset fa = fileAssets.findById(asset.fileId()).orElseThrow();
        fa.markReady("text/csv", 512L);
        fileAssets.saveAndFlush(fa);
        return asset.fileId();
    }

    @Test
    void readyExportReturnsUrlAndExpiry() throws Exception {
        UUID fileId = readyExport();

        mockMvc.perform(get("/api/v1/files/" + fileId + "/download-url").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void pendingExportIsRejected() throws Exception {
        ExportAsset asset = fileService.createExportRecord(userId);

        mockMvc.perform(get("/api/v1/files/" + asset.fileId() + "/download-url").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_REQUEST"));
    }

    @Test
    void anotherUsersExportIsNotFound() throws Exception {
        UUID otherId = users.saveAndFlush(new User(UUID.randomUUID(),
                "other." + UUID.randomUUID() + "@example.com", "hash", "Other")).getId();
        ExportAsset other = fileService.createExportRecord(otherId);
        FileAsset fa = fileAssets.findById(other.fileId()).orElseThrow();
        fa.markReady("text/csv", 100L);
        fileAssets.saveAndFlush(fa);

        mockMvc.perform(get("/api/v1/files/" + other.fileId() + "/download-url").cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        UUID fileId = readyExport();

        mockMvc.perform(get("/api/v1/files/" + fileId + "/download-url"))
                .andExpect(status().isUnauthorized());
    }
}
