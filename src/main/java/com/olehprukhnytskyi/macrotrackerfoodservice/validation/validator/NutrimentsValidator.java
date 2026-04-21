package com.olehprukhnytskyi.macrotrackerfoodservice.validation.validator;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsPatchDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.validation.ValidUnitGroups;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Stream;

public class NutrimentsValidator implements
        ConstraintValidator<ValidUnitGroups, Object> {
    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        BigDecimal cal100;
        BigDecimal carb100;
        BigDecimal fat100;
        BigDecimal pro100;
        BigDecimal calP;
        BigDecimal carbP;
        BigDecimal fatP;
        BigDecimal proP;

        if (dto instanceof NutrimentsDto n) {
            cal100 = n.getCaloriesPer100();
            carb100 = n.getCarbohydratesPer100();
            fat100 = n.getFatPer100();
            pro100 = n.getProteinPer100();
            calP = n.getCaloriesPerPiece();
            carbP = n.getCarbohydratesPerPiece();
            fatP = n.getFatPerPiece();
            proP = n.getProteinPerPiece();
        } else if (dto instanceof NutrimentsPatchDto n) {
            cal100 = n.getCaloriesPer100();
            carb100 = n.getCarbohydratesPer100();
            fat100 = n.getFatPer100();
            pro100 = n.getProteinPer100();
            calP = n.getCaloriesPerPiece();
            carbP = n.getCarbohydratesPerPiece();
            fatP = n.getFatPerPiece();
            proP = n.getProteinPerPiece();
        } else {
            return true;
        }

        boolean hasAllGrams = allNotNull(cal100, fat100, pro100, carb100);
        boolean hasNoGrams = allNull(cal100, fat100, pro100, carb100);
        if (!hasAllGrams && !hasNoGrams) {
            addConstraintViolation(context,
                    "Nutrition data per 100g must be either fully completed (4 fields) or empty");
            return false;
        }

        boolean hasAllPieces = allNotNull(calP, fatP, proP, carbP);
        boolean hasNoPieces = allNull(calP, fatP, proP, carbP);
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
