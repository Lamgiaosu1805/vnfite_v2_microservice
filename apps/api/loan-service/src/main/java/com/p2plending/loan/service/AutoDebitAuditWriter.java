package com.p2plending.loan.service;

import com.p2plending.loan.domain.entity.RepaymentAutoDebitAudit;
import com.p2plending.loan.domain.entity.RepaymentAutoDebitAuditItem;
import com.p2plending.loan.domain.repository.RepaymentAutoDebitAuditItemRepository;
import com.p2plending.loan.domain.repository.RepaymentAutoDebitAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lưu audit tổng quan + chi tiết từng khoản của một lần quét auto-debit trong CÙNG một transaction.
 *
 * <p>Tách riêng bean này (thay vì gọi 2 repository trực tiếp trong {@link AutoDebitSweepService})
 * để {@code @Transactional} phát huy tác dụng qua Spring proxy — gọi từ bean khác, không phải
 * self-invocation. Đảm bảo summary và chi tiết từng khoản luôn cùng thành công hoặc cùng rollback,
 * tránh tình trạng có summary nhưng rỗng chi tiết khi bước lưu item gặp lỗi tạm thời.
 */
@Service
@RequiredArgsConstructor
public class AutoDebitAuditWriter {

    private final RepaymentAutoDebitAuditRepository auditRepository;
    private final RepaymentAutoDebitAuditItemRepository auditItemRepository;

    @Transactional
    public RepaymentAutoDebitAudit save(RepaymentAutoDebitAudit audit, List<RepaymentAutoDebitAuditItem> items) {
        RepaymentAutoDebitAudit saved = auditRepository.save(audit);
        auditItemRepository.saveAll(items.stream()
                .peek(item -> item.setAuditId(saved.getId()))
                .toList());
        return saved;
    }
}
