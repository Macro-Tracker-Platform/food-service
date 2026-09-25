package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReport;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReportReason;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.jpa.FoodReportRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FoodReportService {
    private final FoodRepository foodRepository;
    private final FoodReportRepository foodReportRepository;

    @Transactional
    @CacheEvict(value = CacheConstants.SEARCH_RESULTS, allEntries = true)
    public void report(String foodId, Long userId, FoodReportReason reason) {
        Food food = foodRepository.findById(foodId)
                .filter(Food::isVisible)
                .orElseThrow(() -> new NotFoundException(
                        FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id: " + foodId));
        if (foodReportRepository.existsByUserIdAndFoodId(userId, foodId)) {
            return;
        }
        foodReportRepository.save(FoodReport.builder()
                .foodId(food.getId())
                .userId(userId)
                .reason(reason)
                .build());
    }
}
