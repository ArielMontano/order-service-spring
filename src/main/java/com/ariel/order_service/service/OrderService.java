package com.ariel.order_service.service;

import com.ariel.order_service.dto.CreateOrderRequest;
import com.ariel.order_service.dto.OrderCreatedEvent;
import com.ariel.order_service.dto.OrderCreationResult;
import com.ariel.order_service.dto.OrderItemRequest;
import com.ariel.order_service.dto.OrderResponse;
import com.ariel.order_service.entity.Order;
import com.ariel.order_service.entity.OrderItem;
import com.ariel.order_service.entity.Product;
import com.ariel.order_service.enums.OrderStatus;
import com.ariel.order_service.exception.InsufficientStockException;
import com.ariel.order_service.exception.OrderNotFoundException;
import com.ariel.order_service.exception.ProductNotFoundException;
import com.ariel.order_service.repository.OrderRepository;
import com.ariel.order_service.repository.OutboxEventRepository;
import com.ariel.order_service.repository.ProductRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ariel.order_service.entity.OutboxEvent;
import com.ariel.order_service.enums.OutboxStatus;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    
    public OrderService(
        OrderRepository orderRepository,
        ProductRepository productRepository,
        OutboxEventRepository outboxEventRepository,
        ObjectMapper objectMapper) {

                this.orderRepository = orderRepository;
                this.productRepository = productRepository;
                this.outboxEventRepository = outboxEventRepository;
                this.objectMapper = objectMapper;
        }

    @Transactional
    public OrderCreationResult createOrder(
        CreateOrderRequest request,
        String idempotencyKey,
        Long authenticatedUserId) {

        Optional<Order> existingOrder =
        orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
                OrderResponse response = new OrderResponse(
                        existingOrder.get().getId(),
                        existingOrder.get().getStatus(),
                        existingOrder.get().getTotal(),
                        existingOrder.get().getCreatedAt()
                );
                return new OrderCreationResult(
                    response,
                    false
                );
        }

        Order order = new Order();

        order.setUserId(authenticatedUserId);
        order.setStatus(OrderStatus.CREATED);
        order.setIdempotencyKey(idempotencyKey);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            Product product = productRepository
                    .findById(itemRequest.getProductId())
                    .orElseThrow(() ->
                            new ProductNotFoundException(itemRequest.getProductId())
                    );

            if (product.getStock() < itemRequest.getQuantity()) {

                throw new InsufficientStockException(product.getId());
            }

            product.setStock(product.getStock() - itemRequest.getQuantity());

            BigDecimal subtotal = product
                    .getPrice()
                    .multiply(
                            BigDecimal.valueOf(
                                    itemRequest.getQuantity()
                            )
                    );

            OrderItem orderItem = new OrderItem();

            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(product.getPrice());

            order.addItem(orderItem);

            total = total.add(subtotal);
        }

        order.setTotal(total);

        Order savedOrder =
                orderRepository.save(order);

        OrderCreatedEvent event =
        new OrderCreatedEvent(
                java.util.UUID.randomUUID().toString(),
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTotal(),
                savedOrder.getCreatedAt()
        );

        String payload;
        try {
                payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
                throw new RuntimeException("No fue posible serializar OrderCreatedEvent",e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();

        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setAggregateId(savedOrder.getId());
        outboxEvent.setEventType("ORDER_CREATED");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);

        outboxEventRepository.save(outboxEvent);

        OrderResponse response = new OrderResponse(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getTotal(),
                savedOrder.getCreatedAt()
        );

        return new OrderCreationResult(response, true);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForUser(
            Long orderId,
            Long authenticatedUserId) {

        Order order = orderRepository
            .findByIdAndUserId(
                    orderId,
                    authenticatedUserId
            )
            .orElseThrow(() ->
                    new OrderNotFoundException(orderId)
            );

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(
            Long orderId) {

        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() ->
                        new OrderNotFoundException(orderId)
                );

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long authenticatedUserId) {
        Order order = orderRepository
                .findByIdAndUserId(orderId, authenticatedUserId)
                .orElseThrow(() ->
                        new RuntimeException("Orden no encontrada")
                );

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                order.getCreatedAt()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {

        return orderRepository
            .findAll()
            .stream()
            .map(order ->
                    new OrderResponse(
                            order.getId(),
                            order.getStatus(),
                            order.getTotal(),
                            order.getCreatedAt()
                    )
            )
            .toList();
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                order.getCreatedAt()
        );
    }

}