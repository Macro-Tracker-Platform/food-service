package com.olehprukhnytskyi.macrotrackerfoodservice.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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
        String normalizedQuery = query.trim().toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", "");
        String[] tokens = normalizedQuery.split("\\s+");
        return Query.of(q -> q.bool(b -> {
            for (String token : tokens) {
                String fuzziness = token.length() > 3 ? "AUTO" : "0";
                b.must(mustBuilder -> mustBuilder.bool(tokenBool -> {
                    tokenBool.should(s -> s.multiMatch(mm -> mm
                            .fields("product_name^4", "_keywords^3", "generic_name^2", "brands^2")
                            .query(token)
                            .fuzziness(fuzziness)
                    ));
                    if (token.matches("\\d{8,24}")) {
                        String tokenNoZeros = token.replaceFirst("^0+(?!$)", "");
                        processBarcode(tokenBool, tokenNoZeros);
                    }
                    return tokenBool;
                }));
            }
            b.filter(f -> f.bool(boolFilter -> {
                boolFilter.should(s -> s.term(t -> t.field("moderation_status.keyword")
                        .value("APPROVED")));
                if (userId != null) {
                    boolFilter.should(s -> s.term(t -> t.field("user_id").value(userId)));
                }
                boolFilter.minimumShouldMatch("1");
                return boolFilter;
            }));
            if (excludedIds != null && !excludedIds.isEmpty()) {
                List<FieldValue> excludedValues = excludedIds.stream()
                        .map(FieldValue::of)
                        .toList();
                b.mustNot(mn -> mn.terms(t -> t
                        .field("_id")
                        .terms(ts -> ts.value(excludedValues))
                ));
            }
            return b;
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
