package com.olehprukhnytskyi.macrotrackerfoodservice.client;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.util.CustomHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${feign.user-service:http://localhost:8080}")
public interface EntitlementClient {
    String APP_VERSION_CODE_HEADER = "X-App-Version-Code";

    @GetMapping("/api/users/me/entitlements")
    EntitlementDto getEntitlement(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode);

    @PostMapping("/api/users/me/barcode-scans/{barcode}/reserve")
    BarcodeScanQuotaDto reserveBarcodeScan(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable String barcode);

    @GetMapping("/api/users/me/food-photo-scans/credits")
    FoodPhotoScanCreditDto getFoodPhotoScanCredits(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId);

    @PostMapping("/api/users/me/food-photo-scans/consume")
    FoodPhotoScanCreditDto consumeFoodPhotoScanCredit(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey);

    default FoodPhotoScanCreditDto consumeFoodPhotoScanCredit(Long userId) {
        return consumeFoodPhotoScanCredit(userId, null);
    }
}
