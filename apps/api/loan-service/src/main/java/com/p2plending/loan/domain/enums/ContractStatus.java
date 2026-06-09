package com.p2plending.loan.domain.enums;

/** Trạng thái ký của một hợp đồng điện tử. */
public enum ContractStatus {
    /** Đã phát hành qua nhà cung cấp (mock VNPT), chờ bên liên quan ký bằng OTP. */
    PENDING_SIGNATURE,
    /** Đã ký thành công. */
    SIGNED,
    /** Đã hủy (vd: khoản bị hủy, offer không còn hợp lệ khi ký). */
    VOIDED
}
