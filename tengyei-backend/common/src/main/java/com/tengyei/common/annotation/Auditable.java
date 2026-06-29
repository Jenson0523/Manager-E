package com.tengyei.common.annotation;

import java.lang.annotation.*;

/**
 * Marks a controller method for automatic audit log writing.
 * The AuditAspect in the app module intercepts annotated methods and records
 * the operation in the audit_log table after execution.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    /** The business module name, e.g. "企业管理" */
    String module();
    /** The action type, e.g. "CREATE", "UPDATE", "DELETE" */
    String actionType();
    /** Human-readable description of the operation */
    String description();
}
