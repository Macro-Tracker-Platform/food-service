package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BarcodeScanService {
    private final FoodService foodService;
    private final EntitlementClient entitlementClient;

    public ScanResult scan(Long userId, String barcode) {
        BarcodeScanQuotaDto quota = entitlementClient.reserveBarcodeScan(userId, barcode);
        if (quota == null) {
            throw new IllegalStateException("Barcode scan quota service returned no decision");
        }
        if (!quota.isAllowed()) {
            throw new LimitExceededException(quota);
        }
        FoodResponseDto food = foodService.findPersonalizedById(barcode, userId);
        return new ScanResult(food, quota);
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
