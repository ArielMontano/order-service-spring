package com.ariel.order_service.controller;

import com.ariel.order_service.dto.CreateOrderRequest;
import com.ariel.order_service.dto.OrderCreationResult;
import com.ariel.order_service.dto.OrderResponse;
import com.ariel.order_service.exception.RateLimitExceededException;
import com.ariel.order_service.ratelimit.RateLimitService;
import com.ariel.order_service.service.OrderService;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final RateLimitService rateLimitService;
    

    public OrderController(OrderService orderService, RateLimitService rateLimitService) {
        this.orderService = orderService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request,
        @AuthenticationPrincipal Jwt jwt) {

        Long authenticatedUserId =
                jwt.getClaim("userId");

        if (!rateLimitService.tryConsume(
                authenticatedUserId)) {

                throw new RateLimitExceededException();
        }

        OrderCreationResult result =
                orderService.createOrder(
                        request,
                        idempotencyKey,
                        authenticatedUserId
                );

        if (result.isCreated()) {
                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(result.getResponse());
        }

        return ResponseEntity.ok(
                result.getResponse()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        boolean isAdmin = authentication
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals("ROLE_ADMIN")
                );

        if (isAdmin) {

            return ResponseEntity.ok(
                    orderService.getOrderByIdForAdmin(id)
            );
        }

        Long authenticatedUserId =
                jwt.getClaim("userId");

        return ResponseEntity.ok(
                orderService.getOrderByIdForUser(
                        id,
                        authenticatedUserId
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {

        return ResponseEntity.ok(
                orderService.getAllOrders()
        );
    }
}