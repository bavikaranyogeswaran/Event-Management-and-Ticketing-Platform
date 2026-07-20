package com.ticketing.payment;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.stripe.Stripe;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the adapter against Stripe's own verification code using locally signed payloads,
 * so the webhook path is covered without reaching the network.
 */
class StripePaymentGatewayTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final StripePaymentGateway gateway = new StripePaymentGateway(properties());

    private AppProperties properties() {
        return new AppProperties(null, null, null, null, null, null,
                new AppProperties.Payment("sk_test_dummy", WEBHOOK_SECRET, "LKR", "/ok", "/cancel"),
                null);
    }

    private String payload(String eventType, String orderId, long amountMinor, String currency) {
        return payload(eventType, orderId, amountMinor, currency, Stripe.API_VERSION);
    }

    private String payload(String eventType, String orderId, long amountMinor, String currency,
            String apiVersion) {
        return """
                {
                  "id": "evt_test_1",
                  "object": "event",
                  "api_version": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "cs_test_1",
                      "object": "checkout.session",
                      "amount_total": %d,
                      "currency": "%s",
                      "payment_intent": "pi_test_1",
                      "metadata": { "orderId": "%s" }
                    }
                  }
                }
                """.formatted(apiVersion, eventType, amountMinor, currency, orderId);
    }

    /** Builds the same `t=…,v1=…` header Stripe sends. */
    private String signatureFor(String payload, String secret) {
        long timestamp = System.currentTimeMillis() / 1000;
        String signed = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String v1 = HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + v1;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PaymentEvent parse(String payload) {
        return gateway.parseEvent(payload, signatureFor(payload, WEBHOOK_SECRET));
    }

    private String codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    @Test
    void identifiesItselfAsStripe() {
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.STRIPE);
    }

    @Test
    void aCompletedCheckoutBecomesASuccessfulPayment() {
        PaymentEvent event = parse(payload("checkout.session.completed", ORDER_ID.toString(), 300_000L, "lkr"));

        assertThat(event.type()).isEqualTo(PaymentEventType.PAYMENT_SUCCEEDED);
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.providerPaymentId()).isEqualTo("pi_test_1");
        assertThat(event.amountMinorUnits()).isEqualTo(300_000L);
        assertThat(event.currency()).isEqualTo("LKR"); // Stripe reports lowercase
        assertThat(event.eventId()).isEqualTo("evt_test_1");
    }

    @Test
    void anExpiredCheckoutBecomesAFailedPayment() {
        PaymentEvent event = parse(payload("checkout.session.expired", ORDER_ID.toString(), 300_000L, "lkr"));

        assertThat(event.type()).isEqualTo(PaymentEventType.PAYMENT_FAILED);
        assertThat(event.failureCode()).isEqualTo("checkout.session.expired");
    }

    @Test
    void eventTypesThisPlatformDoesNotActOnAreIgnored() {
        PaymentEvent event = parse(payload("customer.created", ORDER_ID.toString(), 0L, "lkr"));

        assertThat(event.type()).isEqualTo(PaymentEventType.IGNORED);
        assertThat(event.eventId()).isEqualTo("evt_test_1");
    }

    @Test
    void aPayloadSignedWithTheWrongSecretIsRejected() {
        String body = payload("checkout.session.completed", ORDER_ID.toString(), 300_000L, "lkr");

        assertThatThrownBy(() -> gateway.parseEvent(body, signatureFor(body, "whsec_attacker")))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void aTamperedAmountInvalidatesTheSignature() {
        // sign the real amount, then try to sneak a cheaper one through
        String genuine = payload("checkout.session.completed", ORDER_ID.toString(), 300_000L, "lkr");
        String tampered = payload("checkout.session.completed", ORDER_ID.toString(), 1L, "lkr");

        assertThatThrownBy(() -> gateway.parseEvent(tampered, signatureFor(genuine, WEBHOOK_SECRET)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void anUnsignedRequestIsRejected() {
        String body = payload("checkout.session.completed", ORDER_ID.toString(), 300_000L, "lkr");

        assertThatThrownBy(() -> gateway.parseEvent(body, ""))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void aSessionWithoutOurOrderIdYieldsNoOrder() {
        String body = """
                {
                  "id": "evt_test_2", "object": "event", "api_version": "%s",
                  "type": "checkout.session.completed",
                  "data": { "object": { "id": "cs_test_2", "object": "checkout.session",
                    "amount_total": 100, "currency": "lkr", "payment_intent": "pi_test_2" } }
                }
                """.formatted(Stripe.API_VERSION);

        assertThat(parse(body).orderId()).isNull();
    }

    @Test
    void anEventFromAnOlderApiVersionIsStillRead() {
        // a Stripe account can send a version the bundled SDK does not expect
        PaymentEvent event = parse(payload("checkout.session.completed", ORDER_ID.toString(),
                300_000L, "lkr", "2020-08-27"));

        assertThat(event.type()).isEqualTo(PaymentEventType.PAYMENT_SUCCEEDED);
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.amountMinorUnits()).isEqualTo(300_000L);
    }
}
