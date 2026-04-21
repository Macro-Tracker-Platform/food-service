package com.olehprukhnytskyi.macrotrackerfoodservice.mapper;

import com.olehprukhnytskyi.macrotrackerfoodservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsPatchDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = MapperConfig.class, uses = {NutrimentsMapper.class})
public interface FoodMapper {
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "keywords", ignore = true),
            @Mapping(target = "imageUrl", ignore = true),
            @Mapping(target = "userId", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "originalFoodId", ignore = true),
            @Mapping(target = "moderationStatus", expression =
                    "java(com.olehprukhnytskyi.util.ModerationStatus.PENDING_REVIEW)"),
            @Mapping(target = "verifiedByAdmin", constant = "false")
    })
    Food toModel(FoodRequestDto requestDto);

    @Mapping(target = "availableUnits", ignore = true)
    FoodResponseDto toDto(Food food);

    List<FoodResponseDto> toDto(List<Food> foods);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "keywords", ignore = true),
            @Mapping(target = "code", ignore = true),
            @Mapping(target = "imageUrl", ignore = true),
            @Mapping(target = "userId", ignore = true),
            @Mapping(target = "nutriments.availableUnits", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "originalFoodId", ignore = true),
            @Mapping(target = "moderationStatus", ignore = true),
            @Mapping(target = "verifiedByAdmin", ignore = true)
    })
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFoodFromPatchDto(FoodPatchRequestDto dto, @MappingTarget Food entity);

    @Mapping(target = "availableUnits", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void updateNutrimentsFromPatchDto(NutrimentsPatchDto dto, @MappingTarget Nutriments entity);

    FoodPatchRequestDto toPatchDto(FoodRequestDto requestDto);

    @Mapping(target = "availableUnits", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateNutrimentsFromDto(NutrimentsDto dto, @MappingTarget Nutriments entity);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "code", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "userId", ignore = true),
            @Mapping(target = "originalFoodId", ignore = true),
            @Mapping(target = "moderationStatus", ignore = true),
            @Mapping(target = "verifiedByAdmin", ignore = true)
    })
    void mergePendingIntoOriginal(Food pendingFood, @MappingTarget Food originalFood);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "code", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "originalFoodId", source = "original.id"),
            @Mapping(target = "userId", source = "userId"),
            @Mapping(target = "moderationStatus", expression =
                    "java(com.olehprukhnytskyi.util.ModerationStatus.PENDING_REVIEW)"),
            @Mapping(target = "verifiedByAdmin", constant = "false")
    })
    Food createCustomizedCopy(Food original, Long userId);

    @AfterMapping
    default void determineAvailableUnits(Food food,
                                         @MappingTarget
                                         FoodResponseDto.FoodResponseDtoBuilder builder) {
        if (food.getNutriments() != null) {
            builder.availableUnits(new ArrayList<>(food.getNutriments().getAvailableUnits()));
        }
    }
}
