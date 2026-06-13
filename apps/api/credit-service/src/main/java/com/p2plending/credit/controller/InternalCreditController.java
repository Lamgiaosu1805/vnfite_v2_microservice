package com.p2plending.credit.controller;

import com.p2plending.credit.config.AppProperties;
import com.p2plending.credit.domain.entity.BorrowerProfile;
import com.p2plending.credit.domain.entity.DocumentAnalysis;
import com.p2plending.credit.dto.request.AnalyzeDocumentRequest;
import com.p2plending.credit.dto.request.BorrowerProfileRequest;
import com.p2plending.credit.dto.request.EvaluateScoreRequest;
import com.p2plending.credit.dto.response.CreditScoreResponse;
import com.p2plending.credit.service.CreditScoringService;
import com.p2plending.credit.service.DocumentAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints nội bộ cho CMS / loan-service — bảo vệ bằng X-Internal-Secret header.
 * Không expose qua Nginx.
 */
@RestController
@RequestMapping("/internal/credit")
@RequiredArgsConstructor
@Slf4j
public class InternalCreditController {

    private final CreditScoringService creditScoringService;
    private final DocumentAnalysisService documentAnalysisService;
    private final AppProperties appProperties;

    private boolean unauthorized(String secret) {
        return !appProperties.getInternal().getSecret().equals(secret);
    }

    // ─── Hồ sơ tài chính tự khai ─────────────────────────────────────────────

    /** Upsert hồ sơ tài chính (mobile app gửi khi nộp đơn vay lần đầu, qua loan-service forward) */
    @PostMapping("/profiles")
    public ResponseEntity<?> upsertProfile(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody BorrowerProfileRequest req) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        BorrowerProfile saved = creditScoringService.upsertProfile(req);
        return ResponseEntity.ok(Map.of("status", "OK", "profileId", saved.getId()));
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(creditScoringService.getProfile(userId));
    }

    // ─── Chấm điểm ───────────────────────────────────────────────────────────

    /** CMS gọi khi thẩm định khoản gọi vốn → chấm điểm + AI advisory */
    @PostMapping("/scores/evaluate")
    public ResponseEntity<?> evaluate(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody EvaluateScoreRequest req) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(creditScoringService.evaluate(req));
    }

    /** Lấy điểm VALID gần nhất của user (CMS hiển thị nhanh không cần chấm lại) */
    @GetMapping("/scores/{userId}")
    public ResponseEntity<?> getLatest(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String userId) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        CreditScoreResponse resp = creditScoringService.getLatest(userId);
        return ResponseEntity.ok(resp);
    }

    /** Lấy điểm gần nhất đã lưu theo khoản gọi vốn — CMS dùng khi mở lại màn thẩm định. */
    @GetMapping("/scores/by-loan/{loanRequestId}")
    public ResponseEntity<?> getLatestByLoan(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable String loanRequestId) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        CreditScoreResponse resp = creditScoringService.getLatestByLoan(loanRequestId);
        return ResponseEntity.ok(resp);
    }

    // ─── Phân tích chứng từ tài chính/thu nhập (AI) ─────────────────────────

    /**
     * AI đọc chứng từ (sao kê lương, sao kê ngân hàng, hóa đơn, sổ bán hàng, HĐLĐ, ĐKKD...): trích xuất dữ liệu,
     * kiểm tra nhất quán nội tại, đối chiếu khai báo.
     * Yêu cầu APP_AI_ENABLED=true. Kết quả chỉ là CẢNH BÁO cho admin — không phải phán quyết.
     */
    @PostMapping("/documents/analyze")
    public ResponseEntity<?> analyzeDocument(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody AnalyzeDocumentRequest req) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        DocumentAnalysis result = documentAnalysisService.analyze(req);
        return ResponseEntity.ok(result);
    }

    /** Danh sách kết quả phân tích chứng từ — theo user hoặc theo khoản gọi vốn */
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String loanRequestId) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (loanRequestId != null) {
            return ResponseEntity.ok(documentAnalysisService.listByLoan(loanRequestId));
        }
        if (userId != null) {
            return ResponseEntity.ok(documentAnalysisService.listByUser(userId));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Cần truyền userId hoặc loanRequestId"));
    }

    // ─── Outcome label (training data) ───────────────────────────────────────

    /** loan-service gọi khi khoản vay COMPLETED/DEFAULTED → điền label cho ML tương lai */
    @PostMapping("/outcomes")
    public ResponseEntity<?> recordOutcome(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam String userId,
            @RequestParam String loanRequestId,
            @RequestParam String outcome) {

        if (unauthorized(secret)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (!"COMPLETED".equals(outcome) && !"DEFAULTED".equals(outcome)) {
            return ResponseEntity.badRequest().body(Map.of("error", "outcome phải là COMPLETED hoặc DEFAULTED"));
        }
        creditScoringService.recordOutcome(userId, loanRequestId, outcome);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
