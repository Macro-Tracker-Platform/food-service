package com.olehprukhnytskyi.macrotrackerfoodservice.validation;

import com.olehprukhnytskyi.macrotrackerfoodservice.validation.validator.NutrimentsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NutrimentsValidator.class)
public @interface ValidUnitGroups {
    String message() default "At least one nutrient must be filled";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
