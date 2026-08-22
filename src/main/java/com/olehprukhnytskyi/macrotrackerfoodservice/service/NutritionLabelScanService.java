package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsLabelResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class NutritionLabelScanService {
    private final ImageService imageService;
    private final GeminiService geminiService;
    private final NutritionLabelRateLimitService rateLimitService;
    private final EntitlementClient entitlementClient;

    public NutritionLabelScanResponseDto scan(Long userId, String appVersionCode,
                                              MultipartFile image) {
        imageService.validateImage(image);
        EntitlementDto entitlement = entitlementClient.getEntitlement(userId, appVersionCode);
        boolean premium = hasPremiumScanQuota(entitlement);
        rateLimitService.reserveRequest(userId);
        NutritionLabelRateLimitService.SuccessfulScanQuota quota =
                rateLimitService.ensureSuccessfulScanQuotaAvailable(userId, premium);

        NutritionLabelScanResponseDto response = geminiService.scanNutritionLabel(image);
        if (hasParsedNutritionLabel(response)) {
            quota = rateLimitService.recordSuccessfulScan(userId, premium);
        }
        if (response != null) {
            response.setQuota(new NutritionLabelScanResponseDto.ScanQuota(
                    quota.limit(),
                    quota.remaining(),
                    quota.resetAt()));
        }
        return response;
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
