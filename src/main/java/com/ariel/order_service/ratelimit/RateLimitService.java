package com.ariel.order_service.ratelimit;

import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<Long, Bucket> buckets =
            new ConcurrentHashMap<>();

    public boolean tryConsume(Long userId) {

        Bucket bucket = buckets.computeIfAbsent(
                userId,
                this::createBucket
        );

        return bucket.tryConsume(1);
    }

    private Bucket createBucket(Long userId) {

        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(1))
                )
                .build();
    }
}