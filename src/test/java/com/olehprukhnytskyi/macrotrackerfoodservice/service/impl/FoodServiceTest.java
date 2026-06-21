package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.anyList;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import com.mongodb.DuplicateKeyException;
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
import com.olehprukhnytskyi.macrotrackerfoodservice.model.UserFoodFavorite;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.UserFoodFavoriteRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodAssetService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodCodeGenerator;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.ImageService;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.ModerationStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {
    @Mock
    private FoodRepository foodRepository;
    @Mock
    private UserFoodFavoriteRepository userFoodFavoriteRepository;
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
        foodService.setSelf(foodService);

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
    @DisplayName("When food is not public, should skip moderation and keep it private")
    void createFoodWithImages_whenFoodIsNotPublic_shouldSaveAsRejected() {
        // Given
        foodRequestDto.setCode(null);
        foodRequestDto.setPublic(false);

        given(foodMapper.toModel(any())).willReturn(food);
        given(foodCodeGenerator.resolveCode(any())).willReturn("generated_code");
        given(foodRepository.save(any())).willReturn(food);
        given(foodMapper.toDto((Food) any())).willReturn(new FoodResponseDto());

        // When
        FoodResponseDto result = foodService.createFoodWithImages(foodRequestDto, null, 1L);

        // Then
        assertNotNull(result);
        assertEquals(ModerationStatus.REJECTED, food.getModerationStatus());
        assertEquals(1L, food.getUserId());
        verify(foodRepository).save(food);
    }

    @Test
    @DisplayName("When food with same code exists but different data, "
                 + "should redirect to customize flow")
    void createFoodWithImages_whenSameCodeExistsButDifferentData_shouldRedirectToCustomize() {
        // Given
        String existingCode = "12345678";
        foodRequestDto.setCode(existingCode);

        Food existingFood = new Food();
        existingFood.setId(existingCode);
        existingFood.setProductName("other_name");
        existingFood.setUserId(99L);
        existingFood.setModerationStatus(ModerationStatus.APPROVED);
        existingFood.setVerifiedByAdmin(true);

        Food customizedFood = new Food();
        customizedFood.setId("custom-id-123");

        FoodResponseDto customizedResponse = new FoodResponseDto();
        customizedResponse.setId("custom-id-123");

        given(foodRepository.findById(existingCode)).willReturn(Optional.of(existingFood));
        given(foodMapper.toPatchDto(foodRequestDto)).willReturn(new FoodPatchRequestDto());
        given(foodRepository.save(any(Food.class))).willReturn(customizedFood);
        given(foodMapper.toDto(any(Food.class))).willReturn(customizedResponse);
        given(foodAssetService.uploadToTemp(image)).willReturn("temp-image-key");
        given(foodAssetService.confirmImage("temp-image-key", "custom-id-123"))
                .willReturn("https://s3/image.jpg");
        given(foodRepository.findById("custom-id-123")).willReturn(Optional.of(customizedFood));
        given(foodMapper.createCustomizedCopy(any(), anyLong())).willReturn(new Food());
        given(foodRepository.findByOriginalFoodIdAndUserId(any(), any()))
                .willReturn(Optional.empty());
        given(foodCodeGenerator.resolveCode(any())).willReturn("new-code-123");

        // When
        FoodResponseDto result = foodService.createFoodWithImages(foodRequestDto, image, 1L);

        // Then
        assertNotNull(result);
        assertEquals("custom-id-123", result.getId());

        verify(foodMapper).toPatchDto(foodRequestDto);
        verify(foodAssetService).uploadToTemp(image);
        verify(foodAssetService).confirmImage("temp-image-key", "custom-id-123");
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

        given(foodSearchDao.search(anyString(), anyLong(), any(), anyInt(), anyInt()))
                .willReturn(List.of(food));
        given(foodMapper.toDto(anyList())).willReturn(List.of(dto));

        // When
        FoodListCacheWrapper result = foodService.findByQuery("apple", 1L, 0, 10);

        // Then
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
        assertEquals("123", result.getItems().getFirst().getId());
    }

    @Test
    @DisplayName("When DAO throws runtime exception, Service should propagate or wrap it")
    void findByQuery_whenDaoThrowsException_shouldThrowException() {
        // Given
        given(foodSearchDao.search(anyString(), anyLong(), any(), anyInt(), anyInt()))
                .willThrow(new InternalServerException(CommonErrorCode.BAD_REQUEST,
                        "Elastic Error"));

        // When & Then
        assertThrows(InternalServerException.class,
                () -> foodService.findByQuery("milk", 1L, 0, 10));
    }

    @Test
    @DisplayName("When user fetches another user's private food, should not expose it")
    void findPersonalizedById_whenPrivateFoodBelongsToOtherUser_shouldThrowNotFound() {
        // Given
        FoodResponseDto privateFood = FoodResponseDto.builder()
                .id("private-food")
                .userId(2L)
                .moderationStatus(ModerationStatus.REJECTED.name())
                .verifiedByAdmin(false)
                .build();
        foodService.setSelf(foodService);

        given(foodRepository.findById("private-food")).willReturn(Optional.of(food));
        given(foodMapper.toDto(food)).willReturn(privateFood);

        // When & Then
        assertThrows(NotFoundException.class,
                () -> foodService.findPersonalizedById("private-food", 1L));
    }

    @Test
    @DisplayName("When batch contains another user's private food, should filter it out")
    void findAllByIds_whenPrivateFoodBelongsToOtherUser_shouldFilterItOut() {
        // Given
        Food privateFood = Food.builder()
                .id("private-food")
                .userId(2L)
                .moderationStatus(ModerationStatus.REJECTED)
                .verifiedByAdmin(false)
                .build();
        Food ownFood = Food.builder()
                .id("own-food")
                .userId(1L)
                .moderationStatus(ModerationStatus.REJECTED)
                .verifiedByAdmin(false)
                .build();
        FoodResponseDto ownDto = FoodResponseDto.builder().id("own-food").build();

        given(foodRepository.findByOriginalFoodIdInAndUserId(any(), any()))
                .willReturn(List.of());
        given(foodRepository.findAllById(any())).willReturn(List.of(privateFood, ownFood));
        given(foodMapper.toDto(ownFood)).willReturn(ownDto);

        // When
        List<FoodResponseDto> result = foodService.findAllByIds(
                List.of("private-food", "own-food"), 1L);

        // Then
        assertEquals(List.of(ownDto), result);
    }

    @Test
    @DisplayName("When favorite is enabled, should save user-specific favorite and return DTO")
    void updateFavorite_whenEnabled_shouldSaveFavoriteAndReturnDto() {
        // Given
        Long userId = 1L;
        String foodId = "banana";
        Food food = Food.builder()
                .id(foodId)
                .userId(null)
                .moderationStatus(ModerationStatus.APPROVED)
                .verifiedByAdmin(true)
                .build();
        FoodResponseDto dto = FoodResponseDto.builder()
                .id(foodId)
                .moderationStatus(ModerationStatus.APPROVED.name())
                .verifiedByAdmin(true)
                .build();

        given(foodRepository.findByOriginalFoodIdAndUserId(foodId, userId))
                .willReturn(Optional.empty());
        given(foodRepository.findById(foodId)).willReturn(Optional.of(food));
        given(foodMapper.toDto(food)).willReturn(dto);

        // When
        FoodResponseDto result = foodService.updateFavorite(foodId, userId, true);

        // Then
        assertTrue(result.isFavorite());
        verify(userFoodFavoriteRepository).save(any(UserFoodFavorite.class));
    }

    @Test
    @DisplayName("When favorite is disabled, should delete user-specific favorite and return DTO")
    void updateFavorite_whenDisabled_shouldDeleteFavoriteAndReturnDto() {
        // Given
        Long userId = 1L;
        String foodId = "banana";
        Food food = Food.builder()
                .id(foodId)
                .userId(null)
                .moderationStatus(ModerationStatus.APPROVED)
                .verifiedByAdmin(true)
                .build();
        FoodResponseDto dto = FoodResponseDto.builder()
                .id(foodId)
                .moderationStatus(ModerationStatus.APPROVED.name())
                .verifiedByAdmin(true)
                .build();

        given(foodRepository.findByOriginalFoodIdAndUserId(foodId, userId))
                .willReturn(Optional.empty());
        given(foodRepository.findById(foodId)).willReturn(Optional.of(food));
        given(foodMapper.toDto(food)).willReturn(dto);

        // When
        FoodResponseDto result = foodService.updateFavorite(foodId, userId, false);

        // Then
        assertFalse(result.isFavorite());
        verify(userFoodFavoriteRepository).deleteByUserIdAndFoodId(userId, foodId);
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
        assertEquals("Apple", result.getFirst());
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

    @Test
    @DisplayName("When customize food, should create copy, apply patch, save and return DTO")
    void customizeAndSubmitForReview_shouldCreateCopyAndSave() {
        // Given
        String originalId = "orig123";
        Food original = new Food();
        original.setId(originalId);
        original.setUserId(99L);
        original.setModerationStatus(ModerationStatus.APPROVED);
        original.setVerifiedByAdmin(true);

        Food customized = new Food();
        Food saved = new Food();
        FoodResponseDto expectedDto = new FoodResponseDto();
        FoodPatchRequestDto patchDto = new FoodPatchRequestDto();

        Long userId = 1L;

        given(foodRepository.findById(originalId)).willReturn(Optional.of(original));
        given(foodMapper.createCustomizedCopy(original, userId)).willReturn(customized);
        doNothing().when(foodMapper).updateFoodFromPatchDto(any(), any());
        given(foodCodeGenerator.resolveCode(any())).willReturn("newCode123");
        given(foodRepository.save(customized)).willReturn(saved);
        given(foodMapper.toDto(saved)).willReturn(expectedDto);

        // When
        FoodResponseDto result = foodService
                .customizeAndSubmitForReview(originalId, patchDto, userId);

        // Then
        assertNotNull(result);
        assertEquals(expectedDto, result);
        assertEquals("newCode123", customized.getId());
        assertEquals("newCode123", customized.getCode());
        verify(foodRepository).save(customized);
    }

    @Test
    @DisplayName("When customize source is another user's private food, should not expose it")
    void customizeAndSubmitForReview_whenSourceIsPrivateForOtherUser_shouldThrowNotFound() {
        // Given
        String privateFoodId = "private-food";
        Food privateFood = Food.builder()
                .id(privateFoodId)
                .userId(2L)
                .moderationStatus(ModerationStatus.REJECTED)
                .verifiedByAdmin(false)
                .build();

        given(foodRepository.findById(privateFoodId)).willReturn(Optional.of(privateFood));

        // When & Then
        assertThrows(NotFoundException.class,
                () -> foodService.customizeAndSubmitForReview(
                        privateFoodId, new FoodPatchRequestDto(), 1L));
    }

    @Test
    @DisplayName("When approve moderation for a copy, should update original and DELETE copy")
    void approveModeration_whenCopy_shouldUpdateOriginalAndDeleteCopy() {
        // Given
        String pendingId = "pending123";
        String originalId = "orig123";

        Food pending = new Food();
        pending.setId(pendingId);
        pending.setOriginalFoodId(originalId);
        pending.setProductName("Updated Name");

        Food original = new Food();
        original.setId(originalId);
        original.setProductName("Old Name");

        FoodResponseDto expectedDto = new FoodResponseDto();

        given(foodRepository.findById(pendingId)).willReturn(Optional.of(pending));
        given(foodRepository.findById(originalId)).willReturn(Optional.of(original));
        given(foodRepository.save(original)).willReturn(original);
        given(foodMapper.toDto(original)).willReturn(expectedDto);
        doAnswer(invocation -> {
            Food source = invocation.getArgument(0);
            Food target = invocation.getArgument(1);
            target.setProductName(source.getProductName());
            return null;
        }).when(foodMapper).mergePendingIntoOriginal(pending, original);

        // When
        FoodResponseDto result = foodService.approveModeration(pendingId);

        // Then
        assertEquals(expectedDto, result);
        assertTrue(original.isVerifiedByAdmin());
        assertEquals("Updated Name", original.getProductName());
        verify(foodRepository, times(1)).save(original);
    }

    @Test
    @DisplayName("When approve moderation for a new food, should update status and save")
    void approveModeration_whenNewFood_shouldUpdateAndSave() {
        // Given
        String pendingId = "pending123";
        Food pending = new Food();
        pending.setId(pendingId);
        pending.setOriginalFoodId(null);

        Food saved = new Food();
        FoodResponseDto expectedDto = new FoodResponseDto();

        given(foodRepository.findById(pendingId)).willReturn(Optional.of(pending));
        given(foodRepository.save(pending)).willReturn(saved);
        given(foodMapper.toDto(saved)).willReturn(expectedDto);

        // When
        FoodResponseDto result = foodService.approveModeration(pendingId);

        // Then
        assertEquals(expectedDto, result);
        assertTrue(pending.isVerifiedByAdmin());
        verify(foodRepository, times(1)).save(pending);
    }

    @Test
    @DisplayName("When reject moderation, should update status to REJECTED")
    void rejectModeration_shouldUpdateStatus() {
        // Given
        String pendingId = "pending123";
        Food pending = new Food();
        pending.setId(pendingId);

        Food saved = new Food();
        FoodResponseDto expectedDto = new FoodResponseDto();

        given(foodRepository.findById(pendingId)).willReturn(Optional.of(pending));
        given(foodRepository.save(pending)).willReturn(saved);
        given(foodMapper.toDto(saved)).willReturn(expectedDto);

        // When
        FoodResponseDto result = foodService.rejectModeration(pendingId);

        // Then
        assertEquals(expectedDto, result);
        verify(foodRepository).save(pending);
    }

    @Test
    @DisplayName("When get pending review foods, should return mapped list")
    void getPendingReviewFoods_shouldReturnList() {
        // Given
        Food pendingFood = new Food();
        FoodResponseDto dto = new FoodResponseDto();

        given(foodRepository.findAllByModerationStatus(any(), any(Pageable.class)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendingFood)));
        given(foodMapper.toDto(pendingFood)).willReturn(dto);

        // When
        List<FoodResponseDto> result = foodService
                .getAllFoodsForAdmin(ModerationStatus.PENDING_REVIEW, 0, 10);

        // Then
        assertEquals(1, result.size());
        assertEquals(dto, result.getFirst());
    }
}
