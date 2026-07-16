package com.ticketing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Base for Spring integration tests: full context against ticketing_test + real local Redis/RabbitMQ (ADR-0010). */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
}
