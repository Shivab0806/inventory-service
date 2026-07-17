package com.example.inventoryservice.exception;

/**
 * Thrown when a reserve operation requests more units than are currently
 * available (on-hand minus already-reserved).
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
