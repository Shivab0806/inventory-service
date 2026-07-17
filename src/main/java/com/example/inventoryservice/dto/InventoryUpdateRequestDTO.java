package com.example.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Updates metadata only. Stock levels are never edited via a blind PUT -
 * they change only through the explicit adjust/reserve/release operations,
 * which keeps every stock movement intentional and auditable.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateRequestDTO {

    @NotBlank(message = "SKU must not be blank")
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,64}$", message = "SKU must be 3-64 chars: letters, digits, '-' or '_'")
    private String sku;

    @Min(value = 0, message = "Reorder threshold cannot be negative")
    private Integer reorderThreshold;
}
