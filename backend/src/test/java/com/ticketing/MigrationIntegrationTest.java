package com.ticketing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the full empty-database migration path against real PostgreSQL. */
class MigrationIntegrationTest {

    @BeforeAll
    static void cleanAndMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(TestEnv.TEST_JDBC_URL, TestEnv.dbUser(), TestEnv.dbPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(TestEnv.TEST_JDBC_URL, TestEnv.dbUser(), TestEnv.dbPassword());
    }

    @Test
    void allMigrationsApplied() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT count(*) FROM flyway_schema_history WHERE success");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(6);
        }
    }

    @Test
    void allExpectedTablesExist() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'");
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertThat(tables).containsExactlyInAnyOrder(
                "users", "user_roles", "auth_tokens", "organizer_profiles",
                "categories", "events", "ticket_types", "event_staff_assignments",
                "orders", "order_items", "payments",
                "tickets", "check_ins",
                "file_assets", "outbox_jobs", "audit_logs");
    }

    @Test
    void correctnessCriticalConstraintsExist() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'");
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        assertThat(names).contains(
                "ux_users_email",                 // login/registration uniqueness
                "ux_orders_idempotency",          // duplicate-order defense
                "ux_payments_provider_payment",   // webhook-replay defense
                "ux_tickets_token_hash",          // QR forgery defense
                "ux_check_ins_ticket");           // duplicate check-in defense
    }

    @Test
    void categoriesAreSeeded() throws Exception {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT count(*) FROM categories WHERE active");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(6);
        }
    }
}
