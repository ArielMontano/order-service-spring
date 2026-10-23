package com.ariel.order_service.service;

import com.ariel.order_service.dto.PaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentClient {

    private final PaymentService paymentService;

    public PaymentClient(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @CircuitBreaker(name = "paymentService")
    public PaymentResponse pay(
            Long userId,
            BigDecimal amount) {

        return paymentService.processPayment(
                userId,
                amount
        );
    }
}