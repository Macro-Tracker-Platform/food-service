package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchDao;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodListCacheWrapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.OriginalIdOnly;
import com.olehprukhnytskyi.macrotrackerfoodservice.event.FoodCreatedEvent;
import com.olehprukhnytskyi.macrotrackerfoodservice.mapper.FoodMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.UserFoodFavorite;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.UserFoodFavoriteRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.ModerationStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {
    private final NutrimentsMapper nutrimentsMapper;
    private final FoodRepository foodRepository;
    private final UserFoodFavoriteRepository userFoodFavoriteRepository;
    private final FoodMapper foodMapper;
    private final OutboxRepository outboxRepository;
    private final FoodAssetService foodAssetService;
    private final FoodSearchDao foodSearchDao;
    private final FoodCodeGenerator foodCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final RetryTemplate retryTemplate;
    private final CacheManager cacheManager;
    private FoodService self;

    @Autowired
    public void setSelf(@Lazy FoodService self) {
        this.self = self;
    }

    @Transactional
    public FoodResponseDto createFoodWithImages(FoodRequestDto dto,
                                                MultipartFile image, Long userId) {
        log.info("Creating new food item for userId={}", userId);
        try {
            boolean forceInternalCode = false;
            if (dto.getCode() != null) {
                Optional<Food> existing = foodRepository.findById(dto.getCode());
                if (existing.isPresent()) {
                    Food existingFood = existing.get();
                    if (!canAccessFood(existingFood, userId)) {
                        log.info("Food with code={} exists but is not visible for userId={}. "
                                 + "Creating a separate private item.",
                                dto.getCode(), userId);
                        forceInternalCode = true;
                    } else if (isSameProduct(existingFood, dto)) {
                        log.info("Returning existing food id={}", existingFood.getId());
                        return withFavorite(foodMapper.toDto(existingFood), userId);
                    } else {
                        log.info("Food exists with different data. "
                                 + "Redirecting to customize flow for userId={}", userId);
                        FoodPatchRequestDto patchDto = foodMapper.toPatchDto(dto);
                        FoodResponseDto customizedResponse = customizeAndSubmitForReview(
                                existingFood.getId(), patchDto, userId);
                        if (image != null && !image.isEmpty()) {
                            String tempImageKey = foodAssetService.uploadToTemp(image);
                            try {
                                String finalUrl = foodAssetService
                                        .confirmImage(tempImageKey, customizedResponse.getId());
                                Food customizedFood = foodRepository
                                        .findById(customizedResponse.getId()).orElseThrow();
                                customizedFood.setImageUrl(finalUrl);
                                foodRepository.save(customizedFood);
                                customizedResponse.setImageUrl(finalUrl);
                            } catch (Exception e) {
                                log.error("Failed to confirm image for customized foodId={}",
                                        customizedResponse.getId(), e);
                            }
                        }
                        return withFavorite(customizedResponse, userId);
                    }
                }
            }

            String tempImageKey = null;
            if (image != null && !image.isEmpty()) {
                tempImageKey = foodAssetService.uploadToTemp(image);
            }
            Food food = prepareNewFood(dto, userId, forceInternalCode);
            Food savedFood = retryTemplate.execute(context -> foodRepository.save(food));
            if (tempImageKey != null) {
                try {
                    String finalUrl = foodAssetService
                            .confirmImage(tempImageKey, savedFood.getId());
                    savedFood.setImageUrl(finalUrl);
                    savedFood = foodRepository.save(savedFood);
                } catch (Exception e) {
                    log.error("Failed to confirm image for foodId={}. Image left in temp.",
                            savedFood.getId(), e);
                }
            }
            eventPublisher.publishEvent(new FoodCreatedEvent(savedFood.getId(), userId));
            log.info("Food created successfully userId={} foodId={}", userId, savedFood.getId());
            return withFavorite(foodMapper.toDto(savedFood), userId);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while saving food userId={}", userId, e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected error while saving food", e);
        }
    }

    public FoodResponseDto findPersonalizedById(String barcodeOrId, Long userId) {
        if (userId != null) {
            Optional<Food> customCopy = foodRepository
                    .findByOriginalFoodIdAndUserId(barcodeOrId, userId);
            if (customCopy.isPresent()) {
                return withFavorite(findByIdUsingProxy(customCopy.get().getId()), userId);
            }
        }
        FoodResponseDto food = findByIdUsingProxy(barcodeOrId);
        if (!canAccessFood(food, userId)) {
            throw new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                    "Food not found with id: " + barcodeOrId);
        }
        return withFavorite(food, userId);
    }

    public List<FoodResponseDto> findAllByUserId(Long userId, int offset, int limit) {
        if (offset % limit != 0) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Offset must be a multiple of limit");
        }
        Pageable pageable = PageRequest.of(offset / limit, limit);
        List<OriginalIdOnly> originalIds = Optional
                .ofNullable(foodRepository.findOriginalIdsByUserId(userId))
                .orElse(Collections.emptyList());
        List<String> excludedIds = originalIds.stream()
                .map(OriginalIdOnly::getOriginalFoodId)
                .toList();
        Page<Food> foodsPage;
        if (excludedIds.isEmpty()) {
            foodsPage = foodRepository.findAllByUserId(userId, pageable);
        } else {
            foodsPage = foodRepository.findAllByUserIdAndIdNotIn(userId, excludedIds, pageable);
        }
        List<FoodResponseDto> foods = foodsPage.stream()
                .map(foodMapper::toDto)
                .toList();
        return attachFavorites(foods, userId);
    }

    public FoodListCacheWrapper findByQuery(String query, Long userId, int offset, int limit) {
        FoodListCacheWrapper searchResults = self.findByQueryCached(query, userId, offset, limit);
        List<FoodResponseDto> items = searchResults == null
                ? Collections.emptyList()
                : searchResults.getItems();
        return new FoodListCacheWrapper(attachFavorites(items, userId));
    }

    @Cacheable(
            value = CacheConstants.SEARCH_RESULTS,
            key = "T(org.springframework.util.DigestUtils).md5DigestAsHex((#query"
                  + ".trim().toLowerCase() + '-' + #offset + '-' + #limit + '-'"
                  + " + (#userId != null ? #userId : 'anonymous')).getBytes())",
            unless = "#result == null || #result.items.isEmpty()"
    )
    public FoodListCacheWrapper findByQueryCached(String query, Long userId,
                                                  int offset, int limit) {
        log.debug("Searching foods query='{}' offset={} limit={}", query, offset, limit);
        List<String> excludedIds = Collections.emptyList();
        if (userId != null) {
            List<OriginalIdOnly> originalIds = Optional
                    .ofNullable(foodRepository.findOriginalIdsByUserId(userId))
                    .orElse(Collections.emptyList());
            excludedIds = originalIds.stream()
                    .map(OriginalIdOnly::getOriginalFoodId)
                    .toList();
        }
        List<Food> foods = foodSearchDao.search(query, userId, excludedIds, offset, limit);
        return new FoodListCacheWrapper(foodMapper.toDto(foods));
    }

    @Cacheable(
            value = CacheConstants.FOOD_DATA,
            key = "#id",
            unless = "#result == null || !#result.verifiedByAdmin"
    )
    public FoodResponseDto findById(String id) {
        log.debug("Fetching food by id={}", id);
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id: " + id));
        return foodMapper.toDto(food);
    }

    @Cacheable(
            value = CacheConstants.SEARCH_SUGGESTIONS,
            key = "T(org.springframework.util.DigestUtils)"
                    + ".md5DigestAsHex(#query.trim().toLowerCase().getBytes())",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<String> getSearchSuggestions(String query) {
        log.trace("Fetching search suggestions query='{}'", query);
        return foodSearchDao.getSuggestions(query);
    }

    @CacheEvict(value = CacheConstants.FOOD_DATA, key = "#id")
    @Transactional
    public void deleteByIdAndUserId(String id, Long userId) {
        log.info("Deleting food id={} userId={}", id, userId);
        foodRepository.deleteByIdAndUserId(id, userId);
        userFoodFavoriteRepository.deleteByFoodId(id);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("FOOD")
                .aggregateId(id)
                .eventType("FOOD_DELETED")
                .build());
        log.debug("Food deleted successfully id={} userId={}", id, userId);
    }

    @CacheEvict(value = CacheConstants.FOOD_DATA, key = "#id")
    @Transactional
    public void deleteById(String id) {
        log.info("Deleting food id={}", id);
        foodRepository.deleteById(id);
        userFoodFavoriteRepository.deleteByFoodId(id);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("FOOD")
                .aggregateId(id)
                .eventType("FOOD_DELETED")
                .build());
        log.debug("Food deleted successfully id={}", id);
    }

    public List<FoodResponseDto> findAllByIds(List<String> foodIds, Long userId) {
        if (foodIds == null || foodIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> replacements = new HashMap<>();
        if (userId != null) {
            Optional.ofNullable(foodRepository.findByOriginalFoodIdInAndUserId(foodIds, userId))
                    .orElse(Collections.emptyList())
                    .forEach(copy -> replacements.put(copy.getOriginalFoodId(), copy.getId()));
        }
        List<String> finalIdsToFetch = foodIds.stream()
                .map(id -> replacements.getOrDefault(id, id))
                .distinct()
                .toList();
        List<Food> foods = foodRepository.findAllById(finalIdsToFetch);
        List<FoodResponseDto> response = foods.stream()
                .filter(food -> canAccessFood(food, userId))
                .map(foodMapper::toDto)
                .toList();
        return attachFavorites(response, userId);
    }

    @Transactional
    public FoodResponseDto updateFavorite(String id, Long userId, boolean favorite) {
        FoodResponseDto food = findPersonalizedById(id, userId);
        if (favorite) {
            if (!userFoodFavoriteRepository.existsByUserIdAndFoodId(userId, food.getId())) {
                userFoodFavoriteRepository.save(UserFoodFavorite.builder()
                        .userId(userId)
                        .foodId(food.getId())
                        .build());
            }
        } else {
            userFoodFavoriteRepository.deleteByUserIdAndFoodId(userId, food.getId());
        }
        food.setFavorite(favorite);
        return food;
    }

    @Transactional
    public FoodResponseDto customizeAndSubmitForReview(String id,
                                                       FoodPatchRequestDto patchDto, Long userId) {
        log.info("User {} is customizing food id={}", userId, id);
        Food sourceFood = foodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id: " + id));
        if (!canAccessFood(sourceFood, userId)) {
            throw new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                    "Food not found with id: " + id);
        }
        if (sourceFood.getOriginalFoodId() == null
                && Objects.equals(sourceFood.getUserId(), userId)
                && sourceFood.getModerationStatus() == ModerationStatus.PENDING_REVIEW) {
            log.info("Food id={} is already a pending original owned by user={}."
                     + " Updating directly.", id, userId);
            foodMapper.updateFoodFromPatchDto(patchDto, sourceFood);
            return withFavorite(foodMapper.toDto(foodRepository.save(sourceFood)), userId);
        }
        String originalId = sourceFood.getOriginalFoodId() != null
                ? sourceFood.getOriginalFoodId()
                : sourceFood.getId();
        Optional<Food> existingPendingCopy = foodRepository
                .findByOriginalFoodIdAndUserId(originalId, userId);
        Food foodToProcess;
        if (existingPendingCopy.isPresent()) {
            log.info("Found existing pending copy id={} for original id={}. Updating it.",
                    existingPendingCopy.get().getId(), originalId);
            foodToProcess = existingPendingCopy.get();
            foodMapper.updateFoodFromPatchDto(patchDto, foodToProcess);
            foodToProcess.setModerationStatus(ModerationStatus.PENDING_REVIEW);
            foodToProcess.setVerifiedByAdmin(false);
        } else {
            log.info("No pending copy found. Creating a new one for original id={}", originalId);
            foodToProcess = foodMapper.createCustomizedCopy(sourceFood, userId);
            foodMapper.updateFoodFromPatchDto(patchDto, foodToProcess);
            String newCode = foodCodeGenerator.resolveCode(new FoodRequestDto());
            foodToProcess.setId(newCode);
            foodToProcess.setCode(newCode);
            foodToProcess.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        }
        return withFavorite(foodMapper.toDto(foodRepository.save(foodToProcess)), userId);
    }

    @Transactional
    public FoodResponseDto approveModeration(String pendingFoodId) {
        log.info("Admin is approving food id={}", pendingFoodId);
        Food pendingFood = foodRepository.findById(pendingFoodId)
                .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id: " + pendingFoodId));
        if (pendingFood.getOriginalFoodId() != null) {
            Food original = foodRepository.findById(pendingFood.getOriginalFoodId())
                    .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                            "Original food not found"));
            foodMapper.mergePendingIntoOriginal(pendingFood, original);
            original.setVerifiedByAdmin(true);
            pendingFood.setModerationStatus(ModerationStatus.APPROVED);
            foodRepository.save(original);
            foodRepository.delete(pendingFood);
            evictFoodCache(pendingFoodId);
            evictFoodCache(original.getId());
            return foodMapper.toDto(original);
        } else {
            pendingFood.setVerifiedByAdmin(true);
            pendingFood.setModerationStatus(ModerationStatus.APPROVED);
            Food saved = foodRepository.save(pendingFood);
            evictFoodCache(saved.getId());
            return foodMapper.toDto(saved);
        }
    }

    @Transactional
    public FoodResponseDto rejectModeration(String pendingFoodId) {
        log.info("Admin is rejecting food id={}", pendingFoodId);
        Food pendingFood = foodRepository.findById(pendingFoodId)
                .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id: " + pendingFoodId));
        pendingFood.setModerationStatus(ModerationStatus.REJECTED);
        Food saved = foodRepository.save(pendingFood);
        return foodMapper.toDto(saved);
    }

    public List<FoodResponseDto> getAllFoodsForAdmin(ModerationStatus status,
                                                     int offset, int limit) {
        if (offset % limit != 0) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Offset must be a multiple of limit");
        }
        log.info("Fetching all foods for admin. Offset={}, Limit={}", offset, limit);
        Pageable pageable = PageRequest.of(offset / limit, limit);
        return foodRepository.findAllByModerationStatus(status, pageable)
                .stream()
                .map(foodMapper::toDto)
                .toList();
    }

    private Food prepareNewFood(FoodRequestDto request, Long userId, boolean forceInternalCode) {
        Food food = foodMapper.toModel(request);
        String code = forceInternalCode
                ? foodCodeGenerator.resolveCode(new FoodRequestDto())
                : foodCodeGenerator.resolveCode(request);
        food.setUserId(userId);
        food.setId(code);
        food.setCode(code);
        food.setModerationStatus(request.isPublic()
                ? ModerationStatus.PENDING_REVIEW
                : ModerationStatus.REJECTED);
        food.setVerifiedByAdmin(false);
        return food;
    }

    private boolean canAccessFood(Food food, Long userId) {
        return Objects.equals(food.getUserId(), userId)
                || food.getUserId() == null
                || food.isVerifiedByAdmin()
                || food.getModerationStatus() == ModerationStatus.APPROVED;
    }

    private boolean canAccessFood(FoodResponseDto food, Long userId) {
        return Objects.equals(food.getUserId(), userId)
                || food.getUserId() == null
                || food.isVerifiedByAdmin()
                || ModerationStatus.APPROVED.name().equals(food.getModerationStatus());
    }

    private boolean isSameProduct(Food existingFood, FoodRequestDto newRequest) {
        return existingFood.getProductName().equals(newRequest.getProductName())
                && existingFood.getBrands().equals(newRequest.getBrands())
                && existingFood.getGenericName().equals(newRequest.getGenericName())
                && existingFood.getNutriments().equals(
                        nutrimentsMapper.toModel(newRequest.getNutriments()));
    }

    private FoodResponseDto withFavorite(FoodResponseDto food, Long userId) {
        FoodResponseDto copy = copyFoodResponse(food);
        if (copy == null || userId == null || copy.getId() == null) {
            return copy;
        }
        copy.setFavorite(userFoodFavoriteRepository.existsByUserIdAndFoodId(
                userId, copy.getId()));
        return copy;
    }

    private FoodResponseDto findByIdUsingProxy(String id) {
        return self.findById(id);
    }

    private List<FoodResponseDto> attachFavorites(List<FoodResponseDto> foods, Long userId) {
        if (foods == null || foods.isEmpty()) {
            return Collections.emptyList();
        }
        List<FoodResponseDto> copies = foods.stream()
                .map(this::copyFoodResponse)
                .toList();
        if (userId == null) {
            return copies;
        }
        List<String> foodIds = copies.stream()
                .map(FoodResponseDto::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<String> favoriteFoodIds = loadFavoriteFoodIds(userId, foodIds);
        copies.forEach(food -> food.setFavorite(favoriteFoodIds.contains(food.getId())));
        return copies;
    }

    private Set<String> loadFavoriteFoodIds(Long userId, List<String> foodIds) {
        if (foodIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<UserFoodFavorite> favorites = userFoodFavoriteRepository
                .findAllByUserIdAndFoodIdIn(userId, new HashSet<>(foodIds));
        if (favorites == null) {
            return Collections.emptySet();
        }
        Set<String> favoriteFoodIds = new HashSet<>();
        favorites.forEach(favorite -> favoriteFoodIds.add(favorite.getFoodId()));
        return favoriteFoodIds;
    }

    private FoodResponseDto copyFoodResponse(FoodResponseDto food) {
        if (food == null) {
            return null;
        }
        return FoodResponseDto.builder()
                .id(food.getId())
                .code(food.getCode())
                .userId(food.getUserId())
                .productName(food.getProductName())
                .genericName(food.getGenericName())
                .imageUrl(food.getImageUrl())
                .brands(food.getBrands())
                .nutriments(food.getNutriments())
                .availableUnits(food.getAvailableUnits() == null
                        ? Collections.emptyList()
                        : new ArrayList<>(food.getAvailableUnits()))
                .originalFoodId(food.getOriginalFoodId())
                .moderationStatus(food.getModerationStatus())
                .verifiedByAdmin(food.isVerifiedByAdmin())
                .favorite(false)
                .build();
    }

    private void evictFoodCache(String foodId) {
        try {
            if (cacheManager == null) {
                return;
            }
            Cache cache = cacheManager.getCache(CacheConstants.FOOD_DATA);
            if (cache != null) {
                cache.evict(foodId);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache", e);
        }
    }
}
