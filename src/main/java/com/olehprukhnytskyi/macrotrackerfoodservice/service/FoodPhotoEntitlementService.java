package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodPhotoEntitlementService {
    private final EntitlementClient entitlementClient;

    @Cacheable(value = CacheConstants.FOOD_PHOTO_ENTITLEMENT,
            key = "#userId + ':' + (#appVersionCode == null ? '' : #appVersionCode)",
            unless = "#result == null")
    public EntitlementDto get(Long userId, String appVersionCode) {
        return entitlementClient.getEntitlement(userId, appVersionCode);
    }
}
