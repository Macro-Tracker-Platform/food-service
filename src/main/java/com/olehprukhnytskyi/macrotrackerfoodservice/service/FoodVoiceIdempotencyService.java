package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FoodVoiceIdempotencyService {
    private static final String PREFIX = "scans:food-voice:idempotency:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;

    public FoodPhotoScanResponseDto execute(Long userId, String idempotencyKey,
                                             Function<String, FoodPhotoScanResponseDto> action) {
        String normalized = normalize(idempotencyKey);
        if (normalized == null) {
            return action.apply(UUID.randomUUID().toString());
        }
        String operationToken = digest(userId + ":" + normalized);
        String resultKey = PREFIX + operationToken + ":result";
        FoodPhotoScanResponseDto cached = read(resultKey);
        if (cached != null) {
            return cached;
        }
        String lockKey = PREFIX + operationToken + ":lock";
        String lockToken = UUID.randomUUID().toString();
        Duration lockTtl = Duration.ofSeconds(
                properties.getFoodVoiceScan().getReservationTtlSeconds());
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            cached = read(resultKey);
            if (cached != null) {
                return cached;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A voice scan with this Idempotency-Key is already in progress");
        }
        try {
            FoodPhotoScanResponseDto response = action.apply(operationToken);
            write(resultKey, response);
            return response;
        } finally {
            redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), lockToken);
        }
    }

    private FoodPhotoScanResponseDto read(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FoodPhotoScanResponseDto.class);
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(key);
            return null;
        }
    }

    private void write(String key, FoodPhotoScanResponseDto response) {
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(response),
                    Duration.ofHours(properties.getFoodPhotoScan().getIdempotencyTtlHours())
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store idempotent voice scan response",
                    exception);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 128 characters");
        }
        return normalized;
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
