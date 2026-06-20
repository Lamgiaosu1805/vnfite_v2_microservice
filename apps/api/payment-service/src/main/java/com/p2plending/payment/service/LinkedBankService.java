package com.p2plending.payment.service;

import com.p2plending.payment.config.AppProperties;
import com.p2plending.payment.domain.entity.LinkedBank;
import com.p2plending.payment.domain.repository.LinkedBankRepository;
import com.p2plending.payment.dto.request.AddBankRequest;
import com.p2plending.payment.dto.response.BankCatalogItem;
import com.p2plending.payment.dto.response.LinkedBankResponse;
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
        return linkedBankRepository
                .findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LinkedBankResponse addBank(String userId, AddBankRequest req) {
        if (linkedBankRepository.existsByUserIdAndBankAccountNoAndIsDeletedFalse(
                userId, req.getBankAccountNo())) {
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
            validateAccountNameMatchesKyc(userId, accountName, req.getBankAccountNo());
        }

        if (req.isDefault()) {
            resetDefaults(userId);
        }
        boolean shouldBeDefault = req.isDefault() ||
                linkedBankRepository.findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId).isEmpty();

        LinkedBank bank = linkedBankRepository.save(LinkedBank.builder()
                .userId(userId)
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
                    .findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId)
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

    private void resetDefaults(String userId) {
        linkedBankRepository
                .findByUserIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(userId)
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
                .bankCode(b.getBankCode())
                .bankName(b.getBankName())
                .bankAccountNo(b.getBankAccountNo())
                .accountName(b.getAccountName())
                .isDefault(b.isDefault())
                .build();
    }
}
