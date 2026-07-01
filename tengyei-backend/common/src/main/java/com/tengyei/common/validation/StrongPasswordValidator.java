package com.tengyei.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link StrongPassword} 的校验实现，委托给 {@link PasswordRule}。
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 空值放行，是否必填交由 @NotBlank 负责；仅在有值时校验强度
        if (value == null || value.isEmpty()) {
            return true;
        }
        return PasswordRule.isValid(value);
    }
}
