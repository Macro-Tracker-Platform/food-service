package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsLabelResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionLabelScanService {
    private final ImageService imageService;
    private final GeminiService geminiService;
    private final NutritionLabelRateLimitService rateLimitService;
    private final AiCreditReservationService aiCreditReservationService;
    private final EntitlementClient entitlementClient;

    public NutritionLabelScanResponseDto scan(Long userId, String appVersionCode,
                                              MultipartFile image) {
        imageService.validateImage(image);
        EntitlementDto entitlement = entitlementClient.getEntitlement(userId, appVersionCode);
        boolean premium = hasPremiumScanQuota(entitlement);
        rateLimitService.reserveRequest(userId);

        if (!premium) {
            return scanWithFreeCredit(userId, image);
        }
        NutritionLabelRateLimitService.SuccessfulScanQuota quota =
                rateLimitService.ensurePremiumSuccessfulScanQuotaAvailable(userId);

        NutritionLabelScanResponseDto response = geminiService.scanNutritionLabel(image);
        if (hasParsedNutritionLabel(response)) {
            quota = rateLimitService.recordPremiumSuccessfulScan(userId);
        }
        if (response != null) {
            response.setQuota(new NutritionLabelScanResponseDto.ScanQuota(
                    quota.limit(),
                    quota.remaining(),
                    quota.resetAt()));
        }
        return response;
    }

    private NutritionLabelScanResponseDto scanWithFreeCredit(Long userId,
                                                              MultipartFile image) {
        AiCreditReservationService.Reservation reservation =
                aiCreditReservationService.reserve(
                        userId, "nutrition-label:" + UUID.randomUUID());
        boolean committed = false;
        try {
            NutritionLabelScanResponseDto response = geminiService.scanNutritionLabel(image);
            AiCreditReservationService.QuotaSnapshot quota =
                    aiCreditReservationService.commit(reservation);
            committed = true;
            if (response != null) {
                response.setQuota(new NutritionLabelScanResponseDto.ScanQuota(
                        quota.limit(), quota.remaining(), quota.resetAt()));
            }
            return response;
        } finally {
            if (!committed) {
                safeRelease(reservation);
            }
        }
    }

    private void safeRelease(AiCreditReservationService.Reservation reservation) {
        try {
            aiCreditReservationService.release(reservation);
        } catch (RuntimeException exception) {
            log.warn("Could not release nutrition-label AI credit reservation userId={}",
                    reservation.userId(), exception);
        }
    }

    private boolean hasPremiumScanQuota(EntitlementDto entitlement) {
        return entitlement != null
                && ("PRO".equals(entitlement.getPlan()) || entitlement.isLegacyAccess());
    }

    private boolean hasParsedNutritionLabel(NutritionLabelScanResponseDto response) {
        if (response == null || response.getNutriments() == null) {
            return false;
        }
        NutrimentsLabelResponseDto nutriments = response.getNutriments();
        return nutriments.getCaloriesPer100() != null
                || nutriments.getCarbohydratesPer100() != null
                || nutriments.getFatPer100() != null
                || nutriments.getProteinPer100() != null;
    }
}
