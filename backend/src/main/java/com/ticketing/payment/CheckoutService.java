package com.ticketing.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.order.Order;
import com.ticketing.order.OrderService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.config.AppProperties;

/** Opens a hosted payment page for an order that is already holding its seats. */
@Service
public class CheckoutService {

    private static final String ORDER_ID_PLACEHOLDER = "{orderId}";

    private final OrderService orders;
    private final EventRepository events;
    // absent when no provider is configured, so the app still runs without payment credentials
    private final Optional<PaymentGateway> gateway;
    private final String baseUrl;
    private final String gatewayCurrency;
    private final String successPath;
    private final String cancelPath;

    CheckoutService(OrderService orders, EventRepository events, Optional<PaymentGateway> gateway,
            AppProperties properties) {
        this.orders = orders;
        this.events = events;
        this.gateway = gateway;
        this.baseUrl = properties.baseUrl();
        AppProperties.Payment config = properties.payment();
        this.gatewayCurrency = config.gatewayCurrency();
        this.successPath = config.successPath();
        this.cancelPath = config.cancelPath();
    }

    public CheckoutSession startCheckout(UUID orderId, UUID buyerId, String buyerEmail) {
        Order order = orders.requirePayableOrder(orderId, buyerId);
        PaymentGateway provider = gateway.orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, PaymentErrorCodes.PAYMENT_GATEWAY_UNAVAILABLE,
                "Card payments are not configured on this server."));

        return provider.createCheckoutSession(new CheckoutRequest(
                order.getId(),
                order.getOrderNumber(),
                describe(order),
                // the amount charged is derived from the stored order, never from the client
                MinorUnits.from(order.getGrandTotal(), gatewayCurrency),
                gatewayCurrency,
                buyerEmail,
                returnUrl(successPath, orderId),
                returnUrl(cancelPath, orderId)));
    }

    // the buyer sees this on the payment page, so the event name beats an order reference
    private String describe(Order order) {
        return events.findById(order.getEventId())
                .map(Event::getTitle)
                .orElseGet(order::getOrderNumber);
    }

    private String returnUrl(String path, UUID orderId) {
        return baseUrl + path.replace(ORDER_ID_PLACEHOLDER, orderId.toString());
    }
}
