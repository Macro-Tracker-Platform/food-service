package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchDao;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPhotoMatchingService {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final FoodPhotoHistoryLoader historyLoader;
    private final FoodSearchDao foodSearchDao;
    private final GeminiProperties properties;

    public List<FoodPhotoScanResponseDto.Item> process(
            Long userId, List<GeminiFoodPhotoScanDto.Item> items) {
        List<Food> history = historyLoader.load(userId);
        List<Match> historyMatches = items.stream()
                .map(item -> bestMatch(item.getName(), history))
                .toList();
        List<String> unresolved = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (historyMatches.get(index).score() < threshold()) {
                unresolved.add(items.get(index).getName());
            }
        }
        List<Food> globalCandidates = loadGlobalCandidates(userId, unresolved);
        List<FoodPhotoScanResponseDto.Item> results = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            GeminiFoodPhotoScanDto.Item item = items.get(index);
            Match historyMatch = historyMatches.get(index);
            Match globalMatch = bestMatch(item.getName(), globalCandidates);
            Match best = globalMatch.score() >= historyMatch.score()
                    ? globalMatch : historyMatch;
            if (best.food() != null && best.score() >= threshold()
                    && best.food().getNutriments() != null) {
                results.add(fromDatabase(item, best));
            } else {
                results.add(fromFallback(item, best.score()));
            }
        }
        return results;
    }

    private List<Food> loadGlobalCandidates(Long userId, List<String> names) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return foodSearchDao.searchPhotoCandidates(
                    names,
                    userId,
                    properties.getFoodPhotoScan().getGlobalCandidateLimit()
            );
        } catch (RuntimeException exception) {
            log.warn("Global food photo matching failed for userId={}; using fallback",
                    userId, exception);
            return Collections.emptyList();
        }
    }

    private Match bestMatch(String query, List<Food> foods) {
        Match best = new Match(null, 0.0);
        for (Food food : foods) {
            double score = similarity(query, food.getProductName());
            score = Math.max(score, similarity(query, food.getGenericName()));
            if (score > best.score()) {
                best = new Match(food, score);
            }
        }
        return best;
    }

    private FoodPhotoScanResponseDto.Item fromDatabase(
            GeminiFoodPhotoScanDto.Item item, Match match) {
        Food food = match.food();
        Nutriments nutrients = food.getNutriments();
        BigDecimal factor = item.getEstimatedWeightGrams()
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        return baseItem(item, match.score())
                .id(food.getId())
                .name(food.getProductName())
                .source("db_matched")
                .calories(scale(nutrients.getCaloriesPer100(), factor))
                .proteinG(scale(nutrients.getProteinPer100(), factor))
                .fatG(scale(nutrients.getFatPer100(), factor))
                .carbsG(scale(nutrients.getCarbohydratesPer100(), factor))
                .build();
    }

    private FoodPhotoScanResponseDto.Item fromFallback(
            GeminiFoodPhotoScanDto.Item item, double score) {
        GeminiFoodPhotoScanDto.FallbackNutrition nutrients = item.getFallbackNutrition();
        return baseItem(item, score)
                .id(null)
                .name(item.getName())
                .source("ai_fallback")
                .calories(nutritionValue(nutrients.getCalories()))
                .proteinG(nutritionValue(nutrients.getProteinG()))
                .fatG(nutritionValue(nutrients.getFatG()))
                .carbsG(nutritionValue(nutrients.getCarbsG()))
                .build();
    }

    private FoodPhotoScanResponseDto.Item.ItemBuilder baseItem(
            GeminiFoodPhotoScanDto.Item item, double score) {
        return FoodPhotoScanResponseDto.Item.builder()
                .tempId(UUID.randomUUID())
                .searchQuery(item.getName())
                .weightG(item.getEstimatedWeightGrams().setScale(1, RoundingMode.HALF_UP))
                .matchScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
    }

    private BigDecimal scale(BigDecimal per100, BigDecimal factor) {
        return nutritionValue(per100 == null ? BigDecimal.ZERO : per100.multiply(factor));
    }

    private BigDecimal nutritionValue(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    double similarity(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return 0.0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1.0;
        }
        int maxLength = Math.max(normalizedLeft.length(), normalizedRight.length());
        double editSimilarity = 1.0
                - ((double) levenshtein(normalizedLeft, normalizedRight) / maxLength);
        double tokenSimilarity = tokenJaccard(normalizedLeft, normalizedRight);
        double containmentBoost = normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft) ? 0.05 : 0.0;
        return Math.min(1.0, editSimilarity * 0.65 + tokenSimilarity * 0.35
                + containmentBoost);
    }

    private double tokenJaccard(String left, String right) {
        Set<String> leftTokens = new HashSet<>(List.of(left.split(" ")));
        Set<String> rightTokens = new HashSet<>(List.of(right.split(" ")));
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            int[] current = new int[right.length() + 1];
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)
                        ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1,
                                previous[rightIndex] + 1),
                        previous[rightIndex - 1] + substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = NON_WORD.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return SPACES.matcher(normalized).replaceAll(" ").trim();
    }

    private double threshold() {
        return properties.getFoodPhotoScan().getMatchThreshold();
    }

    private record Match(Food food, double score) {
    }
}
