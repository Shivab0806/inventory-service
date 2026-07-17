package com.example.inventoryservice.controller;

import com.example.inventoryservice.dto.*;
import com.example.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock tracking and stock movement operations")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Open a new inventory record for a product")
    @PostMapping
    public ResponseEntity<InventoryResponseDTO> create(
            @Valid @RequestBody InventoryCreateRequestDTO request,
            UriComponentsBuilder uriBuilder) {
        InventoryResponseDTO created = inventoryService.create(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/inventory/{id}").buildAndExpand(created.getId()).toUri())
                .body(created);
    }

    @Operation(summary = "Get an inventory record by id")
    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getById(id));
    }

    @Operation(summary = "Get an inventory record by productId")
    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryResponseDTO> getByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getByProductId(productId));
    }

    @Operation(summary = "Search/list inventory records with pagination, sorting and optional SKU filter")
    @GetMapping
    public ResponseEntity<PagedResponse<InventoryResponseDTO>> search(
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort sort = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        return ResponseEntity.ok(inventoryService.search(sku, pageable));
    }

    @Operation(summary = "Update inventory metadata (SKU, reorder threshold) - not stock levels")
    @PutMapping("/{id}")
    public ResponseEntity<InventoryResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody InventoryUpdateRequestDTO request) {
        return ResponseEntity.ok(inventoryService.update(id, request));
    }

    @Operation(summary = "Delete an inventory record")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        inventoryService.delete(id);
    }

    @Operation(summary = "Adjust on-hand quantity by a signed delta (restock, damage, correction)")
    @PostMapping("/{id}/adjust")
    public ResponseEntity<InventoryResponseDTO> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody StockAdjustmentRequestDTO request) {
        return ResponseEntity.ok(inventoryService.adjustStock(id, request));
    }

    @Operation(summary = "Reserve stock for an order")
    @PostMapping("/{id}/reserve")
    public ResponseEntity<InventoryResponseDTO> reserveStock(
            @PathVariable Long id,
            @Valid @RequestBody StockReservationRequestDTO request) {
        return ResponseEntity.ok(inventoryService.reserveStock(id, request));
    }

    @Operation(summary = "Release previously reserved stock back to available")
    @PostMapping("/{id}/release")
    public ResponseEntity<InventoryResponseDTO> releaseStock(
            @PathVariable Long id,
            @Valid @RequestBody StockReservationRequestDTO request) {
        return ResponseEntity.ok(inventoryService.releaseStock(id, request));
    }

    @Operation(summary = "Fulfill a reservation - removes units from both reserved and on-hand")
    @PostMapping("/{id}/fulfill")
    public ResponseEntity<InventoryResponseDTO> fulfillReservation(
            @PathVariable Long id,
            @Valid @RequestBody StockReservationRequestDTO request) {
        return ResponseEntity.ok(inventoryService.fulfillReservation(id, request));
    }
}
