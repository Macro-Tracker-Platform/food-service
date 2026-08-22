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
    private static final String REQUEST_DAILY_PREFIX = "nutrition-scan:request:daily:";
    private static final String SUCCESS_MONTHLY_PREFIX = "nutrition-scan:success:monthly:";
    private static final String SUCCESS_DAILY_PREFIX = "nutrition-scan:success:daily:";
    private static final String REQUEST_SCOPE = "daily";
    private static final String FREE_SUCCESS_SCOPE = "monthly";
    private static final String PREMIUM_SUCCESS_SCOPE = "premium-daily";
    private static final long REQUEST_WINDOW_SECONDS = Duration.ofHours(24).toSeconds();
    private static final DefaultRedisScript<Long> CHECK_SCRIPT =
            new DefaultRedisScript<>("""
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if used >= tonumber(ARGV[1]) then return -1 end
                    return used
                    """, Long.class);
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if used >= tonumber(ARGV[1]) then
                        local ttl = redis.call('TTL', KEYS[1])
                        if ttl < 1 then ttl = tonumber(ARGV[2]) end
                        return -ttl
                    end
                    used = redis.call('INCR', KEYS[1])
                    if used == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
                    return used
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final GeminiProperties geminiProperties;

    public RequestReservation reserveRequest(Long userId) {
        QuotaWindow window = requestDailyWindow(userId);
        Long result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(window.key()),
                String.valueOf(window.limit()),
                String.valueOf(window.ttlSeconds())
        );
        long used = requireResult(result, "Could not reserve nutrition label request quota");
        if (used < 0L) {
            throwLimitExceeded(window.withResetAfterSeconds(-used));
        }
        return new RequestReservation(window.limit(), (int) used, window.resetAt());
    }

    public SuccessfulScanQuota ensureSuccessfulScanQuotaAvailable(Long userId,
                                                                  boolean premium) {
        QuotaWindow window = successfulScanWindow(userId, premium);
        Long result = redisTemplate.execute(
                CHECK_SCRIPT,
                List.of(window.key()),
                String.valueOf(window.limit())
        );
        long used = requireResult(result, "Could not check nutrition label scan quota");
        if (used == -1L) {
            throwLimitExceeded(window);
        }
        return new SuccessfulScanQuota(window.scope(), window.limit(), (int) used,
                window.resetAt());
    }

    public SuccessfulScanQuota recordSuccessfulScan(Long userId, boolean premium) {
        QuotaWindow window = successfulScanWindow(userId, premium);
        Long result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(window.key()),
                String.valueOf(window.limit()),
                String.valueOf(window.ttlSeconds())
        );
        long used = requireResult(result, "Could not record nutrition label scan quota");
        if (used < 0L) {
            throwLimitExceeded(window);
        }
        return new SuccessfulScanQuota(window.scope(), window.limit(), (int) used,
                window.resetAt());
    }

    private QuotaWindow requestDailyWindow(Long userId) {
        GeminiProperties.NutritionLabelScan properties = scanProperties();
        Instant now = Instant.now();
        return new QuotaWindow(
                REQUEST_DAILY_PREFIX + userId,
                REQUEST_SCOPE,
                properties.getRequestDailyLimit(),
                now.plusSeconds(REQUEST_WINDOW_SECONDS),
                REQUEST_WINDOW_SECONDS
        );
    }

    private QuotaWindow successfulScanWindow(Long userId, boolean premium) {
        GeminiProperties.NutritionLabelScan properties = scanProperties();
        ZonedDateTime now = ZonedDateTime.now(properties.getRateLimitZone());
        if (premium) {
            Instant resetAt = now.toLocalDate().plusDays(1)
                    .atStartOfDay(now.getZone()).toInstant();
            return new QuotaWindow(
                    SUCCESS_DAILY_PREFIX + userId + ":" + now.toLocalDate(),
                    PREMIUM_SUCCESS_SCOPE,
                    properties.getProSuccessfulDailyLimit(),
                    resetAt,
                    secondsUntil(now.toInstant(), resetAt)
            );
        }
        YearMonth month = YearMonth.from(now);
        Instant resetAt = month.plusMonths(1).atDay(1)
                .atStartOfDay(now.getZone()).toInstant();
        return new QuotaWindow(
                SUCCESS_MONTHLY_PREFIX + userId + ":" + month,
                FREE_SUCCESS_SCOPE,
                properties.getFreeSuccessfulMonthlyLimit(),
                resetAt,
                secondsUntil(now.toInstant(), resetAt)
        );
    }

    private long requireResult(Long result, String message) {
        if (result == null) {
            throw new IllegalStateException(message);
        }
        return result;
    }

    private void throwLimitExceeded(QuotaWindow window) {
        throw new NutritionLabelRateLimitExceededException(
                window.scope(), window.ttlSeconds(), window.limit(), window.resetAt());
    }

    private GeminiProperties.NutritionLabelScan scanProperties() {
        return geminiProperties.getNutritionLabelScan();
    }

    private long secondsUntil(Instant now, Instant resetAt) {
        return Math.max(1, Duration.between(now, resetAt).toSeconds());
    }

    private record QuotaWindow(String key, String scope, int limit, Instant resetAt,
                               long ttlSeconds) {
        private QuotaWindow withResetAfterSeconds(long seconds) {
            return new QuotaWindow(key, scope, limit,
                    Instant.now().plusSeconds(seconds), seconds);
        }
    }

    public record RequestReservation(int limit, int used, Instant resetAt) {
        public int remaining() {
            return Math.max(0, limit - used);
        }
    }

    public record SuccessfulScanQuota(String scope, int limit, int used, Instant resetAt) {
        public int remaining() {
            return Math.max(0, limit - used);
        }
    }
}
