package com.ariel.order_service.dto;

import com.ariel.order_service.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {

    private Long id;
    private OrderStatus status;
    private BigDecimal total;
    private LocalDateTime createdAt;

    public OrderResponse(
            Long id,
            OrderStatus status,
            BigDecimal total,
            LocalDateTime createdAt) {

        this.id = id;
        this.status = status;
        this.total = total;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}