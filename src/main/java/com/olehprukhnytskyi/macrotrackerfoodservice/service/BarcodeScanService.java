package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.BarcodeUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BarcodeScanService {
    private final FoodService foodService;
    private final EntitlementClient entitlementClient;

    public ScanResult scan(Long userId, String barcode) {
        BarcodeScanQuotaDto quota = entitlementClient.reserveBarcodeScan(
                userId,
                BarcodeUtils.normalizeForQuota(barcode));
        if (quota == null) {
            throw new IllegalStateException("Barcode scan quota service returned no decision");
        }
        if (!quota.isAllowed()) {
            throw new LimitExceededException(quota);
        }
        FoodResponseDto food = findFoodByBarcodeCandidates(userId, barcode);
        return new ScanResult(food, quota);
    }

    private FoodResponseDto findFoodByBarcodeCandidates(Long userId, String barcode) {
        NotFoundException lastNotFound = null;
        for (String candidate : BarcodeUtils.lookupCandidates(barcode)) {
            try {
                return foodService.findPersonalizedById(candidate, userId);
            } catch (NotFoundException e) {
                lastNotFound = e;
            }
        }
        if (lastNotFound != null) {
            throw lastNotFound;
        }
        throw new IllegalArgumentException("Barcode must not be blank");
    }

    public record ScanResult(FoodResponseDto food, BarcodeScanQuotaDto quota) {
    }

    @Getter
    public static class LimitExceededException extends RuntimeException {
        private final BarcodeScanQuotaDto quota;

        public LimitExceededException(BarcodeScanQuotaDto quota) {
            super("Daily unique barcode scan limit exceeded");
            this.quota = quota;
        }
    }
}
