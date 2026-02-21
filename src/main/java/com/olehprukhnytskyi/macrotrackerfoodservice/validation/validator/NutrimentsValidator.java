package com.olehprukhnytskyi.macrotrackerfoodservice.validation.validator;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.validation.ValidUnitGroups;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;
import java.util.stream.Stream;

public class NutrimentsValidator implements
        ConstraintValidator<ValidUnitGroups, NutrimentsDto> {
    @Override
    public boolean isValid(NutrimentsDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }
        boolean hasAllGrams = allNotNull(dto.getCaloriesPer100(), dto.getFatPer100(),
                dto.getProteinPer100(), dto.getCarbohydratesPer100());
        boolean hasNoGrams = allNull(dto.getCaloriesPer100(), dto.getFatPer100(),
                dto.getProteinPer100(), dto.getCarbohydratesPer100());
        if (!hasAllGrams && !hasNoGrams) {
            addConstraintViolation(context,
                    "Nutrition data per 100g must be either fully completed (4 fields) or empty");
            return false;
        }

        boolean hasAllPieces = allNotNull(dto.getCaloriesPerPiece(), dto.getFatPerPiece(),
                dto.getProteinPerPiece(), dto.getCarbohydratesPerPiece());
        boolean hasNoPieces = allNull(dto.getCaloriesPerPiece(), dto.getFatPerPiece(),
                dto.getProteinPerPiece(), dto.getCarbohydratesPerPiece());
        if (!hasAllPieces && !hasNoPieces) {
            addConstraintViolation(context,
                    "Nutrition data per piece must be either fully completed (4 fields) or empty");
            return false;
        }

        boolean isValid = hasAllGrams || hasAllPieces;
        if (!isValid) {
            addConstraintViolation(context,
                    "At least one full unit type (per 100g or per piece) must be provided");
        }
        return isValid;
    }

    private boolean allNotNull(Object... objects) {
        return Stream.of(objects).allMatch(Objects::nonNull);
    }

    private boolean allNull(Object... objects) {
        return Stream.of(objects).allMatch(Objects::isNull);
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
