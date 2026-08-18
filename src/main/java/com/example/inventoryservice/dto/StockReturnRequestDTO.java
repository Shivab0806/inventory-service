package com.example.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an incoming return or cancellation for inventory processing.
 * If `fulfilled` is false the endpoint will release a previous reservation;
 * if true it will add units back to on-hand (a customer return after shipment).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReturnRequestDTO {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;

    @NotNull(message = "fulfilled flag is required")
    private Boolean fulfilled;

    /** Optional idempotency key header value for callers to provide. Not persisted by service. */
    private String idempotencyKey;
}