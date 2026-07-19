package com.ticketing.order;

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
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @JdbcTypeCode(SqlTypes.CHAR) // column is CHAR(3), not VARCHAR
    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    // fingerprint of the original request; a same-key retry with a different payload is a conflict
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    public Order(UUID id, String orderNumber, UUID userId, UUID eventId,
            BigDecimal subtotal, BigDecimal fees, BigDecimal grandTotal,
            String idempotencyKey, String requestHash) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.eventId = eventId;
        this.subtotal = subtotal;
        this.fees = fees;
        this.grandTotal = grandTotal;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    /** Starts the payment window; the seats stay held until this moment passes. */
    public void holdUntil(Instant deadline) {
        this.expiresAt = deadline;
    }

    public void confirm(Instant now) {
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    // EXPIRED and CANCELLED share cancelled_at; the status says which of the two ended the order
    public void expire(Instant now) {
        this.status = OrderStatus.EXPIRED;
        this.cancelledAt = now;
    }

    public void cancel(Instant now) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public boolean isAwaitingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isDueForExpiry(Instant now) {
        return isAwaitingPayment() && expiresAt != null && !now.isBefore(expiresAt);
    }
}
