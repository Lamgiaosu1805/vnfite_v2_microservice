package com.p2plending.credit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.client.FileManagerClient;
import com.p2plending.credit.config.AppProperties;
import com.p2plending.credit.domain.entity.DocumentAnalysis;
import com.p2plending.credit.domain.repository.DocumentAnalysisRepository;
import com.p2plending.credit.dto.request.AnalyzeDocumentRequest;
import com.p2plending.credit.dto.request.EvaluateScoreRequest;
import com.p2plending.credit.service.ai.AiDocumentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final FileManagerClient fileManagerClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DocumentAnalysis analyze(AnalyzeDocumentRequest req) {
        // Lấy nội dung file: ưu tiên fileId (fetch từ file-manager), fallback base64 trực tiếp
        String mimeType;
        String fileBase64;
        if (req.getFileId() != null && !req.getFileId().isBlank()) {
            FileManagerClient.FetchedFile file = fileManagerClient.fetch(req.getFileId());
            mimeType = file.mimeType();
            fileBase64 = Base64.getEncoder().encodeToString(file.bytes());
        } else if (req.getFileBase64() != null && !req.getFileBase64().isBlank()) {
            if (req.getMimeType() == null || req.getMimeType().isBlank()) {
                throw new IllegalArgumentException("Cần mimeType khi gửi fileBase64");
            }
            mimeType = req.getMimeType();
            fileBase64 = req.getFileBase64();
        } else {
            throw new IllegalArgumentException("Cần truyền fileId hoặc fileBase64");
        }

        validateFile(mimeType, fileBase64);

        AiDocumentAnalyzer.DocumentCheckResult result =
                documentAnalyzer.analyze(mimeType, fileBase64, buildContext(req));

        if (result == null) {
            throw new IllegalStateException(
                    "AI phân tích chứng từ chưa được bật — cần APP_AI_ENABLED=true và ANTHROPIC_API_KEY");
        }

        DocumentAnalysis entity = DocumentAnalysis.builder()
                .userId(req.getUserId())
                .loanRequestId(req.getLoanRequestId())
                .docType(req.getDocType())
                .fileName(req.getFileName())
                .fileId(req.getFileId())
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

    /**
     * Phân tích toàn bộ chứng từ của khoản gọi vốn khi chấm điểm tín dụng.
     * Chứng từ đã phân tích trước đó (cùng loanRequestId + fileId) được tái sử dụng,
     * không tốn thêm lượt gọi AI. Mỗi file lỗi riêng lẻ không chặn việc chấm điểm —
     * trả về bản ghi transient verdict=ERROR để admin biết file nào chưa phân tích được.
     * AI tắt → trả danh sách rỗng.
     */
    @Transactional
    public List<DocumentAnalysis> analyzeForScoring(EvaluateScoreRequest req) {
        if (req.getDocuments() == null || req.getDocuments().isEmpty()
                || !appProperties.getAi().isEnabled()) {
            return List.of();
        }

        List<DocumentAnalysis> results = new ArrayList<>();
        for (EvaluateScoreRequest.DocumentRef doc : req.getDocuments()) {
            if (doc.getFileId() == null || doc.getFileId().isBlank()) continue;

            if (req.getLoanRequestId() != null) {
                var existing = analysisRepository
                        .findFirstByLoanRequestIdAndFileIdAndIsDeletedFalseOrderByCreatedAtDesc(
                                req.getLoanRequestId(), doc.getFileId());
                if (existing.isPresent()) {
                    results.add(existing.get());
                    continue;
                }
            }

            AnalyzeDocumentRequest analyzeReq = new AnalyzeDocumentRequest();
            analyzeReq.setUserId(req.getUserId());
            analyzeReq.setLoanRequestId(req.getLoanRequestId());
            analyzeReq.setDocType(doc.getDocType() != null ? doc.getDocType() : "OTHER");
            analyzeReq.setFileId(doc.getFileId());
            analyzeReq.setFileName(doc.getFileName());
            analyzeReq.setDeclaredFullName(req.getDeclaredFullName());
            analyzeReq.setDeclaredMonthlyIncome(req.getMonthlyIncome());
            analyzeReq.setDeclaredOccupation(req.getOccupation());
            analyzeReq.setDeclaredWorkplace(req.getDeclaredWorkplace());

            try {
                results.add(analyze(analyzeReq));
            } catch (Exception e) {
                log.error("Phân tích chứng từ thất bại khi chấm điểm: fileId={} fileName={}: {}",
                        doc.getFileId(), doc.getFileName(), e.getMessage());
                results.add(DocumentAnalysis.builder()
                        .userId(req.getUserId())
                        .loanRequestId(req.getLoanRequestId())
                        .docType(doc.getDocType() != null ? doc.getDocType() : "OTHER")
                        .fileName(doc.getFileName())
                        .fileId(doc.getFileId())
                        .verdict("ERROR")
                        .summary("Không phân tích được chứng từ này — vui lòng kiểm tra thủ công hoặc thử lại.")
                        .build());
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<DocumentAnalysis> listByUser(String userId) {
        return analysisRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<DocumentAnalysis> listByLoan(String loanRequestId) {
        return latestPerDocument(
                analysisRepository.findByLoanRequestIdAndIsDeletedFalseOrderByCreatedAtDesc(loanRequestId));
    }

    private List<DocumentAnalysis> latestPerDocument(List<DocumentAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return List.of();
        }

        Map<String, DocumentAnalysis> latest = new LinkedHashMap<>();
        for (DocumentAnalysis analysis : analyses) {
            String key = documentKey(analysis);
            latest.putIfAbsent(key, analysis);
        }
        return new ArrayList<>(latest.values());
    }

    private String documentKey(DocumentAnalysis analysis) {
        if (analysis.getFileId() != null && !analysis.getFileId().isBlank()) {
            return "fileId:" + analysis.getFileId();
        }
        if (analysis.getFileName() != null && !analysis.getFileName().isBlank()) {
            return "fileName:" + analysis.getFileName();
        }
        return "analysisId:" + analysis.getId();
    }

    private void validateFile(String mimeType, String fileBase64) {
        String mime = mimeType.toLowerCase();
        boolean isPdf = "application/pdf".equals(mime);
        if (!isPdf && !IMAGE_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException(
                    "mimeType không hỗ trợ: " + mimeType + " (chỉ nhận jpeg/png/webp/gif/pdf)");
        }

        // Ước lượng dung lượng từ độ dài base64
        long approxBytes = fileBase64.length() * 3L / 4;
        if (isPdf && approxBytes > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("File PDF quá lớn (tối đa 30MB)");
        }
        if (!isPdf && approxBytes > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh quá lớn (tối đa 5MB) — vui lòng resize trước khi upload");
        }
    }

    private String buildContext(AnalyzeDocumentRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Loại chứng từ người gọi vốn khai: ").append(req.getDocType()).append("\n\n");
        sb.append("Chấp nhận nhiều nhóm chứng từ chứng minh tài chính: sao kê lương, sao kê ngân hàng, ")
                .append("hợp đồng lao động, bảng lương, giấy phép kinh doanh, hóa đơn, sổ bán hàng, ")
                .append("sao kê POS/ví điện tử/nền tảng bán hàng, chứng từ thuế hoặc chứng từ thu nhập khác.\n\n");
        sb.append("THÔNG TIN NGƯỜI GỌI VỐN KHAI BÁO (để đối chiếu):\n");
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
