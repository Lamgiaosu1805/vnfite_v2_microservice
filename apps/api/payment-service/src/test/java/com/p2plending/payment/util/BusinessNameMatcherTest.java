package com.p2plending.payment.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đối chiếu mềm tên tài khoản ngân hàng DN với tên ĐKKD — chịu được viết hoa/không dấu,
 * viết tắt loại hình, đảo thứ tự và thừa từ; nhưng vẫn từ chối tên hoàn toàn khác.
 */
class BusinessNameMatcherTest {

    @Test
    void company_sameName_differentCaseAndDiacritics_matches() {
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Minh Anh", "CONG TY TNHH MINH ANH")).isTrue();
    }

    @Test
    void company_legalFormAbbreviations_match() {
        // CTCP ↔ Công ty Cổ phần; CTY ↔ Công ty
        assertThat(BusinessNameMatcher.matches("Công ty Cổ phần Minh Anh", "CTCP MINH ANH")).isTrue();
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Minh Anh", "CTY TNHH MINH ANH")).isTrue();
    }

    @Test
    void company_bankNameOmitsTradeWords_matches() {
        // ĐKKD có thêm "Thương mại", tài khoản ngân hàng rút gọn — lõi {MINH,ANH} vẫn nằm trong
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Thương mại Minh Anh", "CONG TY TNHH MINH ANH")).isTrue();
    }

    @Test
    void company_letterD_normalized() {
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Đông Đô", "CONG TY TNHH DONG DO")).isTrue();
    }

    @Test
    void household_bankAccountInOwnerPersonalName_matches() {
        // Hộ KD hay dùng tài khoản cá nhân của chủ hộ
        assertThat(BusinessNameMatcher.matches("Hộ kinh doanh Nguyễn Văn A", "NGUYEN VAN A")).isTrue();
    }

    @Test
    void completelyDifferentName_doesNotMatch() {
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Minh Anh", "TRAN THI BICH")).isFalse();
    }

    @Test
    void onlyLegalFormTokens_doesNotMatch() {
        // Không có phần lõi đặc trưng → không thể coi là khớp
        assertThat(BusinessNameMatcher.matches("Công ty TNHH", "CONG TY CO PHAN")).isFalse();
    }

    @Test
    void blankInputs_doNotMatch() {
        assertThat(BusinessNameMatcher.matches(null, "CONG TY TNHH MINH ANH")).isFalse();
        assertThat(BusinessNameMatcher.matches("Công ty TNHH Minh Anh", "  ")).isFalse();
    }
}
