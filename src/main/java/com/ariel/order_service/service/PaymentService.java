package com.ariel.order_service.service;

import com.ariel.order_service.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    public PaymentResponse processPayment(
            Long userId,
            BigDecimal amount) {
        return new PaymentResponse(
                true,
                UUID.randomUUID().toString()
        );
        /*throw new RuntimeException(
                "Payment Service no disponible"
        );*/
    }
}