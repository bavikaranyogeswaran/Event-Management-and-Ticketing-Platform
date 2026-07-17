package com.ticketing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Base for Spring integration tests: full context against ticketing_test and the real local Redis/RabbitMQ. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
}
