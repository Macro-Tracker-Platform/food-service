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
    private static final int SUGGESTION_CANDIDATE_LIMIT = 50;
    private static final int SUGGESTION_LIMIT = 10;
    private static final float APPROVED_BOOST = 2.0f;
    private static final float VERIFIED_BY_ADMIN_BOOST = 5.0f;
    private static final double VERIFIED_BY_ADMIN_RANK_BOOST = 5_000.0;
    private static final double VERIFIED_RAW_FOOD_RANK_BOOST = 12_000.0;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ElasticsearchClient elasticsearchClient;

    public FoodSearchResult search(String query, Long userId, List<String> excludedIds,
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
            SearchResponse<Food> response = elasticsearchClient.search(
                    s -> s.index("macro_tracker.foods")
                            .query(searchQuery)
                            .from(offset)
                            .size(limit)
                            .trackTotalHits(t -> t.enabled(true)),
                    Food.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return new FoodSearchResult(Collections.emptyList(), 0);
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
            int total = response.hits().total() == null
                    ? offset + foods.size()
                    : (int) Math.min(response.hits().total().value(), Integer.MAX_VALUE);
            return new FoodSearchResult(foods, total);
        } catch (IOException e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to execute search request", e);
        }
    }

    public List<String> getSuggestions(String query, Long userId, List<String> excludedIds) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = cleanQuery(query);
        if (normalized.isBlank()) {
            return Collections.emptyList();
        }
        try {
            SearchResponse<Food> response = elasticsearchClient.search(
                    s -> s.index("macro_tracker.foods")
                            .query(buildSuggestionQuery(normalized, userId, excludedIds))
                            .size(SUGGESTION_CANDIDATE_LIMIT),
                    Food.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return Collections.emptyList();
            }
            return response.hits().hits().stream()
                    .map(hit -> hit.source() != null ? hit.source().getProductName() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(SUGGESTION_LIMIT)
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
                        .type(TextQueryType.CrossFields)
                        .operator(Operator.And)
                ));
                searchBool.should(s -> s.multiMatch(mm -> mm
                        .fields("product_name^5", "product_name._2gram^3",
                                "product_name._3gram^2")
                        .query(cleanQuery)
                        .type(TextQueryType.BoolPrefix)
                ));
                searchBool.should(s -> s.multiMatch(mm -> mm
                        .fields("product_name^3", "_keywords^2", "generic_name^1")
                        .query(cleanQuery)
                        .operator(Operator.Or)
                        .minimumShouldMatch("70%")
                        .fuzziness("AUTO")
                ));
                if (cleanQuery.matches("^\\d{6,24}$")) {
                    String tokenNoZeros = cleanQuery.replaceFirst("^0+(?!$)", "");
                    processBarcode(searchBool, tokenNoZeros);
                }
                searchBool.minimumShouldMatch("1");
                return searchBool;
            }));
            addAccessFilters(mainBool, userId, excludedIds);
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
                        .boost(8.0f)
                ));
            }
            return mainBool;
        }));
    }

    public List<Food> searchPhotoCandidates(List<String> queries, Long userId,
                                            int limitPerItem) {
        List<String> cleaned = queries == null ? List.of() : queries.stream()
                .filter(Objects::nonNull)
                .map(this::cleanQuery)
                .filter(query -> !query.isBlank())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }
        int size = Math.min(50, Math.max(limitPerItem, cleaned.size() * limitPerItem));
        try {
            Query query = Query.of(q -> q.bool(mainBool -> {
                cleaned.forEach(cleanQuery -> mainBool.should(should -> should.multiMatch(mm -> mm
                        .fields("product_name^8", "generic_name^5", "_keywords^3", "brands^2")
                        .query(cleanQuery)
                        .operator(Operator.Or)
                        .fuzziness("AUTO")
                )));
                mainBool.minimumShouldMatch("1");
                addAccessFilters(mainBool, userId, Collections.emptyList());
                return mainBool;
            }));
            SearchResponse<Food> response = elasticsearchClient.search(
                    search -> search.index("macro_tracker.foods")
                            .query(query)
                            .size(size),
                    Food.class
            );
            if (response == null || response.hits() == null
                    || response.hits().hits() == null) {
                return Collections.emptyList();
            }
            return response.hits().hits().stream()
                    .map(hit -> {
                        Food food = hit.source();
                        if (food != null) {
                            food.setId(hit.id());
                        }
                        return food;
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException exception) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to execute food photo search", exception);
        }
    }

    /**
     * Retained for compatibility with existing ranking tests. Search results no longer pass
     * through this method because Elasticsearch relevance must remain authoritative.
     */
    @Deprecated(forRemoval = true)
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
        String searchableText = normalizeText(String.join(" ",
                Objects.toString(food.getProductName(), ""),
                Objects.toString(food.getGenericName(), ""),
                Objects.toString(food.getBrands(), ""),
                food.getKeywords() == null ? "" : String.join(" ", food.getKeywords())));
        return queryTokens.stream().allMatch(searchableText::contains);
    }

    private double relevanceScore(Food food, String cleanQuery, List<String> queryTokens) {
        String productName = normalizeText(food.getProductName());
        double score = productName.equals(cleanQuery) ? 10_000.0 : 0.0;
        if (productName.startsWith(cleanQuery)) {
            score += 4_000.0;
        }
        if (productName.contains(cleanQuery)) {
            score += 1_500.0;
        }
        if (queryTokens.stream().allMatch(productName::contains)) {
            score += 700.0;
        }
        if (food.isVerifiedByAdmin()) {
            score += VERIFIED_BY_ADMIN_RANK_BOOST;
        }
        if (food.isVerifiedByAdmin()
                && (productName.equals(cleanQuery + " raw")
                || productName.startsWith(cleanQuery + " raw "))) {
            score += VERIFIED_RAW_FOOD_RANK_BOOST;
        }
        return score;
    }

    @Deprecated(forRemoval = true)
    List<Food> diversifySimilarProducts(List<Food> foods, int offset, int limit) {
        Map<String, List<Food>> groupedFoods = new LinkedHashMap<>();
        foods.forEach(food -> groupedFoods
                .computeIfAbsent(diversityKey(food), ignored -> new ArrayList<>())
                .add(food));
        List<List<Food>> groups = new ArrayList<>(groupedFoods.values());
        List<Food> diversified = new ArrayList<>(foods.size());
        for (int rank = 0; diversified.size() < foods.size(); rank++) {
            for (List<Food> group : groups) {
                if (rank < group.size()) {
                    diversified.add(group.get(rank));
                }
            }
        }
        return diversified.stream().skip(offset).limit(limit).toList();
    }

    private String diversityKey(Food food) {
        String productName = Objects.toString(food.getProductName(), "");
        String baseName = PARENS.matcher(productName).replaceAll(" ")
                .split("[,;:/|]")[0];
        String normalized = normalizeText(baseName);
        return normalized.isEmpty() ? productName.toLowerCase(Locale.ROOT) : normalized;
    }

    private List<String> queryTokens(String cleanQuery) {
        return cleanQuery.isBlank() ? Collections.emptyList() : List.of(cleanQuery.split(" "));
    }

    private String cleanQuery(String query) {
        return normalizeText(query);
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

    private Query buildSuggestionQuery(String normalized, Long userId,
                                       List<String> excludedIds) {
        return Query.of(q -> q.bool(b -> {
            addAccessFilters(b, userId, excludedIds);
            b.should(s1 -> s1.matchPhrase(mp -> mp
                        .field("product_name")
                        .query(normalized)
                        .boost(10f)));
            b.should(s2 -> s2.multiMatch(m -> m
                        .fields("product_name",
                                "product_name._2gram",
                                "product_name._3gram")
                        .query(normalized)
                        .type(TextQueryType.BoolPrefix)
                        .boost(4f)));
            b.should(s3 -> s3.match(m -> m
                        .field("product_name_ngram")
                        .query(normalized)
                        .fuzziness("AUTO")
                        .boost(2f)));
            b.minimumShouldMatch("1");
            return b;
        }));
    }

    private void addAccessFilters(BoolQuery.Builder query, Long userId,
                                  List<String> excludedIds) {
        query.filter(f -> f.bool(filterBool -> {
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
        addVisibleFilter(query);
        if (excludedIds != null && !excludedIds.isEmpty()) {
            query.mustNot(mn -> mn.ids(i -> i.values(excludedIds)));
        }
    }

    private void addVisibleFilter(BoolQuery.Builder query) {
        query.filter(f -> f.bool(visible -> {
            visible.should(s -> s.term(t -> t.field("visible").value(true)));
            visible.should(s -> s.bool(missing -> missing
                    .mustNot(mn -> mn.exists(e -> e.field("visible")))));
            visible.minimumShouldMatch("1");
            return visible;
        }));
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
