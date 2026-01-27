package com.olehprukhnytskyi.macrotrackerfoodservice.mapper;

import com.olehprukhnytskyi.macrotrackerfoodservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPatchRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodRequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.util.UnitType;
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
            @Mapping(target = "userId", ignore = true)
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
            @Mapping(target = "userId", ignore = true)
    })
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFoodFromPatchDto(FoodPatchRequestDto dto, @MappingTarget Food entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateNutrimentsFromDto(NutrimentsDto dto, @MappingTarget Nutriments entity);

    @AfterMapping
    default void determineAvailableUnits(Food food,
                                         @MappingTarget
                                         FoodResponseDto.FoodResponseDtoBuilder builder) {
        Nutriments n = food.getNutriments();
        List<UnitType> units = new ArrayList<>();
        if (n != null) {
            if (isGramsDataComplete(n)) {
                units.add(UnitType.GRAMS);
            }
            if (isPiecesDataComplete(n)) {
                units.add(UnitType.PIECES);
            }
        }
        if (units.isEmpty()) {
            units.add(UnitType.GRAMS);
        }
        builder.availableUnits(units);
    }

    default boolean isGramsDataComplete(Nutriments n) {
        return n.getCalories() != null
               && n.getProtein() != null
               && n.getFat() != null
               && n.getCarbohydrates() != null;
    }

    default boolean isPiecesDataComplete(Nutriments n) {
        return n.getCaloriesPerPiece() != null
               && n.getProteinPerPiece() != null
               && n.getFatPerPiece() != null
               && n.getCarbohydratesPerPiece() != null;
    }
}
