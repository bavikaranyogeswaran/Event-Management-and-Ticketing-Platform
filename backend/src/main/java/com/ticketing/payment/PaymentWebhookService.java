package com.ticketing.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Front door for verified provider events. Providers deliver at least once, so handling the
 * same payment twice has to be a no-op rather than a second charge or a second set of tickets.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentConfirmation confirmation;
    private final PaymentRepository payments;

    PaymentWebhookService(PaymentConfirmation confirmation, PaymentRepository payments) {
        this.confirmation = confirmation;
        this.payments = payments;
    }

    /**
     * Not transactional on purpose: when two deliveries race, the loser's transaction has to
     * roll back before this can tell that the winner already recorded the payment.
     */
    public void handle(PaymentProvider provider, PaymentEvent event) {
        if (event.type() == PaymentEventType.IGNORED || event.orderId() == null) {
            return;
        }
        if (alreadyRecorded(provider, event)) {
            log.info("Ignoring repeat delivery of payment {}", event.providerPaymentId());
            return;
        }
        try {
            confirmation.apply(provider, event);
        } catch (DataIntegrityViolationException duplicate) {
            // a parallel delivery of the same payment committed first, which is the answer we wanted
            log.info("Payment {} was recorded by a parallel delivery", event.providerPaymentId());
        }
    }

    private boolean alreadyRecorded(PaymentProvider provider, PaymentEvent event) {
        return event.providerPaymentId() != null
                && payments.findByProviderAndProviderPaymentId(provider, event.providerPaymentId()).isPresent();
    }
}
