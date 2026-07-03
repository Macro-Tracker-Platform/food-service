package com.olehprukhnytskyi.macrotrackerfoodservice.controller;

import com.olehprukhnytskyi.annotation.RequireRole;
import com.olehprukhnytskyi.dto.PagedResponse;
import com.olehprukhnytskyi.dto.Pagination;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.FoodService;
import com.olehprukhnytskyi.util.ModerationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/foods/admin")
@RequireRole("ADMIN")
@Tag(
        name = "Admin Food Moderation API",
        description = "Endpoints for platform administrators"
)
public class AdminFoodController {
    private final FoodService foodService;

    @Operation(
            summary = "Publish food publicly (Admin only)",
            description = """
                    Approve user's food for public access without marking it as
                    fully administrator-verified"""
    )
    @RequireRole("ADMIN")
    @PostMapping("/{customizedId}/approve")
    public ResponseEntity<FoodResponseDto> approveFoodChanges(
            @PathVariable String customizedId) {
        log.info("Admin approved changes for customized food id={}", customizedId);
        FoodResponseDto updatedOriginal = foodService.approveModeration(customizedId);
        return ResponseEntity.ok(updatedOriginal);
    }

    @Operation(
            summary = "Verify food data (Admin only)",
            description = """
                    Fully verify food data, publish it publicly and mark it with
                    the administrator-verified flag"""
    )
    @RequireRole("ADMIN")
    @PostMapping("/{customizedId}/verify")
    public ResponseEntity<FoodResponseDto> verifyFoodChanges(
            @PathVariable String customizedId) {
        log.info("Admin verified changes for customized food id={}", customizedId);
        FoodResponseDto verifiedFood = foodService.verifyModeration(customizedId);
        return ResponseEntity.ok(verifiedFood);
    }

    @Operation(
            summary = "Reject customized food (Admin only)",
            description = "Reject user's custom food changes"
    )
    @RequireRole("ADMIN")
    @PostMapping("/{customizedId}/reject")
    public ResponseEntity<FoodResponseDto> rejectFoodChanges(
            @PathVariable String customizedId) {
        log.info("Admin rejected changes for customized food id={}", customizedId);
        FoodResponseDto rejectedCopy = foodService.rejectModeration(customizedId);
        return ResponseEntity.ok(rejectedCopy);
    }

    @Operation(
            summary = "Get all foods (Admin only)",
            description = "Retrieve all food products for admin audit or export"
    )
    @RequireRole("ADMIN")
    @GetMapping("/all")
    public ResponseEntity<PagedResponse<FoodResponseDto>> getAllFoodsForAdmin(
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "25") @Min(1) int limit) {
        log.info("Admin is requesting list of all foods");
        List<FoodResponseDto> pendingFoods = foodService.getAllFoodsForAdmin(status, offset, limit);
        Pagination pagination = new Pagination(offset, limit, pendingFoods.size());
        return ResponseEntity
                .status(pendingFoods.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .body(new PagedResponse<>(pendingFoods, pagination));
    }

    @Operation(
            summary = "Force delete food product (Admin only)",
            description = "Delete any food product regardless of the creator"
    )
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> forceDeleteFood(@PathVariable String id) {
        log.info("Admin forcefully deleting food id={}", id);
        foodService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
