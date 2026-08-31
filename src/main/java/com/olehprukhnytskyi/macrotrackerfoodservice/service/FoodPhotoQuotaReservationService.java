package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.FoodPhotoScanLimitException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodPhotoQuotaReservationService {
    static final String PREMIUM_PREFIX = "scans:premium:";
    static final String PREMIUM_INFLIGHT_PREFIX = "scans:premium:inflight:";
    static final String PREMIUM_COMPLETED_PREFIX = "scans:premium:completed:";
    static final String FREE_INFLIGHT_PREFIX = "scans:free:inflight:";
    private static final long PREMIUM_TTL_SECONDS = Duration.ofHours(24).toSeconds();

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    local inflight = redis.call('ZCARD', KEYS[2])
                    if used + inflight >= tonumber(ARGV[1]) then return -1 end
                    redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
                    redis.call('EXPIRE', KEYS[2], ARGV[5])
                    return used
                    """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE_FREE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
                    local inflight = redis.call('ZCARD', KEYS[1])
                    if inflight >= tonumber(ARGV[1]) then return -1 end
                    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
                    redis.call('EXPIRE', KEYS[1], ARGV[5])
                    return inflight
                    """, Long.class);
    private static final DefaultRedisScript<Long> COMMIT_PREMIUM_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('ZREM', KEYS[2], ARGV[2])
                    if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then
                        return tonumber(redis.call('GET', KEYS[1]) or '0')
                    end
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if used >= tonumber(ARGV[1]) then return -1 end
                    used = redis.call('INCR', KEYS[1])
                    redis.call('EXPIRE', KEYS[1], ARGV[3])
                    redis.call('SADD', KEYS[3], ARGV[2])
                    redis.call('EXPIRE', KEYS[3], ARGV[3])
                    return used
                    """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("return redis.call('ZREM', KEYS[1], ARGV[1])",
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final EntitlementClient entitlementClient;
    private final GeminiProperties properties;

    public Reservation reserve(Long userId, boolean premium, String requestToken) {
        String token = requestToken == null || requestToken.isBlank()
                ? UUID.randomUUID().toString() : requestToken;
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + reservationTtlSeconds();
        if (!premium) {
            return reserveFree(userId, token, now, expiresAt);
        }
        Long used = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(premiumKey(userId), premiumInflightKey(userId)),
                String.valueOf(premiumLimit()),
                String.valueOf(now),
                String.valueOf(expiresAt),
                token,
                String.valueOf(reservationTtlSeconds())
        );
        if (used == null) {
            throw new IllegalStateException("Could not reserve premium food photo scan quota");
        }
        if (used < 0) {
            throw dailyLimitReached();
        }
        return new Reservation(userId, token, true, premiumLimit() - used.intValue());
    }

    private Reservation reserveFree(Long userId, String token, long now, long expiresAt) {
        FoodPhotoScanCreditDto credits = entitlementClient.getFoodPhotoScanCredits(userId);
        if (credits == null || credits.getRemainingScans() <= 0) {
            throw freeLimitReached();
        }
        Long result = redisTemplate.execute(
                RESERVE_FREE_SCRIPT,
                List.of(freeInflightKey(userId)),
                String.valueOf(credits.getRemainingScans()),
                String.valueOf(now),
                String.valueOf(expiresAt),
                token,
                String.valueOf(reservationTtlSeconds())
        );
        if (result == null) {
            throw new IllegalStateException("Could not reserve free food photo scan quota");
        }
        if (result < 0) {
            throw freeLimitReached();
        }
        return new Reservation(userId, token, false, credits.getRemainingScans());
    }

    public QuotaSnapshot commitSuccessfulFoodScan(Reservation reservation) {
        if (!reservation.premium()) {
            return commitFree(reservation);
        }
        Long used = redisTemplate.execute(
                COMMIT_PREMIUM_SCRIPT,
                List.of(premiumKey(reservation.userId()),
                        premiumInflightKey(reservation.userId()),
                        premiumCompletedKey(reservation.userId())),
                String.valueOf(premiumLimit()),
                reservation.token(),
                String.valueOf(PREMIUM_TTL_SECONDS)
        );
        if (used == null) {
            throw new IllegalStateException("Could not commit premium food photo scan quota");
        }
        if (used < 0) {
            throw dailyLimitReached();
        }
        return new QuotaSnapshot(true, Math.max(0, premiumLimit() - used.intValue()));
    }

    private QuotaSnapshot commitFree(Reservation reservation) {
        try {
            FoodPhotoScanCreditDto credits = entitlementClient.consumeFoodPhotoScanCredit(
                    reservation.userId(), reservation.token());
            if (credits == null) {
                throw new IllegalStateException("Could not consume food photo scan credit");
            }
            if (!credits.isConsumed()) {
                throw freeLimitReached();
            }
            return new QuotaSnapshot(false, credits.getRemainingScans());
        } finally {
            release(reservation);
        }
    }

    public void release(Reservation reservation) {
        String key = reservation.premium()
                ? premiumInflightKey(reservation.userId())
                : freeInflightKey(reservation.userId());
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), reservation.token());
    }

    private long reservationTtlSeconds() {
        return properties.getFoodPhotoScan().getReservationTtlSeconds();
    }

    private int premiumLimit() {
        return properties.getFoodPhotoScan().getPremiumDailyLimit();
    }

    private String premiumKey(Long userId) {
        return PREMIUM_PREFIX + userId + ":" + quotaDate();
    }

    private String premiumInflightKey(Long userId) {
        return PREMIUM_INFLIGHT_PREFIX + userId + ":" + quotaDate();
    }

    private String premiumCompletedKey(Long userId) {
        return PREMIUM_COMPLETED_PREFIX + userId + ":" + quotaDate();
    }

    private String freeInflightKey(Long userId) {
        return FREE_INFLIGHT_PREFIX + userId;
    }

    private LocalDate quotaDate() {
        return LocalDate.now(properties.getFoodPhotoScan().getQuotaZone());
    }

    private FoodPhotoScanLimitException freeLimitReached() {
        return new FoodPhotoScanLimitException(HttpStatus.FORBIDDEN, "FREE_LIMIT_REACHED");
    }

    private FoodPhotoScanLimitException dailyLimitReached() {
        return new FoodPhotoScanLimitException(HttpStatus.TOO_MANY_REQUESTS,
                "DAILY_LIMIT_REACHED");
    }

    public record Reservation(Long userId, String token, boolean premium, int remaining) {
        public QuotaSnapshot snapshot() {
            return new QuotaSnapshot(premium, remaining);
        }
    }

    public record QuotaSnapshot(boolean premium, int remaining) {
    }
}
