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
 * Used both for reserving stock (e.g. an order was placed) and releasing a
 * previous reservation (e.g. the order was cancelled) - always a positive
 * quantity, the endpoint determines the direction.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationRequestDTO {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;
}
