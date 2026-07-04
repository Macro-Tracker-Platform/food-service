package com.olehprukhnytskyi.macrotrackerfoodservice.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FoodSearchDao {
    private static final int MAX_DIVERSITY_CANDIDATES = 200;
    private static final float APPROVED_BOOST = 100.0f;
    private static final float VERIFIED_BY_ADMIN_BOOST = 450.0f;
    private static final double VERIFIED_BY_ADMIN_RANK_BOOST = 5_000.0;
    private static final double VERIFIED_RAW_FOOD_RANK_BOOST = 12_000.0;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ElasticsearchClient elasticsearchClient;

    public List<Food> search(String query, Long userId, List<String> excludedIds,
                             int offset, int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Query must not be null or empty");
        }
        try {
            String cleanQuery = cleanQuery(query);
            if (cleanQuery.isBlank()) {
                throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                        "Query must contain searchable text");
            }
            Query searchQuery = buildSearchQuery(cleanQuery, userId, excludedIds);
            boolean diversifyResults = shouldDiversify(offset, limit);
            SearchResponse<Food> response = elasticsearchClient.search(
                    s -> s.index("macro_tracker.foods")
                            .query(searchQuery)
                            .from(diversifyResults ? 0 : offset)
                            .size(diversifyResults ? candidateLimit(offset, limit) : limit),
                    Food.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return Collections.emptyList();
            }
            List<Food> foods = response.hits().hits().stream()
                    .map(hit -> {
                        if (hit.source() == null) {
                            return null;
                        }
                        hit.source().setId(hit.id());
                        return hit.source();
                    })
                    .filter(Objects::nonNull)
                    .toList();
            List<Food> rankedFoods = rankCandidates(foods, cleanQuery);
            return diversifyResults
                    ? diversifySimilarProducts(rankedFoods, offset, limit)
                    : rankedFoods;
        } catch (IOException e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to execute search request", e);
        } catch (Exception e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected error during search", e);
        }
    }

    public List<String> getSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.trim().toLowerCase();
        try {
            SearchResponse<Food> response = elasticsearchClient.search(
                    s -> s.index("macro_tracker.foods")
                            .query(buildSuggestionQuery(normalized)),
                    Food.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return Collections.emptyList();
            }
            return response.hits().hits().stream()
                    .map(hit -> hit.source() != null ? hit.source().getProductName() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to fetch search suggestions from Elasticsearch", e);
        }
    }

    private Query buildSearchQuery(String cleanQuery, Long userId, List<String> excludedIds) {
        return Query.of(q -> q.bool(mainBool -> {
            mainBool.must(m -> m.bool(searchBool -> {
                searchBool.should(s -> s.multiMatch(mm -> mm
                        .fields("product_name^10", "generic_name^6", "brands^6")
                        .query(cleanQuery)
                        .type(TextQueryType.Phrase)
                ));
                searchBool.should(s -> s.multiMatch(mm -> mm
                        .fields("product_name^6", "_keywords^5", "generic_name^4", "brands^4")
                        .query(cleanQuery)
                        .operator(Operator.And)
                ));
                searchBool.should(s -> s.multiMatch(mm -> mm
                        .fields("product_name^3", "_keywords^2", "generic_name^1")
                        .query(cleanQuery)
                        .operator(Operator.And)
                        .fuzziness("AUTO")
                ));
                if (cleanQuery.matches("^\\d{6,24}$")) {
                    String tokenNoZeros = cleanQuery.replaceFirst("^0+(?!$)", "");
                    processBarcode(searchBool, tokenNoZeros);
                }
                searchBool.minimumShouldMatch("1");
                return searchBool;
            }));
            mainBool.filter(f -> f.bool(filterBool -> {
                filterBool.should(s -> s.match(m -> m.field("moderation_status")
                        .query("APPROVED")));
                filterBool.should(s -> s.bool(b -> b
                        .mustNot(mn -> mn.exists(e -> e.field("user_id")))
                ));
                if (userId != null) {
                    filterBool.should(s -> s.term(t -> t.field("user_id").value(userId)));
                }
                filterBool.minimumShouldMatch("1");
                return filterBool;
            }));
            mainBool.should(s -> s.match(m -> m
                    .field("moderation_status")
                    .query("APPROVED")
                    .boost(APPROVED_BOOST)
            ));
            mainBool.should(s -> s.term(t -> t
                    .field("verified_by_admin")
                    .value(true)
                    .boost(VERIFIED_BY_ADMIN_BOOST)
            ));
            if (userId != null) {
                mainBool.should(s -> s.term(t -> t
                        .field("user_id")
                        .value(userId)
                        .boost(1000.0f)
                ));
            }
            if (excludedIds != null && !excludedIds.isEmpty()) {
                mainBool.mustNot(mn -> mn.ids(i -> i.values(excludedIds)));
            }
            return mainBool;
        }));
    }

    private boolean shouldDiversify(int offset, int limit) {
        return offset >= 0 && limit > 0 && offset + limit <= MAX_DIVERSITY_CANDIDATES;
    }

    private int candidateLimit(int offset, int limit) {
        if (limit <= 0) {
            return 0;
        }
        return MAX_DIVERSITY_CANDIDATES;
    }

    List<Food> rankCandidates(List<Food> foods, String cleanQuery) {
        List<String> queryTokens = queryTokens(cleanQuery);
        return foods.stream()
                .filter(food -> matchesRequiredTokens(food, queryTokens))
                .sorted((left, right) -> Double.compare(
                        relevanceScore(right, cleanQuery, queryTokens),
                        relevanceScore(left, cleanQuery, queryTokens)))
                .toList();
    }

    private boolean matchesRequiredTokens(Food food, List<String> queryTokens) {
        if (queryTokens.size() <= 1) {
            return true;
        }
        String searchableText = normalizedSearchableText(food);
        return queryTokens.stream().allMatch(searchableText::contains);
    }

    private double relevanceScore(Food food, String cleanQuery, List<String> queryTokens) {
        String productName = normalizeText(food.getProductName());
        double score = 0.0;
        if (productName.equals(cleanQuery)) {
            score += 10_000.0;
        }
        if (productName.startsWith(cleanQuery + " ") || productName.startsWith(cleanQuery)) {
            score += 4_000.0;
        }
        if (productName.contains(cleanQuery)) {
            score += 1_500.0;
        }
        if (containsAllTokens(productName, queryTokens)) {
            score += 700.0;
        }
        if (containsTokensInOrder(productName, queryTokens)) {
            score += 350.0;
        }
        int firstTokenIndex = queryTokens.isEmpty()
                ? -1
                : productName.indexOf(queryTokens.getFirst());
        if (firstTokenIndex > 0) {
            score -= Math.min(firstTokenIndex * 12.0, 500.0);
        }
        if (food.isVerifiedByAdmin()) {
            score += VERIFIED_BY_ADMIN_RANK_BOOST;
        }
        if (food.isVerifiedByAdmin() && isVerifiedRawFood(productName, cleanQuery)) {
            score += VERIFIED_RAW_FOOD_RANK_BOOST;
        }
        return score;
    }

    private boolean isVerifiedRawFood(String productName, String cleanQuery) {
        return productName.equals(cleanQuery + " raw")
                || productName.startsWith(cleanQuery + " raw ");
    }

    private boolean containsAllTokens(String productName, List<String> queryTokens) {
        return queryTokens.stream().allMatch(productName::contains);
    }

    private boolean containsTokensInOrder(String productName, List<String> queryTokens) {
        int currentIndex = 0;
        for (String token : queryTokens) {
            int tokenIndex = productName.indexOf(token, currentIndex);
            if (tokenIndex < 0) {
                return false;
            }
            currentIndex = tokenIndex + token.length();
        }
        return true;
    }

    List<Food> diversifySimilarProducts(List<Food> foods, int offset, int limit) {
        if (foods.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        Map<String, List<Food>> groupedFoods = new LinkedHashMap<>();
        foods.forEach(food -> groupedFoods
                .computeIfAbsent(diversityKey(food), ignored -> new ArrayList<>())
                .add(food));

        List<List<Food>> groups = new ArrayList<>(groupedFoods.values());
        List<Food> diversified = new ArrayList<>(foods.size());
        boolean added;
        int groupRank = 0;
        do {
            added = false;
            for (List<Food> group : groups) {
                if (groupRank < group.size()) {
                    diversified.add(group.get(groupRank));
                    added = true;
                }
            }
            groupRank++;
        } while (added);

        return diversified.stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private String diversityKey(Food food) {
        String productName = food.getProductName();
        if (productName == null || productName.isBlank()) {
            return food.getId() == null ? "" : food.getId();
        }
        String baseName = PARENS.matcher(productName).replaceAll(" ")
                .split("[,;:/|]")[0];
        String normalized = normalizeText(baseName);
        return normalized.isEmpty() ? productName.toLowerCase(Locale.ROOT) : normalized;
    }

    private String cleanQuery(String query) {
        return normalizeText(query);
    }

    private List<String> queryTokens(String cleanQuery) {
        if (cleanQuery.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(cleanQuery.split(" "));
    }

    private String normalizedSearchableText(Food food) {
        return normalizeText(String.join(" ",
                Objects.toString(food.getProductName(), ""),
                Objects.toString(food.getGenericName(), ""),
                Objects.toString(food.getBrands(), ""),
                food.getKeywords() == null ? "" : String.join(" ", food.getKeywords())));
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = NON_WORD.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return SPACES.matcher(normalized).replaceAll(" ").trim();
    }

    private Query buildSuggestionQuery(String normalized) {
        return Query.of(q -> q.bool(b -> b
                .filter(f -> f.bool(filterBool -> {
                    filterBool.should(s -> s.match(m -> m.field("moderation_status")
                            .query("APPROVED")));
                    filterBool.should(s -> s.bool(noUserId -> noUserId
                            .mustNot(mn -> mn.exists(e -> e.field("user_id")))));
                    filterBool.minimumShouldMatch("1");
                    return filterBool;
                }))
                .should(s1 -> s1.matchPhrase(mp -> mp
                        .field("product_name")
                        .query(normalized)
                        .boost(10f)))
                .should(s2 -> s2.multiMatch(m -> m
                        .fields("product_name",
                                "product_name._2gram",
                                "product_name._3gram")
                        .query(normalized)
                        .type(TextQueryType.BoolPrefix)
                        .boost(4f)))
                .should(s3 -> s3.match(m -> m
                        .field("product_name_ngram")
                        .query(normalized)
                        .fuzziness("AUTO")
                        .boost(2f)))
                .minimumShouldMatch("1")
        ));
    }

    private void processBarcode(BoolQuery.Builder b, String tokenNoZeros) {
        String[] barcodeFormats = {
                padLeft(tokenNoZeros, 13),
                padLeft(tokenNoZeros, 8),
                padLeft(tokenNoZeros, 12),
                padLeft(tokenNoZeros, 24)
        };
        for (String barcode : barcodeFormats) {
            b.should(s -> s.term(t -> t.field("code").value(barcode).boost(5f)));
            b.should(s -> s.term(t -> t.field("original_food_id").value(barcode).boost(5f)));
        }
    }

    private String padLeft(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        return "0".repeat(length - str.length()) + str;
    }
}
