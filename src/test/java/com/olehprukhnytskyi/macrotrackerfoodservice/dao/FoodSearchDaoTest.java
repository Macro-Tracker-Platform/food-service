package com.olehprukhnytskyi.macrotrackerfoodservice.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FoodSearchDaoTest {
    private final FoodSearchDao foodSearchDao = new FoodSearchDao(null);

    @Test
    @DisplayName("Should avoid returning near-identical product names back-to-back")
    void diversifySimilarProducts_whenNamesShareBase_shouldInterleaveGroups() {
        List<Food> foods = List.of(
                food("1", "Banana raw"),
                food("2", "Banana raw, mashed"),
                food("3", "Banana raw, mashed, packed"),
                food("4", "Banana ripe")
        );

        List<Food> result = foodSearchDao.diversifySimilarProducts(foods, 0, 4);

        assertEquals(List.of("1", "4", "2", "3"), result.stream()
                .map(Food::getId)
                .toList());
    }

    @Test
    @DisplayName("Should apply pagination after diversifying candidates")
    void diversifySimilarProducts_whenOffsetIsProvided_shouldPageDiversifiedResults() {
        List<Food> foods = List.of(
                food("1", "Banana raw"),
                food("2", "Banana raw, mashed"),
                food("3", "Apple raw"),
                food("4", "Banana raw, mashed, packed")
        );

        List<Food> result = foodSearchDao.diversifySimilarProducts(foods, 1, 2);

        assertEquals(List.of("3", "2"), result.stream()
                .map(Food::getId)
                .toList());
    }

    private Food food(String id, String productName) {
        return Food.builder()
                .id(id)
                .productName(productName)
                .build();
    }
}
