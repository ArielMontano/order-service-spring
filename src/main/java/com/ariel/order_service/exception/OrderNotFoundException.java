package com.ariel.order_service.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Orden no encontrada: " + orderId);
    }
}