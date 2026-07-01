package com.tengyei.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 强密码校验注解：要求 8-20 位，且同时包含大写字母、小写字母和数字。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "密码须为 8-20 位，且包含大小写字母和数字";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
