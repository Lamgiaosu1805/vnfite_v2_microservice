package com.p2plending.loan.dto.response;

import com.p2plending.loan.domain.enums.LoanStatus;
import com.p2plending.loan.domain.enums.RepaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả hỗ trợ thẩm định cho một khoản gọi vốn: điểm rủi ro, năng lực trả nợ,
 * số tiền & lãi suất đề xuất, xem trước lịch trả nợ, và checklist thẩm định thủ công.
 *
 * <p>Toàn bộ là gợi ý (expert-prior, Phase 0) — KHÔNG phải quyết định tự động.
 */
@Getter
@Builder
public class AppraisalSuggestionResponse {

    private String loanId;
    private String loanCode;
    private LoanStatus status;
    private BigDecimal requestedAmount;
    private Integer termMonths;

    /** Nhóm sản phẩm (1–4) theo biểu lãi suất QĐ-LSGV. */
    private Integer productGroup;
    private String productName;

    private Affordability affordability;
    private Recommendation recommendation;
    private SchedulePreview schedulePreview;

    /** Các mục thẩm định viên phải tự xác minh — hướng dẫn thao tác. */
    private List<ChecklistItem> manualChecklist;

    /** Cảnh báo tự động từ kiểm tra nhất quán/đầy đủ dữ liệu. */
    private List<String> autoWarnings;

    private String disclaimer;

    // ── Năng lực trả nợ ────────────────────────────────────────────
    @Getter
    @Builder
    public static class Affordability {
        private boolean incomeProvided;
        private BigDecimal monthlyIncome;
        /** Trần PTI áp dụng (vd 0.40 = 40%). */
        private BigDecimal ptiCap;
        /** Khoản trả hằng tháng nếu cho vay đúng số tiền yêu cầu (theo lãi suất đề xuất). */
        private BigDecimal requestedInstallment;
        /** Tỷ lệ trả nợ / thu nhập ở số tiền yêu cầu. */
        private BigDecimal requestedPti;
        /** Mức trả tối đa thu nhập gánh được = income × ptiCap. */
        private BigDecimal maxInstallmentByIncome;
        /** Số tiền gốc tối đa suy ra từ năng lực trả nợ. */
        private BigDecimal maxPrincipalByIncome;
    }

    // ── Đề xuất ────────────────────────────────────────────────────
    @Getter
    @Builder
    public static class Recommendation {
        private BigDecimal suggestedAmount;
        /** Ràng buộc quyết định số tiền (thu nhập / hạng rủi ro / trần sản phẩm / yêu cầu). */
        private String amountCapReason;
        /** Lãi suất gọi vốn tối thiểu theo biểu (đã cộng phụ phí lĩnh vực nếu có), %/năm. */
        private BigDecimal suggestedInterestRate;
        /** Khoảng lãi suất thương lượng: từ mức tối thiểu đến trần pháp luật. */
        private BigDecimal suggestedRateMin;
        private BigDecimal suggestedRateMax;
        /** Phí kết nối (giải ngân) thành công — % trên giá trị giải ngân. */
        private BigDecimal feePercent;
        /** Số tiền phí giải ngân = suggestedAmount × feePercent%. */
        private BigDecimal connectionFee;
        /** false khi ô biểu là "Không cấp dịch vụ" cho (nhóm × hạng) này. */
        private boolean serviceAvailable;
        private String rateNote;
    }

    // ── Xem trước lịch trả nợ (ở số tiền & lãi đề xuất) ─────────────
    @Getter
    @Builder
    public static class SchedulePreview {
        private RepaymentMethod method;
        private int periods;
        private BigDecimal firstInstallment;
        private BigDecimal totalPrincipal;
        private BigDecimal totalInterest;
        private BigDecimal totalPayable;
    }

    // ── Checklist thẩm định thủ công ───────────────────────────────
    @Getter
    @Builder
    public static class ChecklistItem {
        private String code;
        /** IDENTITY | INCOME | EMPLOYMENT | REFERENCE | PURPOSE | DOCUMENT | FRAUD */
        private String category;
        private String title;
        /** Hướng dẫn thao tác cụ thể cho thẩm định viên. */
        private String instruction;
        private boolean required;
    }
}
