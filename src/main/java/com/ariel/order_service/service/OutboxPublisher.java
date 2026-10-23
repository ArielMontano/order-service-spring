package com.ariel.order_service.service;

import com.ariel.order_service.entity.OutboxEvent;
import com.ariel.order_service.enums.OutboxStatus;
import com.ariel.order_service.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                outboxEventRepository
                        .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate
                        .send(
                                "order-created",
                                event.getAggregateId().toString(),
                                event.getPayload()
                        )
                        .get();

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);

            } catch (Exception e) {
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
            }
        }
    }
}