package com.p2plending.auth.service;

import com.p2plending.auth.domain.entity.BusinessProfile;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.AccountType;
import com.p2plending.auth.domain.enums.BusinessType;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.BusinessProfileRepository;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.BusinessProfileSubmitRequest;
import com.p2plending.auth.dto.response.BusinessProfileResponse;
import com.p2plending.auth.exception.BusinessProfileConflictException;
import com.p2plending.auth.exception.InvalidIdentityException;
import com.p2plending.auth.kafka.KafkaProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hồ sơ doanh nghiệp — mô hình "1 tài khoản – 2 tư cách":
 * nộp yêu cầu eKYC APPROVED, mỗi user 1 hồ sơ active, duyệt map đúng account_type
 * (HOUSEHOLD → BUSINESS, COMPANY → ENTERPRISE) và publish event tạo ví DN.
 */
@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {

    @Mock private BusinessProfileRepository businessProfileRepository;
    @Mock private UserRepository            userRepository;
    @Mock private KycSubmissionRepository   kycSubmissionRepository;
    @Mock private ImageStorageService       imageStorageService;
    @Mock private KafkaProducerService      kafkaProducerService;

    @InjectMocks private BusinessProfileService service;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User userWithKyc(KycStatus status) {
        return User.builder()
                .id("user-1")
                .phone("0912345678")
                .password("x")
                .kycStatus(status)
                .accountType(AccountType.INDIVIDUAL)
                .build();
    }

    private BusinessProfileSubmitRequest submitRequest(BusinessType type) {
        BusinessProfileSubmitRequest req = new BusinessProfileSubmitRequest();
        req.setBusinessType(type);
        req.setBusinessName("Hộ KD Nguyễn Văn A");
        req.setRegistrationNumber("41A8012345");
        req.setHeadOfficeAddress("123 Lê Lợi, Hà Nội");
        req.setRepresentativeName("Nguyễn Văn A");
        req.setRepresentativeCccd("001087016297");
        req.setLicenseImage(new MockMultipartFile("licenseImage", "gpkd.jpg", "image/jpeg", new byte[]{1}));
        return req;
    }

    private BusinessProfile pendingProfile(BusinessType type) {
        return BusinessProfile.builder()
                .id("bp-1")
                .userId("user-1")
                .businessType(type)
                .businessName("Hộ KD Nguyễn Văn A")
                .registrationNumber("41A8012345")
                .headOfficeAddress("123 Lê Lợi, Hà Nội")
                .representativeName("Nguyễn Văn A")
                .representativeCccd("001087016297")
                .licenseImageId("file-1")
                .status(KycStatus.PENDING)
                .build();
    }

    // ── 1. Nộp hồ sơ yêu cầu eKYC APPROVED ───────────────────────────────────

    @Test
    void submit_requiresApprovedEkyc() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.NONE)));

        assertThatThrownBy(() -> service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD)))
                .isInstanceOf(InvalidIdentityException.class)
                .hasMessageContaining("xác minh danh tính");

        verify(businessProfileRepository, never()).save(any());
    }

    // ── 2. Chặn nộp trùng khi đã có hồ sơ PENDING/APPROVED ───────────────────

    @Test
    void submit_blocksDuplicateActiveProfile() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.APPROVED)));
        when(businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(eq("user-1"), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD)))
                .isInstanceOf(BusinessProfileConflictException.class);

        verify(businessProfileRepository, never()).save(any());
    }

    // ── 3. Nộp thành công → PENDING, upload ảnh, soft-delete hồ sơ REJECTED cũ ─

    @Test
    void submit_success_replacesRejectedProfile() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.APPROVED)));
        when(businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(eq("user-1"), any()))
                .thenReturn(false);
        BusinessProfile rejected = pendingProfile(BusinessType.HOUSEHOLD);
        rejected.setStatus(KycStatus.REJECTED);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(rejected));
        when(imageStorageService.upload(any())).thenReturn("file-new");
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(kycSubmissionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());

        BusinessProfileResponse response = service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD));

        assertThat(response.getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.getLicenseImageId()).isEqualTo("file-new");
        assertThat(rejected.isDeleted()).isTrue();   // hồ sơ REJECTED cũ bị soft-delete
    }

    // ── 3b. Chặn nộp lại trong vòng 14 ngày kể từ lúc bị từ chối ──────────────

    @Test
    void submit_blocksResubmit_within14DaysOfRejection() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.APPROVED)));
        when(businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(eq("user-1"), any()))
                .thenReturn(false);
        BusinessProfile rejected = pendingProfile(BusinessType.HOUSEHOLD);
        rejected.setStatus(KycStatus.REJECTED);
        rejected.setReviewedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(5));
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD)))
                .isInstanceOf(BusinessProfileConflictException.class)
                .hasMessageContaining("ngày nữa");

        verify(businessProfileRepository, never()).save(any());
        assertThat(rejected.isDeleted()).isFalse();
    }

    @Test
    void submit_allowsResubmit_after14DaysOfRejection() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.APPROVED)));
        when(businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(eq("user-1"), any()))
                .thenReturn(false);
        BusinessProfile rejected = pendingProfile(BusinessType.HOUSEHOLD);
        rejected.setStatus(KycStatus.REJECTED);
        rejected.setReviewedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(15));
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(rejected));
        when(imageStorageService.upload(any())).thenReturn("file-new");
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(kycSubmissionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());

        BusinessProfileResponse response = service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD));

        assertThat(response.getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(rejected.isDeleted()).isTrue();
    }

    // ── 4. Duyệt hộ kinh doanh → account_type BUSINESS + publish event ────────

    @Test
    void decide_approveHousehold_setsAccountTypeBusiness_andPublishesEvent() {
        BusinessProfile profile = pendingProfile(BusinessType.HOUSEHOLD);
        User user = userWithKyc(KycStatus.APPROVED);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(profile));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.decide("user-1", true, null, "admin-cms", null);

        assertThat(profile.getStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(profile.getReviewedBy()).isEqualTo("admin-cms");
        assertThat(user.getAccountType()).isEqualTo(AccountType.BUSINESS);
        verify(kafkaProducerService).publishBusinessProfileApproved(
                eq("user-1"), eq("Hộ KD Nguyễn Văn A"), eq("HOUSEHOLD"), eq("41A8012345"), any());
    }

    // ── 5. Duyệt công ty → account_type ENTERPRISE ────────────────────────────

    @Test
    void decide_approveCompany_setsAccountTypeEnterprise() {
        BusinessProfile profile = pendingProfile(BusinessType.COMPANY);
        User user = userWithKyc(KycStatus.APPROVED);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(profile));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.decide("user-1", true, null, "admin-cms", null);

        assertThat(user.getAccountType()).isEqualTo(AccountType.ENTERPRISE);
    }

    // ── 5b. Duyệt kèm tên VietQR tra được → ghi đè tên tự nhập ─────────────────

    @Test
    void decide_approveWithResolvedBusinessName_overridesSubmittedName() {
        BusinessProfile profile = pendingProfile(BusinessType.COMPANY);
        User user = userWithKyc(KycStatus.APPROVED);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(profile));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.decide("user-1", true, null, "admin-cms", "CÔNG TY TNHH ABC (VIETQR)");

        assertThat(profile.getBusinessName()).isEqualTo("CÔNG TY TNHH ABC (VIETQR)");
    }

    // ── 6. Từ chối → giữ nguyên account_type, lưu lý do, không publish ────────

    @Test
    void decide_reject_keepsAccountType_andStoresReason() {
        BusinessProfile profile = pendingProfile(BusinessType.COMPANY);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(profile));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));

        service.decide("user-1", false, "GPKD mờ, không đọc được số đăng ký", "admin-cms", null);

        assertThat(profile.getStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(profile.getRejectReason()).contains("GPKD mờ");
        verify(userRepository, never()).save(any());
        verify(kafkaProducerService, never()).publishBusinessProfileApproved(any(), any(), any(), any(), any());
    }

    // ── 7. Không cho quyết định hồ sơ đã chốt ─────────────────────────────────

    @Test
    void decide_rejectsWhenProfileNotPending() {
        BusinessProfile profile = pendingProfile(BusinessType.HOUSEHOLD);
        profile.setStatus(KycStatus.APPROVED);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.decide("user-1", true, null, "admin-cms", null))
                .isInstanceOf(BusinessProfileConflictException.class)
                .hasMessageContaining("không ở trạng thái chờ duyệt");
    }

    // ── 8. Flag lệch CCCD người đại diện so với eKYC ──────────────────────────

    @Test
    void response_flagsRepresentativeMismatch() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithKyc(KycStatus.APPROVED)));
        when(businessProfileRepository.existsByUserIdAndStatusInAndIsDeletedFalse(eq("user-1"), any()))
                .thenReturn(false);
        when(businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc("user-1"))
                .thenReturn(Optional.empty());
        when(imageStorageService.upload(any())).thenReturn("file-new");
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));
        // eKYC của chủ tài khoản mang CCCD KHÁC với CCCD người đại diện trên hồ sơ
        var ekyc = com.p2plending.auth.domain.entity.KycSubmission.builder()
                .userId("user-1").cccdNumber("999988887777").fullName("Nguyễn Văn A").build();
        when(kycSubmissionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc("user-1", KycStatus.APPROVED))
                .thenReturn(Optional.of(ekyc));

        BusinessProfileResponse response = service.submit("user-1", submitRequest(BusinessType.HOUSEHOLD));

        assertThat(response.getRepresentativeMismatch()).isTrue();
    }
}
