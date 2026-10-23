package com.ariel.order_service.service;

import com.ariel.order_service.dto.OrderCreatedEvent;
import com.ariel.order_service.dto.PaymentResponse;
import com.ariel.order_service.entity.Order;
import com.ariel.order_service.entity.OrderItem;
import com.ariel.order_service.entity.ProcessedEvent;
import com.ariel.order_service.entity.Product;
import com.ariel.order_service.enums.OrderStatus;
import com.ariel.order_service.exception.OrderNotFoundException;
import com.ariel.order_service.repository.OrderRepository;
import com.ariel.order_service.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreatedConsumer {

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final ProcessedEventRepository processedEventRepository;

    public OrderCreatedConsumer(
            ObjectMapper objectMapper,
            OrderRepository orderRepository,
            PaymentClient paymentClient,
            ProcessedEventRepository processedEventRepository) {

        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "payment-group-v2"
    )
    @Transactional
    public void consume(String payload) {

        System.out.println(
                ">>> MENSAJE RECIBIDO DESDE KAFKA:"
        );

        OrderCreatedEvent event;

        try {

            event = objectMapper.readValue(
                    payload,
                    OrderCreatedEvent.class
            );

        } catch (JsonProcessingException e) {

            System.out.println(
                    ">>> ERROR DESERIALIZANDO EVENTO: "
                            + e.getMessage()
            );

            throw new RuntimeException(
                    "No fue posible deserializar OrderCreatedEvent",
                    e
            );
        }

        if (event.getEventId() == null) {

            System.out.println(
                    ">>> EVENTO ANTIGUO SIN eventId. SE IGNORA."
            );

            return;
        }

        if (processedEventRepository.existsByEventId(
                event.getEventId())) {

            System.out.println(
                    ">>> EVENTO YA PROCESADO: "
                            + event.getEventId()
            );

            return;
        }

        System.out.println(
                ">>> ORDER ID: " + event.getOrderId()
        );

        Order order = orderRepository
                .findById(event.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                event.getOrderId()
                        )
                );

        System.out.println(
                ">>> ORDER STATUS: "
                        + order.getStatus()
        );

        if (order.getStatus() != OrderStatus.CREATED) {

            System.out.println(
                    ">>> ORDEN YA PROCESADA. SE IGNORA."
            );

            return;
        }

        PaymentResponse payment =
                paymentClient.pay(
                        event.getUserId(),
                        event.getTotal()
                );

        System.out.println(
                ">>> PAYMENT APPROVED: "
                        + payment.isApproved()
        );

        if (payment.isApproved()) {

            order.setStatus(
                    OrderStatus.PAID
            );

            System.out.println(
                    ">>> ORDEN MARCADA COMO PAGADA."
            );

        } else {

            order.setStatus(
                    OrderStatus.CANCELLED
            );

            restoreStock(order);

            System.out.println(
                    ">>> ORDEN MARCADA COMO CANCELADA Y STOCK RESTAURADO."
            );
        }

        ProcessedEvent processedEvent =
                new ProcessedEvent(
                        event.getEventId(),
                        "payment-group-v2"
                );

        processedEventRepository.save(
                processedEvent
        );

        System.out.println(
                ">>> EVENTO REGISTRADO COMO PROCESADO: "
                        + event.getEventId()
        );
    }

    private void restoreStock(Order order) {

        for (OrderItem item : order.getItems()) {

            Product product = item.getProduct();

            product.setStock(
                    product.getStock()
                            + item.getQuantity()
            );
        }
    }
}