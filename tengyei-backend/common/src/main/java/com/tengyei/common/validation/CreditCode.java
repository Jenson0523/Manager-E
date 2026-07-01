package com.tengyei.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 统一社会信用代码校验注解：18 位，由数字和大写字母（不含 I/O/S/V/Z）组成。
 * 空值放行（是否必填由 @NotBlank 决定）。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = CreditCodeValidator.class)
public @interface CreditCode {
    String message() default "统一社会信用代码格式不正确（应为18位）";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
