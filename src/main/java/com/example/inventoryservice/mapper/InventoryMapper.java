package com.example.inventoryservice.mapper;

import com.example.inventoryservice.dto.InventoryCreateRequestDTO;
import com.example.inventoryservice.dto.InventoryResponseDTO;
import com.example.inventoryservice.entity.Inventory;
import org.springframework.stereotype.Component;

/**
 * Explicit, hand-written mapping (no reflection-based magic) keeps the
 * entity <-> DTO boundary easy to reason about and debug.
 */
@Component
public class InventoryMapper {

    public Inventory toEntity(InventoryCreateRequestDTO dto) {
        return Inventory.builder()
                .productId(dto.getProductId())
                .sku(dto.getSku().trim())
                .quantityOnHand(dto.getInitialQuantity())
                .quantityReserved(0)
                .reorderThreshold(dto.getReorderThreshold() != null ? dto.getReorderThreshold() : 10)
                .build();
    }

    public InventoryResponseDTO toResponseDto(Inventory inventory) {
        return InventoryResponseDTO.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .sku(inventory.getSku())
                .quantityOnHand(inventory.getQuantityOnHand())
                .quantityReserved(inventory.getQuantityReserved())
                .quantityAvailable(inventory.getQuantityAvailable())
                .reorderThreshold(inventory.getReorderThreshold())
                .lowStock(inventory.isLowStock())
                .version(inventory.getVersion())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
