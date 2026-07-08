package com.p2plending.cms.security;

import java.util.Set;

/**
 * Quyền lẻ theo tính năng — cho phép cấp một tính năng nhạy cảm cụ thể của phòng ban
 * khác cho tài khoản, mà không cần gán cả vai trò ({@link CmsRoles}) phòng ban đó.
 * Dùng authority thô (không tiền tố ROLE_) qua {@code hasAuthority(...)}.
 */
public final class CmsPermissions {

    private CmsPermissions() {}

    public static final String LOAN_APPROVE = "loan.approve";
    public static final String LOAN_DISBURSE = "loan.disburse";
    public static final String LOAN_PROPOSE = "loan.propose";
    public static final String LOAN_PRODUCT_EDIT = "loan.product.edit";
    public static final String KYC_DECIDE = "kyc.decide";
    public static final String BUSINESS_DECIDE = "business.decide";
    public static final String FINANCE_RECONCILE = "finance.reconcile";

    public static final Set<String> ASSIGNABLE = Set.of(
            LOAN_APPROVE, LOAN_DISBURSE, LOAN_PROPOSE, LOAN_PRODUCT_EDIT,
            KYC_DECIDE, BUSINESS_DECIDE, FINANCE_RECONCILE
    );

    public static boolean isValidAssignable(String permission) {
        return permission != null && ASSIGNABLE.contains(permission);
    }
}
