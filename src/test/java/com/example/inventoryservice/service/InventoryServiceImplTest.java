package com.example.inventoryservice.service;

import com.example.inventoryservice.dto.*;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.exception.DuplicateResourceException;
import com.example.inventoryservice.exception.InsufficientStockException;
import com.example.inventoryservice.exception.ResourceNotFoundException;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.repository.InventoryRepository;
import com.example.inventoryservice.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private InventoryCreateRequestDTO createRequest;
    private Inventory entity;

    @BeforeEach
    void setUp() {
        createRequest = InventoryCreateRequestDTO.builder()
                .productId(1L)
                .sku("SKU-001")
                .initialQuantity(100)
                .reorderThreshold(10)
                .build();

        entity = Inventory.builder()
                .id(1L)
                .productId(1L)
                .sku("SKU-001")
                .quantityOnHand(100)
                .quantityReserved(0)
                .reorderThreshold(10)
                .version(0L)
                .build();
    }

    @Test
    void create_savesAndReturnsInventory_whenProductIdIsUnique() {
        when(inventoryRepository.existsByProductId(1L)).thenReturn(false);
        when(inventoryMapper.toEntity(createRequest)).thenReturn(entity);
        when(inventoryRepository.save(entity)).thenReturn(entity);
        when(inventoryMapper.toResponseDto(entity)).thenReturn(
                InventoryResponseDTO.builder().id(1L).productId(1L).sku("SKU-001").build());

        InventoryResponseDTO result = inventoryService.create(createRequest);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProductId()).isEqualTo(1L);
        verify(inventoryRepository).save(entity);
    }

    @Test
    void create_throwsDuplicateResourceException_whenProductIdAlreadyExists() {
        when(inventoryRepository.existsByProductId(1L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.create(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("1");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void getById_throwsResourceNotFoundException_whenMissing() {
        when(inventoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void reserveStock_reducesAvailable_whenEnoughStock() {
        when(inventoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryMapper.toResponseDto(any(Inventory.class))).thenAnswer(inv -> {
            Inventory i = inv.getArgument(0);
            return InventoryResponseDTO.builder()
                    .id(i.getId()).quantityOnHand(i.getQuantityOnHand()).quantityReserved(i.getQuantityReserved())
                    .quantityAvailable(i.getQuantityAvailable()).build();
        });

        StockReservationRequestDTO request = StockReservationRequestDTO.builder().quantity(30).reference("ORDER-1").build();
        InventoryResponseDTO result = inventoryService.reserveStock(1L, request);

        assertThat(result.getQuantityReserved()).isEqualTo(30);
        assertThat(result.getQuantityAvailable()).isEqualTo(70);
    }

    @Test
    void reserveStock_throwsInsufficientStockException_whenRequestExceedsAvailable() {
        when(inventoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        StockReservationRequestDTO request = StockReservationRequestDTO.builder().quantity(500).build();

        assertThatThrownBy(() -> inventoryService.reserveStock(1L, request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("500");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseStock_throwsInsufficientStockException_whenReleasingMoreThanReserved() {
        entity.setQuantityReserved(10);
        when(inventoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        StockReservationRequestDTO request = StockReservationRequestDTO.builder().quantity(20).build();

        assertThatThrownBy(() -> inventoryService.releaseStock(1L, request))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void adjustStock_throwsInsufficientStockException_whenResultWouldGoNegative() {
        when(inventoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));

        StockAdjustmentRequestDTO request = StockAdjustmentRequestDTO.builder().quantity(-200).reason("damage").build();

        assertThatThrownBy(() -> inventoryService.adjustStock(1L, request))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void delete_removesInventory_whenExists() {
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(entity));

        inventoryService.delete(1L);

        verify(inventoryRepository).delete(entity);
    }
}
