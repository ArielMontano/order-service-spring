package com.ariel.order_service.exception;

public class RateLimitExceededException
        extends RuntimeException {

    public RateLimitExceededException() {
        super("Se excedió el límite de solicitudes. Intente nuevamente más tarde.");
    }
}