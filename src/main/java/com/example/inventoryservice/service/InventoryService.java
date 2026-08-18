package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.*;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    InventoryResponseDTO create(InventoryCreateRequestDTO request);

    InventoryResponseDTO getById(Long id);

    InventoryResponseDTO getByProductId(Long productId);

    PagedResponse<InventoryResponseDTO> search(String sku, Pageable pageable);

    InventoryResponseDTO update(Long id, InventoryUpdateRequestDTO request);

    void delete(Long id);

    /** Applies a signed quantity delta to quantity-on-hand (restock, damage, correction). */
    InventoryResponseDTO adjustStock(Long id, StockAdjustmentRequestDTO request);

    /** Moves units from available into reserved (e.g. an order was placed). */
    InventoryResponseDTO reserveStock(Long id, StockReservationRequestDTO request);

    /** Moves previously reserved units back to available (e.g. an order was cancelled). */
    InventoryResponseDTO releaseStock(Long id, StockReservationRequestDTO request);

    /** Fulfills a reservation: removes the units from both reserved and on-hand (e.g. order shipped). */
    InventoryResponseDTO fulfillReservation(Long id, StockReservationRequestDTO request);

    /**
     * Processes an order cancellation or customer return.
     * - If `fulfilled` is false the method releases a previous reservation.
     * - If `fulfilled` is true the method adds units back to on-hand (customer return).
     */
    InventoryResponseDTO processReturn(Long id, StockReturnRequestDTO request);
}
