package com.ticketing.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // replay lookup: a provider payment already on file means this event was handled before
    Optional<Payment> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
