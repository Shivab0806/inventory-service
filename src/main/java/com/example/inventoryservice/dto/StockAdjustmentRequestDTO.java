package com.example.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.example.inventoryservice.validation.NotZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Adjusts quantity-on-hand by a signed delta: positive for restocks/returns,
 * negative for damage, loss, or manual corrections.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequestDTO {

    @NotNull(message = "quantity delta is required")
    @NotZero(message = "quantity must not be zero")
    private Integer quantity;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;
}
