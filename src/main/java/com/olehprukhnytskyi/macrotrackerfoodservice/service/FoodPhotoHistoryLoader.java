package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.IntakeHistoryClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPhotoHistoryLoader {
    private final IntakeHistoryClient intakeHistoryClient;
    private final FoodRepository foodRepository;
    private final GeminiProperties properties;

    @Cacheable(value = CacheConstants.FOOD_PHOTO_HISTORY, key = "#userId",
            unless = "#result == null || #result.isEmpty()")
    public List<Food> load(Long userId) {
        try {
            List<String> ids = intakeHistoryClient.getFrequentRecentFoodIds(
                    userId, properties.getFoodPhotoScan().getHistoryLimit());
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            Map<String, Food> byId = new LinkedHashMap<>();
            foodRepository.findAllById(ids).forEach(food -> byId.put(food.getId(), food));
            List<Food> ordered = new ArrayList<>();
            ids.forEach(id -> {
                Food food = byId.get(id);
                if (food != null) {
                    ordered.add(food);
                }
            });
            return ordered;
        } catch (RuntimeException exception) {
            log.warn("Could not load food history for userId={}; using global search",
                    userId, exception);
            return Collections.emptyList();
        }
    }
}
