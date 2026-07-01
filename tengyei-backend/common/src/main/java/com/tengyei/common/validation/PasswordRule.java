package com.tengyei.common.validation;

/**
 * 密码强度规则的统一定义与工具方法，供注解校验与 Service 层兜底校验复用。
 */
public final class PasswordRule {

    /** 8-20 位，且同时包含大写字母、小写字母和数字 */
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,20}$";

    public static final String MESSAGE = "密码须为 8-20 位，且包含大小写字母和数字";

    private PasswordRule() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(PATTERN);
    }
}
