package com.ariel.order_service.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_processed_event_id",
                        columnNames = "event_id"
                )
        }
)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String consumer;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(
            String eventId,
            String consumer) {

        this.eventId = eventId;
        this.consumer = consumer;
    }

    @PrePersist
    public void prePersist() {

        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}