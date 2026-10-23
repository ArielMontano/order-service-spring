package com.ariel.order_service.exception;

public class ConcurrentStockUpdateException extends RuntimeException {

    public ConcurrentStockUpdateException(Long productId) {
        super(
                "El stock del producto "
                        + productId
                        + " fue modificado por otra transacción"
        );
    }
}