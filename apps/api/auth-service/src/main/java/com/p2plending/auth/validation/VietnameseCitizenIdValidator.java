package com.p2plending.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class VietnameseCitizenIdValidator implements ConstraintValidator<VietnameseCitizenId, String> {
    private static final Pattern VIETNAMESE_CITIZEN_ID = Pattern.compile("^\\d{12}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || VIETNAMESE_CITIZEN_ID.matcher(value.trim()).matches();
    }
}
