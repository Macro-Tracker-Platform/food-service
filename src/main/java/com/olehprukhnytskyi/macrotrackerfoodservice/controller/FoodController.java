package com.olehprukhnytskyi.macrotrackerfoodservice.controller;

import com.olehprukhnytskyi.annotation.Idempotent;
import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.dto.Pagination;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodFavoriteRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.NutritionLabelScanService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/foods")
@Tag(
        name = "Food Products API",
        description = "Manage and search food products with nutrition information"
)
public class FoodController {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";
    private final FoodService foodService;
    private final NutritionLabelScanService nutritionLabelScanService;

    @Operation(
            summary = "Get food by ID",
            description = "Retrieve food product details by its unique identifier"
    )
    @GetMapping("/{id}")
    public ResponseEntity<FoodResponseDto> findById(
            @RequestHeader(value = CustomHeaders.X_USER_ID) Long userId,
            @PathVariable String id) {
        log.info("Fetching food by id={}", id);
        FoodResponseDto food = foodService.findPersonalizedById(id, userId);
        log.debug("Food retrieved successfully for id={}", id);
        return ResponseEntity.ok(food);
    }

    @Operation(
            summary = "Get user's food products",
            description = """
            Retrieve a paginated list of food products created by the current user.
            Returns an empty list if no products are found.
            """
    )
    @GetMapping("/my-foods")
    public ResponseEntity<PagedResponse<FoodResponseDto>> getUserFoods(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "25") @Min(1) int limit) {
        log.info("Fetching foods for userId={} offset={} limit={}", userId, offset, limit);
        List<FoodResponseDto> foods = foodService.findAllByUserId(userId, offset, limit);
        Pagination pagination = new Pagination(offset, limit, foods.size());
        log.debug("Retrieved {} foods for userId={}", foods.size(), userId);
        return ResponseEntity
                .status(foods.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .body(new PagedResponse<>(foods, pagination));
    }

    @Operation(
            summary = "Get batch food details",
            description = """
            Retrieve details for multiple food products by their IDs.
            Useful for populating lists or meal templates.
            Ids that are not found in the database will be skipped in the response.
            """
    )
    @PostMapping("/batch")
    public ResponseEntity<List<FoodResponseDto>> getFoodsDetails(
            @RequestHeader(value = CustomHeaders.X_USER_ID) Long userId,
            @RequestBody
            @Size(max = 100, message = "Batch size cannot exceed 100 items")
            List<String> foodIds) {
        log.info("Fetching batch details for {} food items", foodIds.size());
        List<FoodResponseDto> result = foodService.findAllByIds(foodIds, userId);
        log.debug("Batch retrieval completed. Found {} items out of requested {}",
                result.size(), foodIds.size());
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Update food favorite state",
            description = "Mark or unmark a food product as favorite for the current user"
    )
    @PatchMapping("/{id}/favorite")
    public ResponseEntity<FoodResponseDto> updateFavorite(
            @RequestHeader(value = CustomHeaders.X_USER_ID) Long userId,
            @PathVariable String id,
            @RequestBody @Valid FoodFavoriteRequestDto requestDto) {
        log.info("Updating favorite for food id={} userId={}", id, userId);
        FoodResponseDto food = foodService.updateFavorite(
                id, userId, requestDto.getFavorite());
        return ResponseEntity.ok(food);
    }

    @Operation(
            summary = "Search foods",
            description = "Search food products by name, brand or description with pagination"
    )
    @GetMapping
    public ResponseEntity<PagedResponse<FoodResponseDto>> findByQuery(
            @RequestParam String query,
            @RequestHeader(value = CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "25") @Min(1) int limit) {
        log.info("Searching foods query='{}' offset={} limit={}", query, offset, limit);
        List<FoodResponseDto> foods = foodService
                .findByQuery(query, userId, offset, limit).getItems();
        Pagination pagination = new Pagination(offset, limit, foods.size());
        return ResponseEntity
                .status(foods.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .body(new PagedResponse<>(foods, pagination));
    }

    @Operation(
            summary = "Get search suggestions",
            description = "Get autocomplete suggestions for food search"
    )
    @GetMapping("/search-suggestions")
    public ResponseEntity<List<String>> getSearchSuggestions(
            @RequestParam String query) {
        log.debug("Fetching search suggestions for query='{}'", query);
        List<String> suggestions = foodService.getSearchSuggestions(query);
        return suggestions.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(suggestions);
    }

    @Operation(
            summary = "Create food product",
            description = "Add new food product to database with optional image upload"
    )
    @Idempotent
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoodResponseDto> save(
            @RequestPart("food") @Valid FoodRequestDto requestDto,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Creating food product for userId={}", userId);
        FoodResponseDto saved = foodService.createFoodWithImages(requestDto, image, userId);
        log.info("Food created successfully for userId={} code={}", userId, saved.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(saved);
    }

    @Operation(
            summary = "Scan nutrition label",
            description = "Extract macro nutrients per 100g from a nutrition label image"
    )
    @PostMapping(
            value = "/nutrition-label-scan",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<NutritionLabelScanResponseDto> scanNutritionLabel(
            @RequestPart("image") MultipartFile image,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode) {
        log.info("Scanning nutrition label for userId={}", userId);
        return ResponseEntity.ok(nutritionLabelScanService.scan(userId, appVersionCode, image));
    }

    @Operation(
            summary = "Delete food product",
            description = "Delete food product by ID (user can only delete their own products)"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFood(
            @PathVariable String id,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Deleting food id={} by userId={}", id, userId);
        foodService.deleteByIdAndUserId(id, userId);
        log.debug("Food deleted successfully id={} userId={}", id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Customize food",
            description = """
                    Create a custom copy of an existing\s
                    food product and submit for moderation"""
    )
    @PostMapping("/{id}/customize")
    public ResponseEntity<FoodResponseDto> customizeFood(
            @PathVariable String id,
            @RequestBody @Valid FoodPatchRequestDto dto,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Customizing food id={} by userId={}", id, userId);
        FoodResponseDto customFood = foodService.customizeAndSubmitForReview(id, dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(customFood);
    }
}
