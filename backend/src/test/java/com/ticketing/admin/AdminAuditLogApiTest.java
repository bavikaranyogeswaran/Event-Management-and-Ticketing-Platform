package com.ticketing.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminAuditLogApiTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    record AdminSession(Cookie cookie, UUID userId) {}

    private AdminSession loginAsAdmin(String email) throws Exception {
        User admin = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Admin");
        admin.addRole(Role.ADMIN);
        userRepository.saveAndFlush(admin);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new AdminSession(r.getResponse().getCookie("SESSION"), admin.getId());
    }

    /** Inserts directly via JDBC so the row is visible to the JdbcTemplate-backed listing query. */
    private UUID insertAuditLog(UUID actorId, String action, String entityType, UUID entityId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO audit_logs(id, actor_user_id, action, entity_type, entity_id, detail, request_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)",
                id, actorId, action, entityType, entityId, Timestamp.from(Instant.now()));
        return id;
    }

    @Test
    void listLogsReturnsAuditEntries() throws Exception {
        AdminSession admin = loginAsAdmin("audit.list@example.com");
        UUID loggedAction = insertAuditLog(admin.userId(), "TEST_ACTION", "USER", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.logId == '" + loggedAction + "')]").exists());
    }

    @Test
    void logsResponseIncludesExpectedFields() throws Exception {
        AdminSession admin = loginAsAdmin("audit.fields@example.com");
        insertAuditLog(admin.userId(), "USER_SUSPENDED", "USER", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].logId").exists())
                .andExpect(jsonPath("$.items[0].action").exists())
                .andExpect(jsonPath("$.items[0].createdAt").exists());
    }

    @Test
    void paginationReturnsCursorWhenMoreAvailable() throws Exception {
        AdminSession admin = loginAsAdmin("audit.page@example.com");
        for (int i = 0; i < 3; i++) {
            insertAuditLog(admin.userId(), "PAGED_ACTION", "ENTITY", UUID.randomUUID());
        }

        MvcResult result = mockMvc.perform(get("/api/v1/admin/audit-logs?limit=1").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("nextCursor");
    }

    @Test
    void nonAdminCannotListAuditLogs() throws Exception {
        User regular = new User(UUID.randomUUID(), "noaudit@example.com", passwordEncoder.encode("password123"), "R");
        regular.addRole(Role.ATTENDEE);
        userRepository.saveAndFlush(regular);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noaudit@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie session = r.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(session))
                .andExpect(status().isForbidden());
    }
}
