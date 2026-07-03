package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food product creation request")
public class FoodRequestDto {
    @Schema(
            description = "Product barcode (8-24 digits)",
            example = "5901234123457",
            minLength = 8,
            maxLength = 24
    )
    @Size(min = 8, max = 24, message = "The barcode length must be between 8 and 24 characters")
    @Pattern(regexp = "\\d+", message = "The barcode must contain digits only")
    private String code;

    @Schema(
            description = "Product name",
            example = "Organic Chicken Breast",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 100
    )
    @NotNull
    @Size(max = 100)
    private String productName;

    @Schema(description = "Generic product name", example = "Poultry", maxLength = 100)
    @Size(max = 100)
    private String genericName;

    @Schema(description = "Product brands", example = "Organic Farms Inc.", maxLength = 100)
    @Size(max = 100)
    private String brands;

    @Schema(description = "Nutrition information", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull
    private NutrimentsDto nutriments;

    @Schema(description = "Whether the product should be submitted for public publication review."
            + " Works for products without a real barcode; an internal code will be generated.",
            example = "true",
            defaultValue = "true")
    @JsonProperty("isPublic")
    @JsonAlias({"public", "is_public", "publishPublicly"})
    @Builder.Default
    private boolean isPublic = true;
}
