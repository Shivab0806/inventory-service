package com.example.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Inventory is intentionally decoupled from Product Service: it only stores
 * {@code productId} (and a denormalized {@code sku} for convenient lookups),
 * never a foreign key or JPA relationship into Product's database. The two
 * services own separate schemas and can be deployed/scaled independently.
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_product_id", columnNames = "product_id"),
        @UniqueConstraint(name = "uk_inventory_sku", columnNames = "sku")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // Reference to Product Service's Product.id - no FK constraint, no join.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Denormalized copy of Product's SKU, kept for fast/independent lookups.
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    @Builder.Default
    private Integer quantityReserved = 0;

    @Column(name = "reorder_threshold", nullable = false)
    @Builder.Default
    private Integer reorderThreshold = 10;

    // Optimistic locking - stock mutations are concurrent by nature (orders,
    // restocks, corrections).
    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    public int getQuantityAvailable() {
        return quantityOnHand - quantityReserved;
    }

    @Transient
    public boolean isLowStock() {
        return getQuantityAvailable() <= reorderThreshold;
    }
}
