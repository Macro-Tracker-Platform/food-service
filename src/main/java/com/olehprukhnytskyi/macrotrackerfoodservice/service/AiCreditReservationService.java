package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.FoodPhotoScanLimitException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCreditReservationService {
    static final String INFLIGHT_KEY_PREFIX = "scans:ai:free:inflight:";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
                    local inflight = redis.call('ZCARD', KEYS[1])
                    if inflight >= tonumber(ARGV[1]) then return -1 end
                    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
                    redis.call('EXPIRE', KEYS[1], ARGV[5])
                    return inflight
                    """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("return redis.call('ZREM', KEYS[1], ARGV[1])",
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final EntitlementClient entitlementClient;
    private final GeminiProperties properties;

    public Reservation reserve(Long userId, String requestToken) {
        String token = requestToken == null || requestToken.isBlank()
                ? UUID.randomUUID().toString()
                : requestToken;
        FoodPhotoScanCreditDto credits = entitlementClient.getAiScanCredits(userId);
        if (credits == null || credits.getRemainingScans() <= 0) {
            throw freeLimitReached();
        }
        long now = Instant.now().getEpochSecond();
        long ttl = properties.getAiCredits().getReservationTtlSeconds();
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(inflightKey(userId)),
                String.valueOf(credits.getRemainingScans()),
                String.valueOf(now),
                String.valueOf(now + ttl),
                token,
                String.valueOf(ttl));
        if (result == null) {
            throw new IllegalStateException("Could not reserve free AI credit");
        }
        if (result < 0) {
            throw freeLimitReached();
        }
        return new Reservation(userId, token, credits.getLimit(),
                credits.getRemainingScans(), credits.getResetAt());
    }

    public QuotaSnapshot commit(Reservation reservation) {
        try {
            FoodPhotoScanCreditDto credits = entitlementClient.consumeAiScanCredit(
                    reservation.userId(), reservation.token());
            if (credits == null) {
                throw new IllegalStateException("Could not consume free AI credit");
            }
            if (!credits.isConsumed()) {
                throw freeLimitReached();
            }
            return new QuotaSnapshot(
                    credits.getLimit(), credits.getRemainingScans(), credits.getResetAt());
        } finally {
            release(reservation);
        }
    }

    public void release(Reservation reservation) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(inflightKey(reservation.userId())),
                reservation.token());
    }

    private String inflightKey(Long userId) {
        return INFLIGHT_KEY_PREFIX + userId;
    }

    private FoodPhotoScanLimitException freeLimitReached() {
        return new FoodPhotoScanLimitException(HttpStatus.FORBIDDEN, "FREE_LIMIT_REACHED");
    }

    public record Reservation(Long userId, String token, int limit, int remaining,
                              Instant resetAt) {
        public QuotaSnapshot snapshot() {
            return new QuotaSnapshot(limit, remaining, resetAt);
        }
    }

    public record QuotaSnapshot(int limit, int remaining, Instant resetAt) {
    }
}
