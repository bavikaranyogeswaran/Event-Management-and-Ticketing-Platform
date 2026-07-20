package com.ticketing.payment;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A deployment with no payment credentials. Deliberately imports no gateway, so this is the
 * shape the application takes when Stripe is simply not set up: it still starts and still
 * answers, rather than failing at boot or pretending payments work.
 */
@AutoConfigureMockMvc
@Transactional
class PaymentsNotConfiguredTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ApplicationContext context;

    @Test
    void theApplicationStartsWithNoProviderAtAll() {
        assertThat(context.getBeansOfType(PaymentGateway.class)).isEmpty();
    }

    @Test
    void aWebhookDeliveryIsTurnedAwayRatherThanAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/payments/stripe")
                        .header("Stripe-Signature", "t=1,v1=whatever")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
    }

    @Test
    void startingACheckoutSaysSoPlainly() throws Exception {
        // unauthenticated, so this only proves the endpoint exists and is guarded as usual
        mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/checkout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
