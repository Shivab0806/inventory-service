package com.example.inventoryservice.repository;

import com.example.inventoryservice.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long>, JpaSpecificationExecutor<Inventory> {

    Optional<Inventory> findByProductId(Long productId);

    Optional<Inventory> findBySkuIgnoreCase(String sku);

    boolean existsByProductId(Long productId);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    Page<Inventory> findBySkuContainingIgnoreCase(String sku, Pageable pageable);

    /**
     * Pessimistic write lock used for reserve/release/adjust operations so
     * concurrent stock mutations against the same row serialize at the DB
     * level instead of racing (belt-and-braces alongside @Version).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.id = :id")
    Optional<Inventory> findByIdForUpdate(Long id);
}
