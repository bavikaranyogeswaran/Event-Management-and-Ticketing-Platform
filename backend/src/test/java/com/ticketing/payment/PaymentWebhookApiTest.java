package com.ticketing.payment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(TestPaymentGatewayConfig.class)
class PaymentWebhookApiTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/webhooks/payments/stripe";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FakePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway.reset();
        gateway.willReturn(PaymentEvent.ignored("evt_test_1"));
    }

    private String body() {
        return "{\"id\":\"evt_test_1\",\"type\":\"checkout.session.completed\"}";
    }

    @Test
    void aSignedEventIsAccepted() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void anUnsignedEventIsRejected() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void aForgedSignatureIsRejected() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void noSessionIsNeededToDeliverAnEvent() throws Exception {
        // the provider has no login here; the signature is what stands in for one
        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void noCsrfTokenIsNeededToDeliverAnEvent() throws Exception {
        // deliberately posted without .with(csrf()); a provider cannot hold a browser token
        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void theBodyReachesTheGatewayByteForByte() throws Exception {
        // signatures cover the exact bytes sent, so any re-encoding on the way in breaks them
        String payload = "{\"id\":\"evt_unicode\",\"note\":\"Ayubōwan ☕ 1500₨\"}";

        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        assertThat(gateway.lastPayload()).isEqualTo(payload);
    }

    @Test
    void theBodyIsNotReformattedOnTheWayIn() throws Exception {
        // whitespace and key order are part of what was signed
        String payload = "{  \"type\" : \"customer.created\",\n  \"id\":\"evt_spaced\"  }";

        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        assertThat(gateway.lastPayload()).isEqualTo(payload);
    }

    @Test
    void anEventTypeThisPlatformIgnoresIsStillAcknowledged() throws Exception {
        gateway.willReturn(PaymentEvent.ignored("evt_ignored"));

        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
    }

    @Test
    void aVerifiedPaymentEventIsAccepted() throws Exception {
        gateway.willReturn(new PaymentEvent("evt_paid", PaymentEventType.PAYMENT_SUCCEEDED,
                UUID.randomUUID(), "pi_1", 300_000L, "LKR", null));

        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", FakePaymentGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
    }
}
