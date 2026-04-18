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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FoodSearchDao {
    private final ElasticsearchClient elasticsearchClient;

    public List<Food> search(String query, Long userId, List<String> excludedIds,
                             int offset, int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Query must not be null or empty");
        }
        try {
            Query searchQuery = buildSearchQuery(query, userId, excludedIds);
            SearchResponse<Food> response = elasticsearchClient.search(
                    s -> s.index("macro_tracker.foods")
                            .query(searchQuery)
                            .from(offset)
                            .size(limit),
                    Food.class
            );
            if (response == null || response.hits() == null || response.hits().hits() == null) {
                return Collections.emptyList();
            }
            return response.hits().hits().stream()
                    .map(hit -> {
                        if (hit.source() == null) {
                            return null;
                        }
                        hit.source().setId(hit.id());
                        return hit.source();
                    })
                    .filter(Objects::nonNull)
                    .toList();
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

    private Query buildSearchQuery(String query, Long userId, List<String> excludedIds) {
        String cleanQuery = query.trim().replaceAll("[^\\p{L}\\p{N}\\s]", "").trim();
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
                    .boost(100.0f)
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

    private Query buildSuggestionQuery(String normalized) {
        return Query.of(q -> q.bool(b -> b
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
        b.should(s -> s.term(t -> t.field("code").value(padLeft(tokenNoZeros, 13)).boost(5f)));
        b.should(s -> s.term(t -> t.field("code").value(padLeft(tokenNoZeros, 8)).boost(5f)));
        b.should(s -> s.term(t -> t.field("code").value(padLeft(tokenNoZeros, 12)).boost(5f)));
        b.should(s -> s.term(t -> t.field("code").value(padLeft(tokenNoZeros, 24)).boost(5f)));
    }

    private String padLeft(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        return "0".repeat(length - str.length()) + str;
    }
}
