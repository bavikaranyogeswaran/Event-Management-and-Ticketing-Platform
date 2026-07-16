package com.ticketing;

import io.github.cdimascio.dotenv.Dotenv;

/** Reads the repo-root .env for tests that run without a Spring context. */
final class TestEnv {

    private static final Dotenv DOTENV =
            Dotenv.configure().directory("..").ignoreIfMissing().load();

    static final String TEST_JDBC_URL = "jdbc:postgresql://localhost:5433/ticketing_test";

    private TestEnv() {
    }

    static String dbUser() {
        return DOTENV.get("DB_USERNAME", "ticketing_app");
    }

    static String dbPassword() {
        return DOTENV.get("DB_PASSWORD", "");
    }
}
