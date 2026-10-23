package com.ariel.order_service.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Ya existe un usuario registrado con email: " + email);
    }
}