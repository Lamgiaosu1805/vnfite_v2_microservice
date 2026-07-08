package com.p2plending.cms.controller;

import com.p2plending.cms.dto.response.AuditLogResponse;
import com.p2plending.cms.dto.response.PagedResponse;
import com.p2plending.cms.service.LoanManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * API kiểm toán quyết định tín dụng.
 *
 * Chỉ ADMIN/SUPER_ADMIN mới có quyền xem — OPS (thẩm định viên) không xem được
 * vì audit log chứa thông tin về chất lượng đề xuất của chính họ.
 */
@RestController
@RequestMapping("/cms/audit/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'APPROVER', 'APPRAISER')")
public class AuditLogController {

    private final LoanManagementService loanService;

    /**
     * GET /cms/audit/loans
     * Danh sách quyết định phê duyệt/từ chối. Sắp xếp mới nhất trước.
     *
     * @param loanId    Lọc theo khoản cụ thể (optional)
     * @param decision  APPROVED | REJECTED (optional)
     * @param decidedBy Username ban lãnh đạo (optional)
     */
    @GetMapping
    public ResponseEntity<PagedResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String decidedBy,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(loanService.listAuditLogs(loanId, decision, decidedBy, page, size));
    }

    /**
     * GET /cms/audit/loans/{id}
     * Chi tiết một bản ghi — bao gồm JSON đầy đủ của engine thẩm định (dùng cho ML export).
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(loanService.getAuditLogById(id));
    }
}
