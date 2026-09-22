package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.BarcodeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BarcodeScanService {
    private final FoodService foodService;

    public FoodResponseDto scan(Long userId, String barcode) {
        return findFoodByBarcodeCandidates(userId, barcode);
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

}
