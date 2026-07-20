package com.ticketing.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the stand-in provider. The real Stripe adapter stays absent under the test
 * profile because no secret key is configured, so there is no ambiguity about which one wins.
 */
@TestConfiguration
public class TestPaymentGatewayConfig {

    @Bean
    FakePaymentGateway fakePaymentGateway() {
        return new FakePaymentGateway();
    }
}
