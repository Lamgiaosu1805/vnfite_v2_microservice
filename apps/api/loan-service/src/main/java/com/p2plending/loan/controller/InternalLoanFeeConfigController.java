package com.p2plending.loan.controller;

import com.p2plending.loan.dto.request.FeeConfigUpdateRequest;
import com.p2plending.loan.dto.response.LoanFeeConfigResponse;
import com.p2plending.loan.service.LoanFeeConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/loans/fee-config")
@RequiredArgsConstructor
public class InternalLoanFeeConfigController {

    private final LoanFeeConfigService loanFeeConfigService;

    @GetMapping
    public ResponseEntity<List<LoanFeeConfigResponse>> getAll() {
        return ResponseEntity.ok(loanFeeConfigService.getAll());
    }

    @GetMapping("/{feeType}")
    public ResponseEntity<LoanFeeConfigResponse> getByType(@PathVariable String feeType) {
        return ResponseEntity.ok(loanFeeConfigService.getByType(feeType));
    }

    @PutMapping
    public ResponseEntity<LoanFeeConfigResponse> upsert(
            @Valid @RequestBody FeeConfigUpdateRequest req,
            @RequestHeader(value = "X-CMS-Username", defaultValue = "system") String updatedBy
    ) {
        return ResponseEntity.ok(loanFeeConfigService.upsert(req, updatedBy));
    }
}
