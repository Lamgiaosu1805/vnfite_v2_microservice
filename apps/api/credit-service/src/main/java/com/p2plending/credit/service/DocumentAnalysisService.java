package com.p2plending.credit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.domain.entity.DocumentAnalysis;
import com.p2plending.credit.domain.repository.DocumentAnalysisRepository;
import com.p2plending.credit.dto.request.AnalyzeDocumentRequest;
import com.p2plending.credit.service.ai.AiDocumentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisService {

    private static final Set<String> IMAGE_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;   // giới hạn ảnh của Claude API
    private static final long MAX_PDF_BYTES   = 30L * 1024 * 1024;

    private final AiDocumentAnalyzer documentAnalyzer;
    private final DocumentAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DocumentAnalysis analyze(AnalyzeDocumentRequest req) {
        validateFile(req);

        AiDocumentAnalyzer.DocumentCheckResult result =
                documentAnalyzer.analyze(req.getMimeType(), req.getFileBase64(), buildContext(req));

        if (result == null) {
            throw new IllegalStateException(
                    "AI phân tích chứng từ chưa được bật — cần APP_AI_ENABLED=true và ANTHROPIC_API_KEY");
        }

        DocumentAnalysis entity = DocumentAnalysis.builder()
                .userId(req.getUserId())
                .loanRequestId(req.getLoanRequestId())
                .docType(req.getDocType())
                .fileName(req.getFileName())
                .verdict(result.verdict() != null ? result.verdict() : "UNREADABLE")
                .trustScore(result.trustScore())
                .extractedData(toJson(result))
                .summary(result.summary())
                .build();
        entity = analysisRepository.save(entity);

        log.info("Phân tích chứng từ xong: userId={} loanRequestId={} docType={} verdict={} trustScore={}",
                req.getUserId(), req.getLoanRequestId(), req.getDocType(),
                entity.getVerdict(), entity.getTrustScore());
        return entity;
    }

    @Transactional(readOnly = true)
    public List<DocumentAnalysis> listByUser(String userId) {
        return analysisRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<DocumentAnalysis> listByLoan(Long loanRequestId) {
        return analysisRepository.findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtDesc(loanRequestId);
    }

    private void validateFile(AnalyzeDocumentRequest req) {
        String mime = req.getMimeType().toLowerCase();
        boolean isPdf = "application/pdf".equals(mime);
        if (!isPdf && !IMAGE_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException(
                    "mimeType không hỗ trợ: " + req.getMimeType() + " (chỉ nhận jpeg/png/webp/gif/pdf)");
        }

        // Ước lượng dung lượng từ độ dài base64
        long approxBytes = req.getFileBase64().length() * 3L / 4;
        if (isPdf && approxBytes > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("File PDF quá lớn (tối đa 30MB)");
        }
        if (!isPdf && approxBytes > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh quá lớn (tối đa 5MB) — vui lòng resize trước khi upload");
        }
    }

    private String buildContext(AnalyzeDocumentRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Loại chứng từ người vay khai: ").append(req.getDocType()).append("\n\n");
        sb.append("THÔNG TIN NGƯỜI VAY KHAI BÁO (để đối chiếu):\n");
        sb.append("- Họ tên: ").append(orUnknown(req.getDeclaredFullName())).append("\n");
        sb.append("- Thu nhập hàng tháng: ")
                .append(req.getDeclaredMonthlyIncome() != null ? req.getDeclaredMonthlyIncome() + " VND" : "(không khai)")
                .append("\n");
        sb.append("- Nghề nghiệp: ").append(orUnknown(req.getDeclaredOccupation())).append("\n");
        sb.append("- Nơi làm việc: ").append(orUnknown(req.getDeclaredWorkplace())).append("\n\n");
        sb.append("Hãy phân tích chứng từ đính kèm theo đúng quy trình 4 bước.");
        return sb.toString();
    }

    private String orUnknown(String v) {
        return v != null && !v.isBlank() ? v : "(không khai)";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("JSON serialize error: {}", e.getMessage());
            return null;
        }
    }
}
