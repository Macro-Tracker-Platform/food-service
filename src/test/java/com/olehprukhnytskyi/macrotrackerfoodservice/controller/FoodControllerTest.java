package com.olehprukhnytskyi.macrotrackerfoodservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.ObjectBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.dto.Pagination;
import com.olehprukhnytskyi.dto.ProblemDetails;
import com.olehprukhnytskyi.exception.error.BaseErrorCode;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsPatchDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.GeminiService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.ImageService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.S3StorageService;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.UnitType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

class FoodControllerTest extends AbstractIntegrationTest {
    protected static MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NutrimentsMapper nutrimentsMapper;

    @MockitoBean
    private S3Client s3Client;
    @MockitoBean
    private S3StorageService s3StorageService;
    @MockitoBean
    private GeminiService geminiService;
    @MockitoBean
    private ImageService imageService;
    @MockitoBean
    private ElasticsearchClient elasticsearchClient;

    @MockitoSpyBean
    private FoodService foodService;
    @MockitoSpyBean
    private FoodRepository foodRepository;
    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    private FoodRequestDto foodRequestDto;
    private FoodResponseDto foodResponseDto;
    private Food food;

    @BeforeAll
    static void beforeAll(@Autowired WebApplicationContext applicationContext) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    @BeforeEach
    void setUp() {
        foodRepository.deleteAll();

        NutrimentsDto nutrimentsDto = new NutrimentsDto();
        nutrimentsDto.setCalories(BigDecimal.valueOf(100));
        nutrimentsDto.setCarbohydrates(BigDecimal.valueOf(100));
        nutrimentsDto.setFat(BigDecimal.valueOf(100));
        nutrimentsDto.setProtein(BigDecimal.valueOf(100));

        foodRequestDto = new FoodRequestDto();
        foodRequestDto.setCode("12345678");
        foodRequestDto.setProductName("Product name");
        foodRequestDto.setGenericName("Generic name");
        foodRequestDto.setBrands("Brands");
        foodRequestDto.setNutriments(nutrimentsDto);

        foodResponseDto = new FoodResponseDto();
        foodResponseDto.setCode("12345678");
        foodResponseDto.setProductName("Product name");
        foodResponseDto.setGenericName("Generic name");
        foodResponseDto.setBrands("Brands");
        foodResponseDto.setImageUrl("https://image.url");
        foodResponseDto.setNutriments(nutrimentsDto);
        foodResponseDto.setAvailableUnits(List.of(UnitType.GRAMS));

        food = Food.builder()
                .productName("Product name")
                .genericName("Generic name")
                .code("12345678")
                .brands("Brands")
                .imageUrl("https://image.url")
                .nutriments(nutrimentsMapper.toModel(nutrimentsDto))
                .build();

        foodRepository.save(Food.builder()
                .id("11111111")
                .code("11111111")
                .userId(1L)
                .productName("Rice")
                .nutriments(Nutriments.builder()
                        .calories(BigDecimal.valueOf(100))
                        .carbohydrates(BigDecimal.valueOf(25))
                        .fat(BigDecimal.valueOf(5))
                        .protein(BigDecimal.valueOf(15))
                        .build())
                .build());
        foodRepository.save(Food.builder()
                .id("22222222")
                .code("22222222")
                .userId(2L)
                .productName("Potato")
                .nutriments(Nutriments.builder()
                        .calories(BigDecimal.valueOf(50))
                        .carbohydrates(BigDecimal.valueOf(18))
                        .fat(BigDecimal.valueOf(4))
                        .protein(BigDecimal.valueOf(12))
                        .build())
                .build());
        foodRepository.save(Food.builder()
                .id("33333333")
                .code("33333333")
                .userId(3L)
                .productName("Tomato")
                .nutriments(Nutriments.builder()
                        .calories(BigDecimal.valueOf(20))
                        .carbohydrates(BigDecimal.valueOf(5))
                        .fat(BigDecimal.valueOf(2))
                        .protein(BigDecimal.valueOf(1))
                        .build())
                .build());
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

    @Test
    @DisplayName("When food exist, should return 200 OK and DTO")
    void findById_whenFoodExist_shouldReturn200AndDto() throws Exception {
        // Given
        String foodId = "11111111";
        FoodResponseDto foodDto = FoodResponseDto.builder()
                .id(foodId)
                .code(foodId)
                .userId(1L)
                .productName("Rice")
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.valueOf(100))
                        .carbohydrates(BigDecimal.valueOf(25))
                        .fat(BigDecimal.valueOf(5))
                        .protein(BigDecimal.valueOf(15))
                        .build())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                get("/api/foods/" + foodId))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(foodDto);
        String actual = mvcResult.getResponse().getContentAsString();
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.LENIENT);
    }

    @Test
    @DisplayName("When food exist, should return 200 OK and DTO")
    void findByQuery_whenFoodExist_shouldReturn200AndDto() throws Exception {
        // Given
        SearchResponse<Food> elasticResponse = generateSearchResponse(List.of(food));

        Pagination pagination = new Pagination(0, 10, 1);
        PagedResponse<FoodResponseDto> pagedResponse = new PagedResponse<>(
                List.of(foodResponseDto), pagination);
        String expected = objectMapper.writeValueAsString(pagedResponse);

        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(elasticResponse);

        // When
        MvcResult mvcResult = mockMvc.perform(
                get("/api/foods")
                        .param("query", "apple")
                        .param("offset", "0")
                        .param("limit", "10")
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When food does not exist, should return 204 No Content and an empty list")
    void findByQuery_whenFoodDoesNotExist_shouldReturnNoContentAndEmptyList()
            throws Exception {
        // Given
        Pagination pagination = new Pagination(0, 10, 0);
        PagedResponse<FoodResponseDto> pagedResponse = new PagedResponse<>(
                Collections.emptyList(), pagination);
        String expected = objectMapper.writeValueAsString(pagedResponse);

        SearchResponse<Food> elasticResponse = generateSearchResponse(List.of());

        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(elasticResponse);

        // When
        MvcResult mvcResult = mockMvc.perform(
                get("/api/foods")
                        .param("query", "apple")
                        .param("offset", "0")
                        .param("limit", "10")
                )
                .andExpect(status().isNoContent())
                .andReturn();

        // Then
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When offset is negative, should throw validation exception")
    void findByQuery_whenOffsetIsNegative_shouldThrowValidationException()
            throws Exception {
        // Given
        BaseErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .detail("Validation failed for one or more parameters")
                .traceId(MDC.get("traceId"))
                .code(errorCode.getCode())
                .traceId("N/A")
                .invalidParams(List.of(new ProblemDetails.InvalidParam(
                        "findByQuery.offset",
                        "must be greater than or equal to 0"
                )))
                .build();

        String expected = objectMapper.writeValueAsString(problemDetails);

        // When
        MvcResult mvcResult = mockMvc.perform(
                get("/api/foods")
                        .param("query", "apple")
                        .param("offset", "-10")
                        .param("limit", "10")
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When limit is negative, should throw validation exception")
    void findByQuery_whenLimitIsNegative_shouldThrowValidationException()
            throws Exception {
        // Given
        BaseErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .detail("Validation failed for one or more parameters")
                .traceId(MDC.get("traceId"))
                .code(errorCode.getCode())
                .traceId("N/A")
                .invalidParams(List.of(new ProblemDetails.InvalidParam(
                        "findByQuery.limit",
                        "must be greater than or equal to 1"
                )))
                .build();

        String expected = objectMapper.writeValueAsString(problemDetails);

        // When
        MvcResult mvcResult = mockMvc.perform(
                get("/api/foods")
                        .param("query", "apple")
                        .param("offset", "0")
                        .param("limit", "-10")
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When query is valid and suggestions exist,"
            + " should return 200 OK and suggestion list")
    void getSearchSuggestions_whenSuggestionsExist_shouldReturnOkAndList()
            throws Exception {
        // Given
        SearchResponse<Food> searchResponse = generateSearchResponse(List.of(food));

        List<String> mockSuggestions = List.of("Product name");
        String expected = objectMapper.writeValueAsString(mockSuggestions);

        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(searchResponse);

        // When & Then
        mockMvc.perform(
                get("/api/foods/search-suggestions")
                        .param("query", "apple")
                )
                .andExpect(status().isOk())
                .andExpect(content().json(expected));
    }

    @Test
    @DisplayName("When query is valid but no suggestions found, should return 204 No Content")
    void getSearchSuggestions_whenNoSuggestions_shouldReturnNoContent()
            throws Exception {
        // Given
        SearchResponse<Food> searchResponse = generateSearchResponse(List.of());

        when(elasticsearchClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder,
                        ObjectBuilder<SearchRequest>>>any(),
                eq(Food.class)
        )).thenReturn(searchResponse);

        // When & Then
        mockMvc.perform(
                get("/api/foods/search-suggestions")
                        .param("query", "xyz")
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("When query param is missing, should return 400 Bad Request")
    void getSearchSuggestions_whenQueryMissing_shouldReturnBadRequest()
            throws Exception {
        mockMvc.perform(get("/api/foods/search-suggestions"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When request is valid, should store and return 201 Created")
    void save_whenRequestIsValid_shouldPersistInMongo() throws Exception {
        // Given
        String foodJson = objectMapper.writeValueAsString(foodRequestDto);

        MockMultipartFile foodPart = new MockMultipartFile(
                "food",
                "food.json",
                MediaType.APPLICATION_JSON_VALUE,
                foodJson.getBytes()
        );

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image-content".getBytes()
        );

        when(imageService.resizeImage(any(), anyInt()))
                .thenReturn(new ByteArrayInputStream("fake".getBytes()));
        when(imageService.generateImageKey(any(), anyString(), anyInt()))
                .thenReturn("images/products/test.png");
        doNothing().when(imageService).validateImage(any());
        when(s3StorageService.uploadFile(any(), anyLong(), anyString(), anyString()))
                .thenReturn("https://mock-s3/image.jpg");
        when(geminiService.generateKeywords(any())).thenReturn(List.of());
        when(imageService.resizeImage(any(), anyInt()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));

        // When
        MvcResult mvcResult = mockMvc.perform(
                multipart("/api/foods")
                        .file(foodPart)
                        .file(imagePart)
                        .header(CustomHeaders.X_USER_ID, 1L)
                        .header(CustomHeaders.X_REQUEST_ID, "requestId")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isCreated())
                .andReturn();
        FoodResponseDto response = objectMapper.readValue(mvcResult
                .getResponse().getContentAsString(), FoodResponseDto.class);

        // Then
        assertThat(response.getCode()).isEqualTo("12345678");
        assertThat(response.getProductName()).isEqualTo("Product name");
        assertThat(response.getImageUrl()).isEqualTo("https://mock-s3/image.jpg");

        Optional<Food> saved = foodRepository.findById("12345678");
        assertThat(saved).isPresent();
        assertThat(saved.get().getProductName()).isEqualTo("Product name");

        verify(s3StorageService, times(1))
                .uploadFile(any(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("When request has validation errors, should throw validation exception")
    void save_whenValidationError_shouldThrowValidationException() throws Exception {
        // Given
        foodRequestDto.setProductName(null);
        String foodJson = objectMapper.writeValueAsString(foodRequestDto);

        MockMultipartFile foodPart = new MockMultipartFile(
                "food",
                "food.json",
                MediaType.APPLICATION_JSON_VALUE,
                foodJson.getBytes()
        );

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake".getBytes()
        );

        BaseErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .detail("Validation failed for one or more parameters")
                .traceId(MDC.get("traceId"))
                .code(errorCode.getCode())
                .traceId("N/A")
                .invalidParams(List.of(new ProblemDetails.InvalidParam(
                        "productName",
                        "must not be null"
                )))
                .build();

        String expected = objectMapper.writeValueAsString(problemDetails);

        // When & Then
        mockMvc.perform(
                multipart("/api/foods")
                        .file(foodPart)
                        .file(imagePart)
                        .header(CustomHeaders.X_USER_ID, 1L)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expected));
    }

    @Test
    @DisplayName("When patch is successful, should return 200 and updated DTO")
    void patch_whenSuccessful_shouldReturn200AndUpdatedDto() throws Exception {
        // Given
        String id = "11111111";

        FoodRequestDto requestDto = FoodRequestDto.builder()
                .productName("New product name")
                .build();
        String requestJson = objectMapper.writeValueAsString(requestDto);

        FoodResponseDto responseDto = FoodResponseDto.builder()
                .id("11111111")
                .code("11111111")
                .productName("New product name")
                .userId(1L)
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.valueOf(100))
                        .carbohydrates(BigDecimal.valueOf(25))
                        .fat(BigDecimal.valueOf(5))
                        .protein(BigDecimal.valueOf(15))
                        .build())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();
        String expected = objectMapper.writeValueAsString(responseDto);

        // When & Then
        mockMvc.perform(
                patch("/api/foods/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(expected));
    }

    @Test
    @DisplayName("When patch request has validation errors, should throw validation exception")
    void patch_whenValidationError_shouldThrowValidationException() throws Exception {
        // Given
        String id = "11111111";

        NutrimentsPatchDto nutrimentsPatchDto = NutrimentsPatchDto.builder()
                .carbohydrates(BigDecimal.valueOf(-100))
                .build();

        FoodPatchRequestDto requestDto = FoodPatchRequestDto.builder()
                .nutriments(nutrimentsPatchDto)
                .build();

        String requestJson = objectMapper.writeValueAsString(requestDto);

        BaseErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .detail("Validation failed for one or more parameters")
                .traceId(MDC.get("traceId"))
                .traceId("N/A")
                .code(errorCode.getCode())
                .invalidParams(List.of(new ProblemDetails.InvalidParam(
                        "nutriments.carbohydrates",
                        "must be greater than or equal to 0.0"
                )))
                .build();

        String expected = objectMapper.writeValueAsString(problemDetails);

        // When & Then
        mockMvc.perform(
                patch("/api/foods/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expected));
    }

    @Test
    @DisplayName("When valid request, should return delete food")
    void deleteFood_whenValidRequest_shouldDeleteFood() throws Exception {
        // Given
        String foodId = "22222222";

        assertThat(foodRepository.findById(foodId)).isPresent();

        // When
        mockMvc.perform(
                delete("/api/foods/{id}", foodId)
                        .header(CustomHeaders.X_USER_ID, 2)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Then
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        assertThat(foodRepository.findById(foodId)).isEmpty();
    }
}
