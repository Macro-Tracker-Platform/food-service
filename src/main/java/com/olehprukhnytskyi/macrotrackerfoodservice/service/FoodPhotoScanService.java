package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoBase64RequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPhotoScanService {
    private final ImageService imageService;
    private final Base64ImageDecoder base64ImageDecoder;
    private final FoodPhotoEntitlementService entitlementService;
    private final FoodPhotoQuotaReservationService quotaService;
    private final GeminiService geminiService;
    private final FoodPhotoMatchingService matchingService;
    private final FoodPhotoCapacityGuard capacityGuard;
    private final FoodPhotoIdempotencyService idempotencyService;

    public FoodPhotoScanResponseDto scan(Long userId, String appVersionCode,
                                         FoodPhotoBase64RequestDto request) {
        return scan(userId, appVersionCode, null, request);
    }

    public FoodPhotoScanResponseDto scan(Long userId, String appVersionCode,
                                         String idempotencyKey,
                                         FoodPhotoBase64RequestDto request) {
        return idempotencyService.execute(userId, idempotencyKey, operationToken -> {
            try (FoodPhotoCapacityGuard.Permit ignored = capacityGuard.acquire()) {
                return scanInternal(userId, appVersionCode,
                        base64ImageDecoder.decode(request), operationToken);
            }
        });
    }

    public FoodPhotoScanResponseDto scan(Long userId, String appVersionCode,
                                         MultipartFile image) {
        return scan(userId, appVersionCode, null, image);
    }

    public FoodPhotoScanResponseDto scan(Long userId, String appVersionCode,
                                         String idempotencyKey, MultipartFile image) {
        return idempotencyService.execute(userId, idempotencyKey, operationToken -> {
            try (FoodPhotoCapacityGuard.Permit ignored = capacityGuard.acquire()) {
                return scanInternal(userId, appVersionCode, image, operationToken);
            }
        });
    }

    private FoodPhotoScanResponseDto scanInternal(Long userId, String appVersionCode,
                                                   MultipartFile image,
                                                   String operationToken) {
        Instant startedAt = Instant.now();
        imageService.validateImage(image);
        EntitlementDto entitlement = entitlementService.get(userId, appVersionCode);
        boolean premium = isPremium(entitlement);

        FoodPhotoQuotaReservationService.Reservation reservation =
                quotaService.reserve(userId, premium, operationToken);
        boolean committed = false;
        try {
            GeminiFoodPhotoScanDto visionResult = geminiService.scanFoodPhoto(image);
            if ("blurred".equals(visionResult.getImageQuality())) {
                logCompleted(userId, "blurred", startedAt, 0);
                return response("not_food", reservation.snapshot(), Collections.emptyList());
            }
            if (!"food".equals(visionResult.getScanType())) {
                logCompleted(userId, visionResult.getScanType(), startedAt, 0);
                return response(visionResult.getScanType(), reservation.snapshot(),
                        Collections.emptyList());
            }

            List<FoodPhotoScanResponseDto.Item> items = matchingService.process(
                    userId, visionResult.getItems());
            FoodPhotoQuotaReservationService.QuotaSnapshot updatedQuota =
                    quotaService.commitSuccessfulFoodScan(reservation);
            committed = true;
            logCompleted(userId, visionResult.getScanType(), startedAt, items.size());
            return response(visionResult.getScanType(), updatedQuota, items);
        } finally {
            if (!committed) {
                safeRelease(reservation);
            }
        }
    }

    private void safeRelease(FoodPhotoQuotaReservationService.Reservation reservation) {
        try {
            quotaService.release(reservation);
        } catch (RuntimeException exception) {
            log.warn("Could not release food photo quota reservation userId={}",
                    reservation.userId(), exception);
        }
    }

    private FoodPhotoScanResponseDto response(
            String scanType, FoodPhotoQuotaReservationService.QuotaSnapshot quota,
            List<FoodPhotoScanResponseDto.Item> items) {
        return FoodPhotoScanResponseDto.builder()
                .scanType(scanType)
                .remainingScans(quota.remaining())
                .premium(quota.premium())
                .items(items)
                .build();
    }

    private boolean isPremium(EntitlementDto entitlement) {
        return entitlement != null && ("PRO".equalsIgnoreCase(entitlement.getPlan())
                || entitlement.isLegacyAccess());
    }

    private void logCompleted(Long userId, String scanType, Instant startedAt, int itemCount) {
        log.info("Food photo scan completed userId={} scan_type={} items={} latency_ms={}",
                userId, scanType, itemCount,
                Duration.between(startedAt, Instant.now()).toMillis());
    }
}
