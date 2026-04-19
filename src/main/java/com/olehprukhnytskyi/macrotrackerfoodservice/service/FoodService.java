package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.ConflictException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.DBErrorCode;
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
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.ModerationStatus;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private final FoodMapper foodMapper;
    private final OutboxRepository outboxRepository;
    private final FoodAssetService foodAssetService;
    private final FoodSearchDao foodSearchDao;
    private final FoodCodeGenerator foodCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final RetryTemplate retryTemplate;

    @CachePut(value = CacheConstants.FOOD_DATA, key = "#result.id")
    public FoodResponseDto createFoodWithImages(FoodRequestDto dto,
                                                MultipartFile image, Long userId) {
        log.info("Creating new food item for userId={}", userId);
        try {
            Optional<Food> existingSameProduct = tryFindExistingSameProduct(dto);
            if (existingSameProduct.isPresent()) {
                log.info("Returning existing food id={}", existingSameProduct.get().getId());
                return foodMapper.toDto(existingSameProduct.get());
            }
            String tempImageKey = null;
            if (image != null && !image.isEmpty()) {
                tempImageKey = foodAssetService.uploadToTemp(image);
            }
            Food food = prepareNewFood(dto, userId);
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
            return foodMapper.toDto(savedFood);
        } catch (ConflictException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while saving food userId={}", userId, e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected error while saving food", e);
        }
    }

    public List<FoodResponseDto> findAllByUserId(Long userId, int offset, int limit) {
        if (offset % limit != 0) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Offset must be a multiple of limit");
        }
        Pageable pageable = PageRequest.of(offset / limit, limit);
        List<String> excludedIds = foodRepository.findOriginalIdsByUserId(userId).stream()
                .map(OriginalIdOnly::getOriginalFoodId)
                .toList();
        Page<Food> foodsPage;
        if (excludedIds.isEmpty()) {
            foodsPage = foodRepository.findAllByUserId(userId, pageable);
        } else {
            foodsPage = foodRepository.findAllByUserIdAndIdNotIn(userId, excludedIds, pageable);
        }
        return foodsPage.stream()
                .map(foodMapper::toDto)
                .toList();
    }

    @Cacheable(
            value = CacheConstants.SEARCH_RESULTS,
            key = "T(org.springframework.util.DigestUtils).md5DigestAsHex((#query"
                  + ".trim().toLowerCase() + '-' + #offset + '-' + #limit + '-'"
                  + " + (#userId != null ? #userId : 'anonymous')).getBytes())",
            unless = "#result == null || #result.items.isEmpty()"
    )
    public FoodListCacheWrapper findByQuery(String query, Long userId, int offset, int limit) {
        log.debug("Searching foods query='{}' offset={} limit={}", query, offset, limit);
        List<String> excludedIds = Collections.emptyList();
        if (userId != null) {
            excludedIds = foodRepository.findOriginalIdsByUserId(userId).stream()
                    .map(OriginalIdOnly::getOriginalFoodId)
                    .toList();
        }
        List<Food> foods = foodSearchDao.search(query, userId, excludedIds, offset, limit);
        return new FoodListCacheWrapper(foodMapper.toDto(foods));
    }

    @Cacheable(value = CacheConstants.FOOD_DATA, key = "#id")
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

    @CachePut(value = CacheConstants.FOOD_DATA, key = "#id")
    public FoodResponseDto patch(String id, FoodPatchRequestDto dto, Long userId) {
        log.info("Updating food id={}", id);
        try {
            Food existing = foodRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                            "Food not found with id: " + id));
            foodMapper.updateFoodFromPatchDto(dto, existing);
            Food saved = foodRepository.save(existing);
            return foodMapper.toDto(saved);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(DBErrorCode.DB_DUPLICATE_KEY,
                    "The data has been changed by another user."
                    + " Please refresh the page and try again");
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected error while patching food", e);
        }
    }

    @CacheEvict(value = CacheConstants.FOOD_DATA, key = "#id")
    @Transactional
    public void deleteByIdAndUserId(String id, Long userId) {
        log.info("Deleting food id={} userId={}", id, userId);
        foodRepository.deleteByIdAndUserId(id, userId);
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
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("FOOD")
                .aggregateId(id)
                .eventType("FOOD_DELETED")
                .build());
        log.debug("Food deleted successfully id={}", id);
    }

    public List<FoodResponseDto> findAllByIds(List<String> foodIds) {
        return foodRepository.findAllById(foodIds).stream()
                .map(foodMapper::toDto)
                .toList();
    }

    @Transactional
    public FoodResponseDto customizeAndSubmitForReview(String originalId,
                                                       FoodPatchRequestDto patchDto, Long userId) {
        log.info("User {} is customizing food id={}", userId, originalId);
        Optional<Food> existingPendingCopy = foodRepository
                .findByOriginalFoodIdAndUserIdAndModerationStatus(
                        originalId, userId, ModerationStatus.PENDING_REVIEW);
        Food foodToProcess;
        if (existingPendingCopy.isPresent()) {
            log.info("Found existing pending copy id={} for original id={}. Updating it.",
                    existingPendingCopy.get().getId(), originalId);
            foodToProcess = existingPendingCopy.get();
            foodMapper.updateFoodFromPatchDto(patchDto, foodToProcess);
        } else {
            log.info("No pending copy found. Creating a new one for original id={}", originalId);
            Food original = foodRepository.findById(originalId)
                    .orElseThrow(() -> new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                            "Food not found with id: " + originalId));
            foodToProcess = foodMapper.createCustomizedCopy(original, userId);
            foodMapper.updateFoodFromPatchDto(patchDto, foodToProcess);
            String newCode = foodCodeGenerator.resolveCode(new FoodRequestDto());
            foodToProcess.setId(newCode);
            foodToProcess.setCode(newCode);
            foodToProcess.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        }
        return foodMapper.toDto(foodRepository.save(foodToProcess));
    }

    @Transactional
    @CacheEvict(value = CacheConstants.FOOD_DATA, key = "#p0", condition = "#result != null")
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
            return foodMapper.toDto(original);
        } else {
            pendingFood.setVerifiedByAdmin(true);
            pendingFood.setModerationStatus(ModerationStatus.APPROVED);
            return foodMapper.toDto(foodRepository.save(pendingFood));
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

    private Food prepareNewFood(FoodRequestDto request, Long userId) {
        Food food = foodMapper.toModel(request);
        String code = foodCodeGenerator.resolveCode(request);
        food.setUserId(userId);
        food.setId(code);
        food.setCode(code);
        food.setModerationStatus(ModerationStatus.PENDING_REVIEW);
        food.setVerifiedByAdmin(false);
        return food;
    }

    private boolean isSameProduct(Food existingFood, FoodRequestDto newRequest) {
        return existingFood.getProductName().equals(newRequest.getProductName())
                && existingFood.getBrands().equals(newRequest.getBrands())
                && existingFood.getGenericName().equals(newRequest.getGenericName())
                && existingFood.getNutriments().equals(
                        nutrimentsMapper.toModel(newRequest.getNutriments()));
    }

    private Optional<Food> tryFindExistingSameProduct(FoodRequestDto dto) {
        if (dto.getCode() != null) {
            Optional<Food> existing = foodRepository.findById(dto.getCode());
            if (existing.isPresent()) {
                if (isSameProduct(existing.get(), dto)) {
                    log.debug("Duplicate food detected with same data, "
                            + "returning existing id={}", existing.get().getId());
                    return existing;
                }
                throw new ConflictException(FoodErrorCode.FOOD_ALREADY_EXISTS,
                        "Food with this code already exists with different data");
            }
        }
        return Optional.empty();
    }
}
