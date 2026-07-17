package com.ticketing.tickettype;

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
import lombok.Setter;

@Entity
@Table(name = "ticket_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketType extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    private String description;

    @Setter
    @Column(nullable = false)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR) // column is CHAR(3), not VARCHAR
    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Setter
    @Column(name = "quantity_total", nullable = false)
    private int quantityTotal;

    @Column(name = "quantity_sold", nullable = false)
    private int quantitySold = 0;

    @Setter
    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder;

    @Setter
    @Column(name = "sales_start_at", nullable = false)
    private Instant salesStartAt;

    @Setter
    @Column(name = "sales_end_at", nullable = false)
    private Instant salesEndAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketTypeStatus status = TicketTypeStatus.ACTIVE;

    @Version
    private long version;

    public TicketType(UUID id, UUID eventId, String name, String description, BigDecimal price,
            int quantityTotal, int maxPerOrder, Instant salesStartAt, Instant salesEndAt) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantityTotal = quantityTotal;
        this.maxPerOrder = maxPerOrder;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
    }

    public boolean hasSales() {
        return quantitySold > 0;
    }
}
