package com.tengyei.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 统一社会信用代码校验实现：18 位，字符集为数字 + 大写字母（不含 I、O、S、V、Z）。
 */
public class CreditCodeValidator implements ConstraintValidator<CreditCode, String> {

    private static final String PATTERN = "^[0-9A-HJ-NP-RT-UW-Y]{2}[0-9]{6}[0-9A-HJ-NP-RT-UW-Y]{10}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return value.matches(PATTERN);
    }
}
