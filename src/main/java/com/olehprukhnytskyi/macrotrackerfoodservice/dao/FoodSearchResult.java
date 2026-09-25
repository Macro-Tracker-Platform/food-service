package com.olehprukhnytskyi.macrotrackerfoodservice.dao;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import java.util.List;

public record FoodSearchResult(List<Food> items, int total) {
}
