package com.ariel.order_service.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId) {
        super("Stock insuficiente para producto: " + productId);
    }
}