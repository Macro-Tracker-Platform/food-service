package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
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
    private final GeminiProperties geminiProperties;

    public NutritionLabelScanResponseDto scan(Long userId, String appVersionCode,
                                              MultipartFile image) {
        imageService.validateImage(image);
        EntitlementDto entitlement = entitlementClient.getEntitlement(userId, appVersionCode);
        int monthlyLimit = resolveMonthlyLimit(entitlement);
        NutritionLabelRateLimitService.Reservation reservation =
                rateLimitService.reserve(userId, monthlyLimit);
        try {
            NutritionLabelScanResponseDto response = geminiService.scanNutritionLabel(image);
            response.setQuota(new NutritionLabelScanResponseDto.ScanQuota(
                    reservation.monthlyLimit(),
                    reservation.remaining(),
                    reservation.monthlyResetAt()));
            return response;
        } catch (RuntimeException exception) {
            rateLimitService.release(reservation);
            throw exception;
        }
    }

    private int resolveMonthlyLimit(EntitlementDto entitlement) {
        int proLimit = geminiProperties.getNutritionLabelScan().getProMonthlyLimit();
        if (entitlement.getFeatures() != null
                && entitlement.getFeatures().getNutritionLabelScans() != null
                && entitlement.getFeatures().getNutritionLabelScans().getLimit() >= proLimit) {
            return proLimit;
        }
        return "PRO".equals(entitlement.getPlan()) || entitlement.isLegacyAccess()
                ? proLimit
                : geminiProperties.getNutritionLabelScan().getFreeMonthlyLimit();
    }
}
