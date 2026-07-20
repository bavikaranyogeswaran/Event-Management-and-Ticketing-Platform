package com.ticketing.payment;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.shared.api.ApiException;

/**
 * Receives server-to-server payment notifications. There is no session and no CSRF token
 * behind these calls; the signature over the request body is the only thing that makes one
 * trustworthy, so nothing else is believed until it verifies.
 */
@RestController
@RequestMapping("/webhooks/payments")
class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
    private static final String SIGNATURE_HEADER = "Stripe-Signature";

    private final Optional<PaymentGateway> gateway;

    PaymentWebhookController(Optional<PaymentGateway> gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/stripe")
    ResponseEntity<Void> stripe(@RequestBody byte[] rawPayload,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        PaymentGateway provider = gateway.orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, PaymentErrorCodes.PAYMENT_GATEWAY_UNAVAILABLE,
                "No payment provider is configured on this server."));

        // the signature covers the bytes exactly as sent, so the body is never parsed before checking
        PaymentEvent event = provider.parseEvent(new String(rawPayload, StandardCharsets.UTF_8), signature);

        log.info("Verified payment event {} of type {}", event.eventId(), event.type());
        return ResponseEntity.ok().build();
    }
}
