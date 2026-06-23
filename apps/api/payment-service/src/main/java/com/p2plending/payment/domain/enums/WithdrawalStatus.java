package com.p2plending.payment.domain.enums;

import java.util.Set;

public enum WithdrawalStatus {

    /** Vừa tạo, chưa gửi OTP */
    INITIATED,
    /** Đã gửi OTP, chờ user xác nhận */
    OTP_PENDING,
    /** OTP đúng, tiền đã bị lock trong ví, đang gửi lệnh chuyển */
    FUNDS_LOCKED,
    /** Đã gửi lệnh chuyển sang TIKLUY, chờ phản hồi */
    TRANSFER_INITIATED,
    /** TIKLUY đang xử lý chuyển khoản */
    PROCESSING,
    /** Chuyển khoản thành công — trạng thái cuối */
    COMPLETED,
    /** TIKLUY báo transfer thất bại, có thể retry */
    TRANSFER_FAILED,
    /** Hết lần retry, transfer thất bại hoàn toàn — tiền sẽ được mở khóa */
    FAILED,
    /** User huỷ trước khi lock tiền */
    CANCELLED,
    /** Tiền đã được mở khóa sau FAILED — trạng thái cuối */
    FUNDS_RELEASED;

    /** Các trạng thái cuối — không thể chuyển tiếp */
    public static final Set<WithdrawalStatus> TERMINAL = Set.of(
            COMPLETED, CANCELLED, FUNDS_RELEASED
    );

    /** Các trạng thái chưa kết thúc — dùng để chặn user tạo withdrawal mới */
    public static final Set<WithdrawalStatus> ACTIVE = Set.of(
            INITIATED, OTP_PENDING, FUNDS_LOCKED, TRANSFER_INITIATED, PROCESSING
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(WithdrawalStatus next) {
        return switch (this) {
            case INITIATED          -> next == OTP_PENDING || next == CANCELLED;
            case OTP_PENDING        -> next == FUNDS_LOCKED || next == CANCELLED;
            case FUNDS_LOCKED       -> next == TRANSFER_INITIATED;
            case TRANSFER_INITIATED -> next == PROCESSING || next == TRANSFER_FAILED;
            case PROCESSING         -> next == COMPLETED || next == TRANSFER_FAILED;
            case TRANSFER_FAILED    -> next == TRANSFER_INITIATED || next == FAILED;
            case FAILED             -> next == FUNDS_RELEASED;
            default                 -> false;
        };
    }
}
