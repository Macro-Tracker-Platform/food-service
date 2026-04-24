package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.ObjectBuilder;
import com.olehprukhnytskyi.macrotrackerfoodservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodListCacheWrapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.S3StorageService;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.ModerationStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.DigestUtils;

class FoodServiceCacheTest extends AbstractIntegrationTest {
    @MockitoBean
    private LockProvider lockProvider;
    @MockitoBean
    private FoodRepository foodRepository;
    @MockitoBean
    private OutboxRepository outboxRepository;
    @MockitoBean
    private S3StorageService s3StorageService;
    @MockitoBean
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private FoodService foodService;

    @Test
    @DisplayName("Should cache search results for same query and pagination")
    void findByQuery_shouldCacheSameQueryAndPagination() throws IOException {
        // Given
        Long userId = 1L;
        String query = "apple";
        int offset = 0;
        int limit = 10;

        Food food = Food.builder()
                .id("1")
                .productName("Apple")
                .userId(userId)
                .build();
        SearchResponse<Food> mockResponse = generateSearchResponse(List.of(food));

        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(mockResponse);

        // When
        FoodListCacheWrapper first = foodService.findByQuery(query, userId, offset, limit);
        FoodListCacheWrapper second = foodService.findByQuery(query, userId, offset, limit);

        // Then
        verify(elasticsearchClient, times(1)).search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        );
    }

    @Test
    @DisplayName("Should use cache on second call")
    void findPersonalizedById_shouldUseCacheOnSecondCall() {
        // Given
        Long userId = 1L;
        String id = "11111111";
        Food entity = Food.builder()
                .id(id)
                .code(id)
                .userId(userId)
                .productName("Rice")
                .verifiedByAdmin(true)
                .moderationStatus(ModerationStatus.APPROVED)
                .build();

        when(foodRepository.findById(id)).thenReturn(Optional.of(entity));

        // When
        FoodResponseDto dto1 = foodService.findPersonalizedById(id, userId);
        verify(foodRepository, times(1)).findById(id);

        Object cached1 = redisTemplate.opsForValue().get(CacheConstants.FOOD_DATA + "::" + id);
        assertThat(cached1).isNotNull();
        assertThat(((FoodResponseDto) cached1).getId()).isEqualTo(id);

        FoodResponseDto dto2 = foodService.findPersonalizedById(id, userId);
        verify(foodRepository, times(1)).findById(id);

        // Then
        assertThat(dto2.getId()).isEqualTo(dto1.getId());
        assertThat(dto2.getProductName()).isEqualTo(dto1.getProductName());
    }

    @Test
    @DisplayName("Should return cached search suggestions on second call")
    void getSearchSuggestions_shouldReturnCachedSearchSuggestions() throws IOException {
        // Given
        String query = "ric";
        List<String> productNames = new ArrayList<>(List.of("rice", "ricotta"));

        List<Food> foods = productNames.stream()
                .map(productName -> Food.builder()
                        .productName(productName)
                        .build())
                .toList();

        SearchResponse<Food> mockResponse = generateSearchResponse(foods);
        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(mockResponse);

        // When
        List<String> first = foodService.getSearchSuggestions(query);
        List<String> second = foodService.getSearchSuggestions(query);

        // Then
        verify(elasticsearchClient, times(1)).search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        );

        String cacheKey = CacheConstants.SEARCH_SUGGESTIONS + "::"
                + DigestUtils.md5DigestAsHex(query.trim().toLowerCase().getBytes());
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cached).isNotNull();

        @SuppressWarnings("unchecked")
        List<String> cachedList = (List<String>) cached;
        assertThat(cachedList).containsExactlyInAnyOrderElementsOf(productNames);

        assertThat(second).containsExactlyInAnyOrderElementsOf(productNames);
    }

    @Test
    @DisplayName("Should delete entity and clear cache")
    void deleteByIdAndUserId_shouldClearCache() {
        // Given
        String id = "123";
        Long userId = 5L;
        String cacheKey = CacheConstants.FOOD_DATA + "::" + id;

        FoodResponseDto dto = FoodResponseDto.builder()
                .id(id)
                .productName("Orange")
                .build();
        redisTemplate.opsForValue().set(cacheKey, dto);

        assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotNull();

        doNothing().when(foodRepository).deleteByIdAndUserId(id, userId);
        doNothing().when(s3StorageService).deleteFolder(anyString());

        // When
        foodService.deleteByIdAndUserId(id, userId);

        // Then
        verify(foodRepository, times(1)).deleteByIdAndUserId(id, userId);
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        assertThat(redisTemplate.opsForValue().get(cacheKey)).isNull();
    }

    private <T> SearchResponse<T> generateSearchResponse(List<T> elements) {
        @SuppressWarnings("unchecked")
        List<Hit<T>> hits = elements.stream()
                .map(element -> (Hit<T>) Hit.of(hit -> hit
                        .index("1")
                        .source(element)))
                .toList();
        return SearchResponse.of(sr -> sr
                .hits(h -> h.hits(hits))
                .took(100)
                .timedOut(false)
                .shards(builder -> builder.successful(1).failed(0).total(1))
        );
    }
}
