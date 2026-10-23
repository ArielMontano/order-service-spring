package com.ariel.order_service.entity;

import com.ariel.order_service.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(unique = true)
    private String idempotencyKey;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(
            Long userId,
            OrderStatus status,
            BigDecimal total,
            String idempotencyKey) {

        this.userId = userId;
        this.status = status;
        this.total = total;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void setIdempotencyKey(String idempotencyKey2) {
        this.idempotencyKey = idempotencyKey2;
    }
}