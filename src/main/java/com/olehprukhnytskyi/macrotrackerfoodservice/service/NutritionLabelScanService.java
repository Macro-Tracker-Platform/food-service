package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class NutritionLabelScanService {
    private final ImageService imageService;
    private final GeminiService geminiService;
    private final NutritionLabelRateLimitService rateLimitService;

    public NutritionLabelScanResponseDto scan(Long userId, MultipartFile image) {
        imageService.validateImage(image);
        NutritionLabelRateLimitService.Reservation reservation =
                rateLimitService.reserve(userId);
        try {
            return geminiService.scanNutritionLabel(image);
        } catch (GeminiTemporaryUnavailableException e) {
            rateLimitService.release(reservation);
            throw e;
        }
    }
}
