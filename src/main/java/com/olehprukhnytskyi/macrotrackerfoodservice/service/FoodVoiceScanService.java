package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodVoiceBase64RequestDto;
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
public class FoodVoiceScanService {
    private final Base64AudioDecoder base64AudioDecoder;
    private final FoodPhotoEntitlementService entitlementService;
    private final FoodVoiceQuotaReservationService quotaService;
    private final GeminiService geminiService;
    private final FoodPhotoMatchingService matchingService;
    private final FoodPhotoCapacityGuard capacityGuard;
    private final FoodVoiceIdempotencyService idempotencyService;

    public FoodPhotoScanResponseDto scan(Long userId, String appVersionCode,
                                         String idempotencyKey, String acceptLanguage,
                                         FoodVoiceBase64RequestDto request) {
        MultipartFile audio = base64AudioDecoder.decode(request);
        return idempotencyService.execute(userId, idempotencyKey, operationToken -> {
            try (FoodPhotoCapacityGuard.Permit ignored = capacityGuard.acquire()) {
                return scanInternal(userId, appVersionCode, audio, acceptLanguage,
                        operationToken);
            }
        });
    }

    private FoodPhotoScanResponseDto scanInternal(Long userId, String appVersionCode,
                                                  MultipartFile audio,
                                                  String acceptLanguage,
                                                  String operationToken) {
        Instant startedAt = Instant.now();
        EntitlementDto entitlement = entitlementService.get(userId, appVersionCode);
        boolean premium = isPremium(entitlement);

        FoodVoiceQuotaReservationService.Reservation reservation =
                quotaService.reserve(userId, premium, operationToken);
        boolean committed = false;
        try {
            GeminiFoodPhotoScanDto voiceResult = geminiService.scanFoodVoice(
                    audio, acceptLanguage);
            if (!"food".equals(voiceResult.getScanType())) {
                logCompleted(userId, voiceResult.getScanType(), startedAt, 0);
                return response(voiceResult.getScanType(), reservation.snapshot(),
                        Collections.emptyList());
            }

            List<FoodPhotoScanResponseDto.Item> items = matchingService.process(
                    userId, voiceResult.getItems());
            FoodVoiceQuotaReservationService.QuotaSnapshot updatedQuota =
                    quotaService.commitSuccessfulFoodScan(reservation);
            committed = true;
            logCompleted(userId, voiceResult.getScanType(), startedAt, items.size());
            return response(voiceResult.getScanType(), updatedQuota, items);
        } finally {
            if (!committed) {
                safeRelease(reservation);
            }
        }
    }

    private void safeRelease(FoodVoiceQuotaReservationService.Reservation reservation) {
        try {
            quotaService.release(reservation);
        } catch (RuntimeException exception) {
            log.warn("Could not release voice food quota reservation userId={}",
                    reservation.userId(), exception);
        }
    }

    private FoodPhotoScanResponseDto response(
            String scanType, FoodVoiceQuotaReservationService.QuotaSnapshot quota,
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
        log.info("Voice food scan completed userId={} scan_type={} items={} latency_ms={}",
                userId, scanType, itemCount,
                Duration.between(startedAt, Instant.now()).toMillis());
    }
}
