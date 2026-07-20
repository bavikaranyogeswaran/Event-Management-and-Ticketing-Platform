package com.ticketing.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the test suite away from the live payment provider. The repo's .env is on the
 * classpath while tests run, so a real key is present in the environment; only the blank
 * override in application-test.yml stops the Stripe adapter being wired and billed against.
 * Losing that would fail nothing loudly, hence these checks.
 */
@Import(TestPaymentGatewayConfig.class)
class PaymentGatewayWiringTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext context;
    @Autowired
    Environment environment;

    @Test
    void theOnlyGatewayAvailableToTestsIsTheStandIn() {
        assertThat(context.getBeansOfType(PaymentGateway.class))
                .isNotEmpty()
                .allSatisfy((name, gateway) -> assertThat(gateway).isInstanceOf(FakePaymentGateway.class));
    }

    @Test
    void theRealAdapterIsNeverConstructed() {
        assertThat(context.getBeansOfType(StripePaymentGateway.class)).isEmpty();
    }

    @Test
    void theProviderKeyStaysBlankUnderTheTestProfile() {
        assertThat(environment.getProperty("app.payment.secret-key", "")).isEmpty();
    }
}
