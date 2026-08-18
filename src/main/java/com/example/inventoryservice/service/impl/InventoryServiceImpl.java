package com.example.inventoryservice.service.impl;

import com.example.inventoryservice.dto.*;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.exception.DuplicateResourceException;
import com.example.inventoryservice.exception.OperationNotAllowedException;
import com.example.inventoryservice.exception.InsufficientStockException;
import com.example.inventoryservice.exception.ResourceNotFoundException;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.repository.InventoryRepository;
import com.example.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional
    public InventoryResponseDTO create(InventoryCreateRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new DuplicateResourceException(
                    "An inventory record already exists for productId " + request.getProductId());
        }
        // Prevent duplicate SKUs (case-insensitive) with a clear business error
        String skuTrimmed = request.getSku() != null ? request.getSku().trim() : null;
        if (skuTrimmed != null && inventoryRepository.findBySkuIgnoreCase(skuTrimmed).isPresent()) {
            throw new DuplicateResourceException("An inventory record with SKU '" + skuTrimmed + "' already exists");
        }
        Inventory saved = inventoryRepository.save(inventoryMapper.toEntity(request));
        log.info("Created inventory id={} productId={} sku={}", saved.getId(), saved.getProductId(), saved.getSku());
        return inventoryMapper.toResponseDto(saved);
    }

    @Override
    public InventoryResponseDTO getById(Long id) {
        return inventoryMapper.toResponseDto(findOrThrow(id));
    }

    @Override
    public InventoryResponseDTO getByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for productId: " + productId));
        return inventoryMapper.toResponseDto(inventory);
    }

    @Override
    public PagedResponse<InventoryResponseDTO> search(String sku, Pageable pageable) {
        Page<Inventory> page = StringUtils.hasText(sku)
                ? inventoryRepository.findBySkuContainingIgnoreCase(sku, pageable)
                : inventoryRepository.findAll(pageable);
        return PagedResponse.from(page.map(inventoryMapper::toResponseDto));
    }

    @Override
    @Transactional
    public InventoryResponseDTO update(Long id, InventoryUpdateRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        Inventory inventory = findOrThrow(id);

        String skuTrimmed = request.getSku() != null ? request.getSku().trim() : null;
        if (skuTrimmed != null && inventoryRepository.existsBySkuIgnoreCaseAndIdNot(skuTrimmed, id)) {
            throw new DuplicateResourceException("An inventory record with SKU '" + skuTrimmed + "' already exists");
        }

        if (skuTrimmed != null) {
            inventory.setSku(skuTrimmed);
        }
        if (request.getReorderThreshold() != null) {
            inventory.setReorderThreshold(request.getReorderThreshold());
        }
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Updated inventory id={} sku={}", saved.getId(), saved.getSku());
        return inventoryMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Inventory inventory = findOrThrow(id);
        if (inventory.getQuantityOnHand() != null && inventory.getQuantityOnHand() > 0
                || inventory.getQuantityReserved() != null && inventory.getQuantityReserved() > 0) {
            throw new OperationNotAllowedException(
                    "Cannot delete inventory id " + id + " with non-zero quantities or reservations");
        }
        inventoryRepository.delete(inventory);
        log.info("Deleted inventory id={}", id);
    }

    @Override
    @Transactional
    public InventoryResponseDTO adjustStock(Long id, StockAdjustmentRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        Inventory inventory = findForUpdateOrThrow(id);

        int newOnHand = getNewOnHand(id, request, inventory);

        inventory.setQuantityOnHand(newOnHand);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Adjusted stock id={} delta={} reason='{}' newOnHand={}",
                id, request.getQuantity(), request.getReason(), newOnHand);
        return inventoryMapper.toResponseDto(saved);
    }

    private static int getNewOnHand(Long id, StockAdjustmentRequestDTO request, Inventory inventory) {
        int newOnHand = inventory.getQuantityOnHand() + request.getQuantity();
        if (newOnHand < 0) {
            throw new InsufficientStockException(
                    "Adjustment of " + request.getQuantity()
                            + " would result in negative on-hand quantity for inventory id " + id);
        }
        if (newOnHand < inventory.getQuantityReserved()) {
            throw new InsufficientStockException(
                    "Adjustment of " + request.getQuantity()
                            + " would drop on-hand below already-reserved quantity for inventory id " + id);
        }
        return newOnHand;
    }

    @Override
    @Transactional
    public InventoryResponseDTO reserveStock(Long id, StockReservationRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        Inventory inventory = findForUpdateOrThrow(id);

        int available = inventory.getQuantityAvailable();
        if (request.getQuantity() > available) {
            throw new InsufficientStockException(
                    "Cannot reserve " + request.getQuantity() + " units for inventory id " + id
                            + ": only " + available + " available");
        }

        inventory.setQuantityReserved(inventory.getQuantityReserved() + request.getQuantity());
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Reserved {} units for inventory id={} reference='{}'", request.getQuantity(), id,
                request.getReference());
        return inventoryMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public InventoryResponseDTO releaseStock(Long id, StockReservationRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        Inventory inventory = findForUpdateOrThrow(id);

        if (request.getQuantity() > inventory.getQuantityReserved()) {
            throw new InsufficientStockException(
                    "Cannot release " + request.getQuantity() + " units for inventory id " + id
                            + ": only " + inventory.getQuantityReserved() + " currently reserved");
        }

        inventory.setQuantityReserved(inventory.getQuantityReserved() - request.getQuantity());
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Released {} units for inventory id={} reference='{}'", request.getQuantity(), id,
                request.getReference());
        return inventoryMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public InventoryResponseDTO fulfillReservation(Long id, StockReservationRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");
        Inventory inventory = findForUpdateOrThrow(id);

        if (request.getQuantity() > inventory.getQuantityReserved()) {
            throw new InsufficientStockException(
                    "Cannot fulfill " + request.getQuantity() + " units for inventory id " + id
                            + ": only " + inventory.getQuantityReserved() + " currently reserved");
        }

        if (request.getQuantity() > inventory.getQuantityOnHand()) {
            throw new InsufficientStockException(
                    "Cannot fulfill " + request.getQuantity() + " units for inventory id " + id
                            + ": only " + inventory.getQuantityOnHand() + " on-hand");
        }

        inventory.setQuantityReserved(inventory.getQuantityReserved() - request.getQuantity());
        inventory.setQuantityOnHand(inventory.getQuantityOnHand() - request.getQuantity());
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Fulfilled {} units for inventory id={} reference='{}'", request.getQuantity(), id,
                request.getReference());
        return inventoryMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public InventoryResponseDTO processReturn(Long id, StockReturnRequestDTO request) {
        Objects.requireNonNull(request, "request must not be null");

        // Log idempotency key if provided (basic support; callers may use for de-duplication)
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            log.info("Processing return id={} idempotencyKey={}", id, request.getIdempotencyKey());
        }

        if (Boolean.TRUE.equals(request.getFulfilled())) {
            StockAdjustmentRequestDTO adjust = StockAdjustmentRequestDTO.builder()
                    .quantity(request.getQuantity())
                    .reason(request.getReason())
                    .build();
            return adjustStock(id, adjust);
        } else {
            StockReservationRequestDTO release = StockReservationRequestDTO.builder()
                    .quantity(request.getQuantity())
                    .reference(request.getReference())
                    .build();
            return releaseStock(id, release);
        }
    }

    private Inventory findOrThrow(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.forId("Inventory", id));
    }

    private Inventory findForUpdateOrThrow(Long id) {
        return inventoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.forId("Inventory", id));
    }
}
