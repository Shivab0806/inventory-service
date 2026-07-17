package com.example.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound payload to open a new inventory record for a product.
 * {@code productId} is the only link back to Product Service - deliberately
 * just an id, not a nested object or a call across services.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCreateRequestDTO {

    @NotNull(message = "productId is required")
    @Min(value = 1, message = "productId must be a positive number")
    private Long productId;

    @NotBlank(message = "SKU must not be blank")
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,64}$", message = "SKU must be 3-64 chars: letters, digits, '-' or '_'")
    private String sku;

    @NotNull(message = "Initial quantity is required")
    @Min(value = 0, message = "Initial quantity cannot be negative")
    private Integer initialQuantity;

    @Min(value = 0, message = "Reorder threshold cannot be negative")
    private Integer reorderThreshold;
}
