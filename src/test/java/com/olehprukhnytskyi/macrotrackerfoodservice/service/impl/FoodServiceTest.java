package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.anyList;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeast;

import com.mongodb.DuplicateKeyException;
import com.olehprukhnytskyi.exception.ConflictException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchDao;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodListCacheWrapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.mapper.FoodMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodAssetService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodCodeGenerator;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.ImageService;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.retry.support.RetryTemplate;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {
    @Mock
    private FoodRepository foodRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private FoodSearchDao foodSearchDao;
    @Mock
    private FoodCodeGenerator foodCodeGenerator;
    @Mock
    private FoodAssetService foodAssetService;
    @Mock
    private NutrimentsMapper nutrimentsMapper;
    @Mock
    private FoodMapper foodMapper;
    @Mock
    private ImageService imageService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Spy
    private RetryTemplate retryTemplate = new RetryTemplate();

    @InjectMocks
    private FoodService foodService;

    @Mock
    private Nutriments nutriments;
    private FoodRequestDto foodRequestDto;
    private Food food;
    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        NutrimentsDto nutrimentsDto = new NutrimentsDto();
        nutrimentsDto.setCaloriesPer100(BigDecimal.ONE);
        nutrimentsDto.setCarbohydratesPer100(BigDecimal.ONE);
        nutrimentsDto.setFatPer100(BigDecimal.ONE);
        nutrimentsDto.setProteinPer100(BigDecimal.ONE);

        nutrimentsDto.setCaloriesPer100(BigDecimal.ONE);
        nutrimentsDto.setCarbohydratesPer100(BigDecimal.ONE);
        nutrimentsDto.setFatPer100(BigDecimal.ONE);
        nutrimentsDto.setProteinPer100(BigDecimal.ONE);

        foodRequestDto = new FoodRequestDto();
        foodRequestDto.setCode("code");
        foodRequestDto.setProductName("product_name");
        foodRequestDto.setBrands("brands");
        foodRequestDto.setGenericName("generic_name");
        foodRequestDto.setNutriments(nutrimentsDto);

        food = new Food();
        food.setProductName("product_name");
        food.setBrands("brands");
        food.setGenericName("generic_name");
        food.setNutriments(nutriments);

        image = new MockMultipartFile(
                "image",
                "image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );
    }

    @Test
    @DisplayName("When food with same code and fields exists, should return existing DTO")
    void createFoodWithImages_whenSameCodeExists_shouldReturnExistingDto() {
        // Given
        given(nutrimentsMapper.toModel(any())).willReturn(nutriments);
        given(foodRepository.findById(anyString())).willReturn(Optional.of(food));
        given(foodMapper.toDto((Food) any())).willReturn(new FoodResponseDto());

        // When
        FoodResponseDto result = foodService.createFoodWithImages(foodRequestDto, image, 1L);

        // Then
        assertNotNull(result);
        verify(foodRepository).findById(anyString());
        verify(foodRepository, never()).save(any());
    }

    @Test
    @DisplayName("When food do not exist, should save and return DTO")
    void createFoodWithImages_whenFoodDoNotExist_shouldCreateAndReturnDto() {
        // Given
        foodRequestDto.setCode(null);

        given(foodAssetService.uploadToTemp(any())).willReturn("temp/image-key.jpg");
        given(foodMapper.toModel(any())).willReturn(food);
        given(foodCodeGenerator.resolveCode(any())).willReturn("generated_code");
        given(foodAssetService.confirmImage(anyString(), any())).willReturn("final_path");
        given(foodRepository.save(any())).willReturn(food);
        given(foodMapper.toDto((Food) any())).willReturn(new FoodResponseDto());

        // When
        FoodResponseDto result = foodService.createFoodWithImages(foodRequestDto, image, 1L);

        // Then
        assertNotNull(result);
        verify(foodCodeGenerator).resolveCode(any());
        verify(foodAssetService).uploadToTemp(image);
        verify(foodAssetService).confirmImage(anyString(), any());
        verify(foodRepository, atLeast(1)).save(food);
    }

    @Test
    @DisplayName("When food with same code exists but different data,"
            + " should throw ConflictException")
    void createFoodWithImages_whenSameCodeExistsButDifferentData_shouldThrowException() {
        // Given
        Food differentFood = new Food();
        differentFood.setProductName("other_name");

        given(foodRepository.findById(anyString())).willReturn(Optional.of(differentFood));

        // When
        ConflictException conflictException = assertThrows(ConflictException.class,
                () -> foodService.createFoodWithImages(foodRequestDto, image, 1L));

        // Then
        String expected = "Food with this code already exists with different data";
        assertEquals(expected, conflictException.getMessage());
    }

    @Test
    @DisplayName("When DuplicateKeyException occurs max times, should throw Exception")
    void createFoodWithImages_whenDuplicateKeyExceptionMaxTimes_shouldThrowException() {
        // Given
        given(foodMapper.toModel(any())).willReturn(food);
        given(foodCodeGenerator.resolveCode(any())).willReturn("code");
        given(foodRepository.save(any())).willThrow(DuplicateKeyException.class);

        // When
        InternalServerException exception = assertThrows(InternalServerException.class,
                () -> foodService.createFoodWithImages(foodRequestDto, image, 1L));

        // Then
        assertEquals("Unexpected error while saving food", exception.getMessage());
        verify(foodRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("When DataIntegrityViolation occurs, should throw InternalServerException")
    void createFoodWithImages_whenDataIntegrityViolationOccurs_shouldThrowException() {
        // Given
        given(foodMapper.toModel(any())).willReturn(food);
        given(foodCodeGenerator.resolveCode(any())).willReturn("code");

        // When
        InternalServerException exception = assertThrows(InternalServerException.class,
                () -> foodService.createFoodWithImages(foodRequestDto, image, 1L));

        // Then
        assertEquals("Unexpected error while saving food", exception.getMessage());
    }

    @Test
    @DisplayName("When search succeeds, should return mapped DTO list")
    void findByQuery_whenSearchSucceeds_shouldReturnMappedDto() {
        // Given
        FoodResponseDto dto = new FoodResponseDto();
        dto.setId("123");

        given(foodSearchDao.search(anyString(), anyInt(), anyInt())).willReturn(List.of(food));
        given(foodMapper.toDto(anyList())).willReturn(List.of(dto));

        // When
        FoodListCacheWrapper result = foodService.findByQuery("apple", 0, 10);

        // Then
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
        assertEquals("123", result.getItems().get(0).getId());
    }

    @Test
    @DisplayName("When DAO throws runtime exception, Service should propagate or wrap it")
    void findByQuery_whenDaoThrowsException_shouldThrowException() {
        // Given
        given(foodSearchDao.search(anyString(), anyInt(), anyInt()))
                .willThrow(new InternalServerException(CommonErrorCode.BAD_REQUEST,
                        "Elastic Error"));

        // When & Then
        assertThrows(InternalServerException.class,
                () -> foodService.findByQuery("milk", 0, 10));
    }

    @Test
    @DisplayName("When query is null, should return an empty list")
    void getSearchSuggestions_whenQueryIsNull_shouldReturnEmptyList() {
        List<String> result = foodService.getSearchSuggestions(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("When query is blank, should return an empty list")
    void getSearchSuggestions_whenQueryIsBlank_shouldReturnEmptyList() {
        List<String> result = foodService.getSearchSuggestions("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("When getting suggestions, should delegate to DAO")
    void getSearchSuggestions_shouldDelegateToDao() {
        // Given
        List<String> suggestions = List.of("Apple", "Apricot");
        given(foodSearchDao.getSuggestions("ap")).willReturn(suggestions);

        // When
        List<String> result = foodService.getSearchSuggestions("ap");

        // Then
        assertEquals(2, result.size());
        assertEquals("Apple", result.get(0));
    }

    @Test
    @DisplayName("When food not found for patch, should throw NotFoundException")
    void patch_whenFoodNotFound_shouldThrowNotFoundException() {
        // When & Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> foodService.patch("123", new FoodPatchRequestDto(), 1L));

        assertTrue(ex.getMessage().contains("Food not found"));
    }

    @Test
    @DisplayName("When food exists, should update and return DTO")
    void patch_whenFoodExists_shouldUpdateAndReturnDto() {
        // Given
        String id = "123";
        Food existing = new Food();
        existing.setId(id);

        Food saved = new Food();
        saved.setId(id);
        saved.setProductName("Updated");

        FoodResponseDto expected = new FoodResponseDto();
        expected.setId(id);

        Long userId = 1L;

        given(foodRepository.findByIdAndUserId(id, userId)).willReturn(Optional.of(existing));
        given(foodRepository.save(existing)).willReturn(saved);
        given(foodMapper.toDto(saved)).willReturn(expected);

        // When
        FoodResponseDto result = foodService.patch(id, new FoodPatchRequestDto(), userId);

        // Then
        assertEquals(id, result.getId());
        verify(foodMapper).updateFoodFromPatchDto(any(), eq(existing));
    }

    @Test
    @DisplayName("When delete, should remove from repo and save to outbox")
    void deleteByIdAndUserId_shouldDeleteAndSaveOutbox() {
        // Given
        String id = "123";
        Long userId = 1L;

        // When
        foodService.deleteByIdAndUserId(id, userId);

        // Then
        verify(foodRepository).deleteByIdAndUserId(id, userId);
        verify(outboxRepository).save(any(OutboxEvent.class));
    }
}
