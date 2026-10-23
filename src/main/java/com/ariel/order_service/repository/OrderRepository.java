package com.ariel.order_service.repository;

import com.ariel.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}