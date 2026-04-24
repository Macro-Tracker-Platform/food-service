package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food product response")
public class FoodResponseDto {
    @Schema(description = "Unique identifier", example = "5901234123457")
    private String id;

    @Schema(description = "Product barcode", example = "5901234123457")
    private String code;

    @Schema(description = "ID of the user who created the product", example = "12345")
    private Long userId;

    @Schema(description = "Product name", example = "Organic Chicken Breast")
    private String productName;

    @Schema(description = "Generic product name", example = "Poultry")
    private String genericName;

    @Schema(description = "Product image URL", example = "https://example.com/images/chicken.jpg")
    private String imageUrl;

    @Schema(description = "Product brands", example = "Organic Farms Inc.")
    private String brands;

    @Schema(description = "Nutrition information")
    private NutrimentsDto nutriments;

    @Schema(description = "List of supported measurement units",
            example = "[\"GRAMS\", \"PIECES\"]")
    @Builder.Default
    private List<UnitType> availableUnits = new ArrayList<>();

    @Schema(
            description = "ID of the original food item if this is a customized copy",
            example = "5901234123457")
    private String originalFoodId;

    @Schema(description = "Current moderation status of the product",
            example = "PENDING_REVIEW")
    private String moderationStatus;

    @Schema(description = "Indicates whether the product data has been verified"
                          + " by an administrator")
    private boolean verifiedByAdmin;
}
