package com.ticketing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ticketing.shared.jpa.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    // the provider's own payment identifier; unique per provider, which is what stops a replayed webhook
    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    // kept for tracing a confirmation back to the exact delivery that caused it
    @Column(name = "provider_event_id")
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR) // column is CHAR(3), not VARCHAR
    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "paid_at")
    private Instant paidAt;

    public Payment(UUID id, UUID orderId, PaymentProvider provider, BigDecimal amount, String currency) {
        this.id = id;
        this.orderId = orderId;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
    }

    public void markSucceeded(String providerPaymentId, String providerEventId, Instant paidAt) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
        this.providerEventId = providerEventId;
        this.paidAt = paidAt;
    }

    public void markFailed(String providerPaymentId, String providerEventId, String failureCode) {
        this.status = PaymentStatus.FAILED;
        this.providerPaymentId = providerPaymentId;
        this.providerEventId = providerEventId;
        this.failureCode = failureCode;
    }
}
