package com.ticketing.payment;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.config.AppProperties;

/**
 * The only class that knows Stripe exists. Everything it returns is expressed in the
 * platform's own vocabulary, so replacing this file replaces the provider.
 */
@Component
@ConditionalOnProperty(name = "app.payment.secret-key")
class StripePaymentGateway implements PaymentGateway {

    private static final String ORDER_ID_METADATA = "orderId";
    private static final String SESSION_COMPLETED = "checkout.session.completed";
    private static final String SESSION_EXPIRED = "checkout.session.expired";
    private static final String PAYMENT_FAILED = "checkout.session.async_payment_failed";

    private final StripeClient stripe;
    private final String webhookSecret;

    StripePaymentGateway(AppProperties properties) {
        AppProperties.Payment config = properties.payment();
        this.stripe = StripeClient.builder().setApiKey(config.secretKey()).build();
        this.webhookSecret = config.webhookSecret();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .setCustomerEmail(request.buyerEmail())
                .setClientReferenceId(request.orderId().toString())
                // the order id comes back on the webhook; without it a payment cannot be matched
                .putMetadata(ORDER_ID_METADATA, request.orderId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.currency().toLowerCase())
                                .setUnitAmount(request.amountMinorUnits())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.description())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = stripe.checkout().sessions().create(params);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // a provider failure must never look like a completed payment
            throw new ApiException(HttpStatus.BAD_GATEWAY, PaymentErrorCodes.PAYMENT_GATEWAY_UNAVAILABLE,
                    "The payment provider could not start a checkout right now.");
        }
    }

    @Override
    public PaymentEvent parseEvent(String rawPayload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PaymentErrorCodes.WEBHOOK_SIGNATURE_INVALID,
                    "Webhook signature could not be verified.");
        }

        return switch (event.getType()) {
            case SESSION_COMPLETED -> completed(event);
            case SESSION_EXPIRED, PAYMENT_FAILED -> failed(event);
            default -> PaymentEvent.ignored(event.getId());
        };
    }

    private PaymentEvent completed(Event event) {
        Session session = session(event);
        return new PaymentEvent(event.getId(), PaymentEventType.PAYMENT_SUCCEEDED,
                orderIdOf(session), paymentIdOf(session),
                session.getAmountTotal() == null ? 0 : session.getAmountTotal(),
                currencyOf(session), null);
    }

    private PaymentEvent failed(Event event) {
        Session session = session(event);
        return new PaymentEvent(event.getId(), PaymentEventType.PAYMENT_FAILED,
                orderIdOf(session), paymentIdOf(session),
                session.getAmountTotal() == null ? 0 : session.getAmountTotal(),
                currencyOf(session), event.getType());
    }

    private Session session(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject().orElseGet(() -> readAcrossVersions(deserializer));
        if (object instanceof Session session) {
            return session;
        }
        throw unreadable();
    }

    // the account's API version drifts from the SDK's over time; the payload is still ours to read
    private StripeObject readAcrossVersions(EventDataObjectDeserializer deserializer) {
        try {
            return deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw unreadable();
        }
    }

    private ApiException unreadable() {
        return new ApiException(HttpStatus.BAD_REQUEST, PaymentErrorCodes.WEBHOOK_PAYLOAD_UNREADABLE,
                "Webhook payload did not contain a readable checkout session.");
    }

    private UUID orderIdOf(Session session) {
        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata == null ? null : metadata.get(ORDER_ID_METADATA);
        return orderId == null ? null : UUID.fromString(orderId);
    }

    // the session carries a payment intent once money actually moved
    private String paymentIdOf(Session session) {
        return Optional.ofNullable(session.getPaymentIntent()).orElse(session.getId());
    }

    private String currencyOf(Session session) {
        return session.getCurrency() == null ? null : session.getCurrency().toUpperCase();
    }
}
