package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchDao;
import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchResult;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FoodSearchPaginationTest {
    @Test
    void searchPreservesElasticsearchOrderAndReturnsExactTotal() throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        FoodSearchDao foodSearchDao = new FoodSearchDao(client);
        List<Hit<Food>> hits = List.of(
                hit("2", "Chicken breast"),
                hit("1", "Chicken soup")
        );
        SearchResponse<Food> response = SearchResponse.of(search -> search
                .hits(result -> result
                        .total(total -> total.value(42).relation(TotalHitsRelation.Eq))
                        .hits(hits))
                .took(1)
                .timedOut(false)
                .shards(shards -> shards.successful(1).failed(0).total(1)));
        when(client.search(
                org.mockito.ArgumentMatchers
                        .<Function<SearchRequest.Builder,
                                ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)))
                .thenReturn(response);

        FoodSearchResult result = foodSearchDao.search(
                "chiken", 1L, Collections.emptyList(), 0, 25);

        assertEquals(42, result.total());
        assertEquals(List.of("2", "1"), result.items().stream()
                .map(Food::getId)
                .toList());
    }

    private Hit<Food> hit(String id, String productName) {
        Food food = Food.builder().productName(productName).build();
        return Hit.of(hit -> hit.index("foods").id(id).source(food));
    }
}
