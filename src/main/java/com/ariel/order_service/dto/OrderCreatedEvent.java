package com.ariel.order_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderCreatedEvent {

    private String eventId;
    private Long orderId;
    private Long userId;
    private BigDecimal total;
    private LocalDateTime createdAt;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(
            String eventId,
            Long orderId,
            Long userId,
            BigDecimal total,
            LocalDateTime createdAt) {

        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.total = total;
        this.createdAt = createdAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}