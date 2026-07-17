package com.example.inventoryservice.controller;

import com.example.inventoryservice.dto.InventoryCreateRequestDTO;
import com.example.inventoryservice.dto.InventoryResponseDTO;
import com.example.inventoryservice.dto.StockReservationRequestDTO;
import com.example.inventoryservice.exception.InsufficientStockException;
import com.example.inventoryservice.exception.ResourceNotFoundException;
import com.example.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void create_returns201_whenPayloadIsValid() throws Exception {
        InventoryCreateRequestDTO request = InventoryCreateRequestDTO.builder()
                .productId(1L).sku("SKU-001").initialQuantity(100).build();
        InventoryResponseDTO response = InventoryResponseDTO.builder()
                .id(1L).productId(1L).sku("SKU-001").quantityOnHand(100).quantityAvailable(100).build();

        when(inventoryService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void create_returns400_whenProductIdMissing() throws Exception {
        InventoryCreateRequestDTO invalid = InventoryCreateRequestDTO.builder()
                .sku("SKU-001").initialQuantity(100).build();

        mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        when(inventoryService.getById(99L)).thenThrow(ResourceNotFoundException.forId("Inventory", 99L));

        mockMvc.perform(get("/api/v1/inventory/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void reserveStock_returns409_whenInsufficientStock() throws Exception {
        StockReservationRequestDTO request = StockReservationRequestDTO.builder().quantity(500).build();
        when(inventoryService.reserveStock(eq(1L), any()))
                .thenThrow(new InsufficientStockException("Cannot reserve 500 units for inventory id 1: only 100 available"));

        mockMvc.perform(post("/api/v1/inventory/{id}/reserve", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void delete_returns204_whenSuccessful() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/{id}", 1L))
                .andExpect(status().isNoContent());
    }
}
