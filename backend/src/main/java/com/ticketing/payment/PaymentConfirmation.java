package com.ticketing.payment;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.order.Order;
import com.ticketing.order.OrderRepository;
import com.ticketing.order.OrderService;
import com.ticketing.order.PaidOrderOutcome;
import com.ticketing.shared.config.AppProperties;
import com.ticketing.shared.port.IdGenerator;

/**
 * Records what the provider says happened and, when it checks out, settles the order in the
 * same transaction. Money is never recorded without the order moving, or the reverse.
 */
@Service
class PaymentConfirmation {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmation.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final String gatewayCurrency;

    PaymentConfirmation(PaymentRepository payments, OrderRepository orders, OrderService orderService,
            IdGenerator idGenerator, Clock clock, AppProperties properties) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.gatewayCurrency = properties.payment().gatewayCurrency();
    }

    @Transactional
    void apply(PaymentProvider provider, PaymentEvent event) {
        // locked up front: everything after this decides against a row nobody else can move,
        // and reading it a second time later would risk two versions in one session
        Order order = orders.findByIdForUpdate(event.orderId()).orElse(null);
        if (order == null) {
            // nothing here to settle; acknowledged so the provider stops resending
            log.warn("Payment event {} refers to unknown order {}", event.eventId(), event.orderId());
            return;
        }

        if (event.type() == PaymentEventType.PAYMENT_FAILED) {
            recordFailure(provider, event, order);
            return;
        }
        recordSuccess(provider, event, order);
    }

    private void recordFailure(PaymentProvider provider, PaymentEvent event, Order order) {
        Payment payment = newPayment(provider, order);
        payment.markFailed(event.providerPaymentId(), event.eventId(), event.failureCode());
        payments.save(payment);
        // the order keeps its seats and stays payable until it runs out of time
        log.info("Payment {} failed for order {}", event.providerPaymentId(), order.getId());
    }

    private void recordSuccess(PaymentProvider provider, PaymentEvent event, Order order) {
        Payment payment = newPayment(provider, order);
        payment.markSucceeded(event.providerPaymentId(), event.eventId(), Instant.now(clock));
        payments.saveAndFlush(payment); // the unique provider payment is what stops a replay here

        if (!chargeMatchesOrder(order, event)) {
            // the money is on file, but it is not what this order costs, so nothing is released
            log.error("{}: order {} expects {} {} but provider reported {} {}",
                    PaymentErrorCodes.PAYMENT_AMOUNT_MISMATCH, order.getId(),
                    MinorUnits.from(order.getGrandTotal(), gatewayCurrency), gatewayCurrency,
                    event.amountMinorUnits(), event.currency());
            return;
        }

        PaidOrderOutcome outcome = orderService.confirmPaidOrder(order);
        if (outcome == PaidOrderOutcome.SEATS_GONE) {
            // paid after the hold lapsed and the seats had gone; needs a refund by hand
            log.error("Order {} was paid by {} after expiry and could not be honoured; refund required",
                    order.getId(), event.providerPaymentId());
        }
    }

    private boolean chargeMatchesOrder(Order order, PaymentEvent event) {
        return event.currency() != null
                && event.currency().equalsIgnoreCase(gatewayCurrency)
                && event.amountMinorUnits() == MinorUnits.from(order.getGrandTotal(), gatewayCurrency);
    }

    private Payment newPayment(PaymentProvider provider, Order order) {
        return new Payment(idGenerator.newId(), order.getId(), provider,
                order.getGrandTotal(), order.getCurrency());
    }
}
