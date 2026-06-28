package com.tengyei.common.context;

public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> BRANCH_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DATA_SCOPE = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { TENANT_ID.set(tenantId); }
    public static Long getTenantId() { return TENANT_ID.get(); }

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }

    public static void setBranchId(Long branchId) { BRANCH_ID.set(branchId); }
    public static Long getBranchId() { return BRANCH_ID.get(); }

    public static void setDataScope(String scope) { DATA_SCOPE.set(scope); }
    public static String getDataScope() { return DATA_SCOPE.get(); }

    public static boolean isSuperAdmin() { return Long.valueOf(0L).equals(TENANT_ID.get()); }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        BRANCH_ID.remove();
        DATA_SCOPE.remove();
    }
}
