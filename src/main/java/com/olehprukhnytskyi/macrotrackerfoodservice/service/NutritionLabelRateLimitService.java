package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.exception.NutritionLabelRateLimitExceededException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NutritionLabelRateLimitService {
    private static final String MONTHLY_PREFIX = "nutrition-scan:monthly:";
    private static final String DAILY_PREFIX = "nutrition-scan:daily:";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<>("""
                    local monthly = tonumber(redis.call('GET', KEYS[1]) or '0')
                    local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
                    if monthly >= tonumber(ARGV[1]) then return -1 end
                    if daily >= tonumber(ARGV[2]) then return -2 end
                    monthly = redis.call('INCR', KEYS[1])
                    daily = redis.call('INCR', KEYS[2])
                    if monthly == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
                    if daily == 1 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
                    return monthly
                    """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    local monthly = tonumber(redis.call('GET', KEYS[1]) or '0')
                    local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
                    if monthly > 0 then redis.call('DECR', KEYS[1]) end
                    if daily > 0 then redis.call('DECR', KEYS[2]) end
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final GeminiProperties geminiProperties;

    public Reservation reserve(Long userId, int monthlyLimit) {
        ZonedDateTime now = ZonedDateTime.now(scanProperties().getRateLimitZone());
        YearMonth month = YearMonth.from(now);
        Instant monthlyReset = month.plusMonths(1).atDay(1)
                .atStartOfDay(now.getZone()).toInstant();
        Instant dailyReset = now.toLocalDate().plusDays(1)
                .atStartOfDay(now.getZone()).toInstant();
        String monthlyKey = MONTHLY_PREFIX + userId + ":" + month;
        String dailyKey = DAILY_PREFIX + userId + ":" + now.toLocalDate();
        long monthlyTtl = secondsUntil(now.toInstant(), monthlyReset);
        long dailyTtl = secondsUntil(now.toInstant(), dailyReset);

        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(monthlyKey, dailyKey),
                String.valueOf(monthlyLimit),
                String.valueOf(scanProperties().getDailyLimit()),
                String.valueOf(monthlyTtl),
                String.valueOf(dailyTtl)
        );
        if (result == null) {
            throw new IllegalStateException("Could not reserve nutrition scan quota");
        }
        if (result == -1L) {
            throw new NutritionLabelRateLimitExceededException(
                    "monthly", monthlyTtl, monthlyLimit, monthlyReset);
        }
        if (result == -2L) {
            throw new NutritionLabelRateLimitExceededException(
                    "daily", dailyTtl, scanProperties().getDailyLimit(), dailyReset);
        }
        return new Reservation(monthlyKey, dailyKey, monthlyLimit,
                result.intValue(), monthlyReset);
    }

    public void release(Reservation reservation) {
        redisTemplate.execute(RELEASE_SCRIPT,
                List.of(reservation.monthlyKey(), reservation.dailyKey()));
    }

    private GeminiProperties.NutritionLabelScan scanProperties() {
        return geminiProperties.getNutritionLabelScan();
    }

    private long secondsUntil(Instant now, Instant resetAt) {
        return Math.max(1, Duration.between(now, resetAt).toSeconds());
    }

    public record Reservation(String monthlyKey, String dailyKey, int monthlyLimit,
                              int monthlyUsed, Instant monthlyResetAt) {
        public int remaining() {
            return Math.max(0, monthlyLimit - monthlyUsed);
        }
    }
}
