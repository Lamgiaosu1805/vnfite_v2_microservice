package com.p2plending.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class VietnamesePhoneValidator implements ConstraintValidator<VietnamesePhone, String> {
    private static final Pattern VIETNAMESE_PHONE =
            Pattern.compile("^(?:\\+84|84|0)(?:3|5|7|8|9)\\d{8}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || VIETNAMESE_PHONE.matcher(value.trim()).matches();
    }
}
