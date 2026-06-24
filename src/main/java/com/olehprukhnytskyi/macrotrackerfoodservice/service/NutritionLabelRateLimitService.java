package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.exception.NutritionLabelRateLimitExceededException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NutritionLabelRateLimitService {
    private static final String KEY_PREFIX = "nutrition-label-scan:";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[2])
                    end
                    if current > tonumber(ARGV[1]) then
                        redis.call('DECR', KEYS[1])
                        return -1
                    end
                    return current
                    """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if current > 0 then
                        return redis.call('DECR', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final GeminiProperties geminiProperties;

    public Reservation reserve(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(scanProperties().getRateLimitZone());
        LocalDate date = now.toLocalDate();
        long retryAfterSeconds = secondsUntilNextDay(now);
        String key = buildKey(userId, date);

        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(key),
                String.valueOf(scanProperties().getDailyLimit()),
                String.valueOf(retryAfterSeconds)
        );
        if (result < 0) {
            throw new NutritionLabelRateLimitExceededException(retryAfterSeconds);
        }
        return new Reservation(key);
    }

    public void release(Reservation reservation) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(reservation.key()));
    }

    private GeminiProperties.NutritionLabelScan scanProperties() {
        return geminiProperties.getNutritionLabelScan();
    }

    private String buildKey(Long userId, LocalDate date) {
        return KEY_PREFIX + userId + ":" + date;
    }

    private long secondsUntilNextDay(ZonedDateTime now) {
        ZonedDateTime nextDay = now.toLocalDate()
                .plusDays(1)
                .atStartOfDay(now.getZone());
        return Math.max(1, Duration.between(now, nextDay).toSeconds());
    }

    public record Reservation(String key) {
    }
}
