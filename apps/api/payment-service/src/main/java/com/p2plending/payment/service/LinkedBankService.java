package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.enums.WalletOwnerType;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.dto.request.AddBankRequest;
import com.p2plending.payment.dto.response.BankCatalogItem;
import com.p2plending.payment.dto.response.LinkedBankResponse;
import com.p2plending.payment.service.AuthServiceClient.BusinessProfileInfo;
import com.p2plending.payment.util.BusinessNameMatcher;
import com.p2plending.payment.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedBankService {

    private final LinkedBankRepository linkedBankRepository;
    private final VietQrClient vietQrClient;
    private final TikluyClient tikluyClient;
    private final AuthServiceClient authServiceClient;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<LinkedBankResponse> listBanks(String userId) {
        return listBanks(userId, WalletOwnerType.PERSONAL);
    }

    @Transactional(readOnly = true)
    public List<LinkedBankResponse> listBanks(String userId, WalletOwnerType ownerType) {
        return linkedBankRepository
                .findByUserIdAndOwnerTypeAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId, ownerType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LinkedBankResponse addBank(String userId, AddBankRequest req) {
        WalletOwnerType ownerType = parseOwnerType(req.getOwnerType());
        if (linkedBankRepository.existsByUserIdAndOwnerTypeAndBankCodeAndBankAccountNoAndIsDeletedFalse(
                userId, ownerType, req.getBankCode(), req.getBankAccountNo())) {
            throw new IllegalStateException("Tài khoản ngân hàng này đã được liên kết");
        }

        String accountName = req.getAccountName();

        if (appProperties.getPayment().isMock()) {
            if (accountName == null || accountName.isBlank()) {
                accountName = "MOCK ACCOUNT NAME";
            }
            log.info("userId={} [MOCK] Bank verify + name check skipped", userId);
        } else {
            String txnId = UUID.randomUUID().toString();
            accountName = tikluyClient.verifyBankAccount(txnId, req.getBankCode(), req.getBankAccountNo());
            if (accountName == null || accountName.isBlank()) {
                throw new IllegalArgumentException(
                        "Không tìm thấy số tài khoản " + req.getBankAccountNo()
                        + " tại " + req.getBankName() + ". Vui lòng kiểm tra lại.");
            }
            if (ownerType == WalletOwnerType.PERSONAL) {
                validateAccountNameMatchesKyc(userId, accountName, req.getBankAccountNo());
            } else {
                validateBusinessAccountName(userId, accountName, req.getBankAccountNo());
            }
        }

        if (req.isDefault()) {
            resetDefaults(userId, ownerType);
        }
        boolean shouldBeDefault = req.isDefault() ||
                linkedBankRepository
                        .findByUserIdAndOwnerTypeAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId, ownerType)
                        .isEmpty();

        LinkedBank bank = linkedBankRepository.save(LinkedBank.builder()
                .userId(userId)
                .ownerType(ownerType)
                .bankCode(req.getBankCode())
                .bankName(req.getBankName())
                .bankAccountNo(req.getBankAccountNo())
                .accountName(accountName)
                .isDefault(shouldBeDefault)
                .build());

        return toResponse(bank);
    }

    @Transactional
    public void removeBank(String userId, String bankId) {
        LinkedBank bank = linkedBankRepository
                .findByIdAndUserIdAndIsDeletedFalse(bankId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản ngân hàng không tồn tại"));

        bank.setDeleted(true);
        linkedBankRepository.save(bank);

        if (bank.isDefault()) {
            linkedBankRepository
                    .findByUserIdAndOwnerTypeAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(
                            userId, bank.getOwnerType())
                    .stream().findFirst()
                    .ifPresent(b -> {
                        b.setDefault(true);
                        linkedBankRepository.save(b);
                    });
        }
    }

    /**
     * Lấy danh sách ngân hàng từ VietQR API (cache Redis 24h).
     */
    public List<BankCatalogItem> getBankCatalog() {
        return vietQrClient.getBankList();
    }

    /**
     * Tra tên chủ tài khoản ngân hàng qua TIKLUY → MB Bank.
     */
    @Transactional(readOnly = true)
    public String verifyBankAccount(String userId, String bankCode, String bankAccountNo) {
        if (appProperties.getPayment().isMock()) {
            return "MOCK_ACCOUNT_NAME";
        }
        String txnId = UUID.randomUUID().toString();
        return tikluyClient.verifyBankAccount(txnId, bankCode, bankAccountNo);
    }

    private void validateAccountNameMatchesKyc(String userId, String tikluyName, String bankAccountNo) {
        String userFullName = authServiceClient.getUserFullName(userId);
        if (userFullName == null || userFullName.isBlank()) {
            log.warn("userId={} fullName not found in auth-service, skipping name check", userId);
            return;
        }
        String normalizedKyc    = TextNormalizer.normalize(userFullName);
        String normalizedTikluy = TextNormalizer.normalize(tikluyName);
        if (!normalizedKyc.equals(normalizedTikluy)) {
            log.warn("userId={} name mismatch: kyc='{}' tikluy='{}' stk={}",
                    userId, normalizedKyc, normalizedTikluy, bankAccountNo);
            throw new IllegalArgumentException(
                    "Tên chủ tài khoản (" + tikluyName + ") không khớp với thông tin định danh của bạn. "
                    + "Vui lòng liên kết tài khoản ngân hàng đứng tên chính chủ.");
        }
    }

    /**
     * Đối chiếu tên chủ tài khoản ngân hàng với hồ sơ doanh nghiệp cho ví DN.
     * <ul>
     *   <li><b>Công ty (ENTERPRISE)</b>: chỉ chấp nhận tài khoản đứng tên công ty (khớp mềm tên ĐKKD).</li>
     *   <li><b>Hộ kinh doanh (BUSINESS)</b>: chấp nhận tài khoản đứng tên hộ KD, hoặc chính chủ hộ
     *       (khớp eKYC cá nhân / người đại diện) — vì hộ KD thực tế hay dùng tài khoản cá nhân.</li>
     * </ul>
     * Không lấy được hồ sơ (transient/không có) → fail-open kèm cảnh báo, giống luồng cá nhân;
     * chốt chặn cuối vẫn là admin đã duyệt hồ sơ + OTP khi rút.
     */
    private void validateBusinessAccountName(String userId, String tikluyName, String bankAccountNo) {
        BusinessProfileInfo profile = authServiceClient.getBusinessProfile(userId);
        if (profile == null || profile.businessName() == null || profile.businessName().isBlank()) {
            log.warn("userId={} không lấy được hồ sơ DN, bỏ qua đối chiếu tên TK ngân hàng ví DN stk={}",
                    userId, bankAccountNo);
            return;
        }

        if (BusinessNameMatcher.matches(profile.businessName(), tikluyName)) {
            return;
        }

        boolean household = profile.isHousehold();
        if (household) {
            String personalName = authServiceClient.getUserFullName(userId);
            if (personalName != null && BusinessNameMatcher.matches(personalName, tikluyName)) {
                return;
            }
            if (profile.representativeName() != null
                    && BusinessNameMatcher.matches(profile.representativeName(), tikluyName)) {
                return;
            }
        }

        log.warn("userId={} tên TK ví DN không khớp: tikluy='{}' business='{}' type={} stk={}",
                userId, tikluyName, profile.businessName(), profile.businessType(), bankAccountNo);
        throw new IllegalArgumentException(household
                ? "Tên chủ tài khoản (" + tikluyName + ") không khớp tên hộ kinh doanh hoặc chủ hộ. "
                  + "Vui lòng dùng tài khoản đứng tên hộ kinh doanh hoặc chính chủ hộ."
                : "Tên chủ tài khoản (" + tikluyName + ") không khớp tên doanh nghiệp trên giấy đăng ký kinh doanh. "
                  + "Ví doanh nghiệp chỉ được rút về tài khoản đứng tên công ty.");
    }

    private void resetDefaults(String userId, WalletOwnerType ownerType) {
        linkedBankRepository
                .findByUserIdAndOwnerTypeAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId, ownerType)
                .forEach(b -> {
                    if (b.isDefault()) {
                        b.setDefault(false);
                        linkedBankRepository.save(b);
                    }
                });
    }

    private LinkedBankResponse toResponse(LinkedBank b) {
        return LinkedBankResponse.builder()
                .id(b.getId())
                .ownerType(b.getOwnerType().name())
                .bankCode(b.getBankCode())
                .bankName(b.getBankName())
                .bankAccountNo(b.getBankAccountNo())
                .accountName(b.getAccountName())
                .isDefault(b.isDefault())
                .build();
    }

    private WalletOwnerType parseOwnerType(String ownerType) {
        if (ownerType == null || ownerType.isBlank()) {
            return WalletOwnerType.PERSONAL;
        }
        return WalletOwnerType.valueOf(ownerType.trim().toUpperCase());
    }
}
