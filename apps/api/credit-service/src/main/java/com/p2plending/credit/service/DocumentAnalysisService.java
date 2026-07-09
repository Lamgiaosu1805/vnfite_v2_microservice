package com.p2plending.credit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.credit.client.FileManagerClient;
import com.p2plending.credit.config.AppProperties;
import com.p2plending.credit.domain.entity.DocumentAnalysis;
import com.p2plending.credit.domain.repository.DocumentAnalysisRepository;
import com.p2plending.credit.dto.request.AnalyzeBusinessLicenseRequest;
import com.p2plending.credit.dto.request.AnalyzeDocumentRequest;
import com.p2plending.credit.dto.request.EvaluateScoreRequest;
import com.p2plending.credit.service.ai.AiDocumentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VIETNAM_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern VIETNAM_DATE_PATTERN =
            Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
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
     * Phân tích GPKD của hồ sơ doanh nghiệp: trích xuất tên DN/số ĐKKD/MST/người đại diện
     * và đối chiếu với thông tin khai báo. Kết quả chỉ tham khảo — admin vẫn duyệt tay.
     */
    @Transactional
    public DocumentAnalysis analyzeBusinessLicense(AnalyzeBusinessLicenseRequest req) {
        List<String> fileIds = businessLicenseFileIds(req);
        List<AiDocumentAnalyzer.DocumentInput> documents = new ArrayList<>();
        String context = buildBusinessLicenseContext(req);
        for (String fileId : fileIds) {
            FileManagerClient.FetchedFile file = fileManagerClient.fetch(fileId);
            String mimeType = file.mimeType();
            String fileBase64 = Base64.getEncoder().encodeToString(file.bytes());
            validateFile(mimeType, fileBase64);
            documents.add(new AiDocumentAnalyzer.DocumentInput(mimeType, fileBase64));
        }
        List<AiDocumentAnalyzer.DocumentCheckResult> pageResults =
                documentAnalyzer.analyzePages(documents, context);
        if (pageResults == null || pageResults.isEmpty()) {
            throw new IllegalStateException(
                    "AI phân tích chứng từ chưa được bật — cần APP_AI_ENABLED=true và API key tương ứng");
        }
        AiDocumentAnalyzer.DocumentCheckResult result =
                normalizeBusinessLicenseResult(mergeBusinessLicenseResults(pageResults), req);

        DocumentAnalysis entity = DocumentAnalysis.builder()
                .userId(req.getUserId())
                .docType("BUSINESS_LICENSE")
                .fileId(req.getFileId())
                .verdict(result.verdict() != null ? result.verdict() : "UNREADABLE")
                .trustScore(result.trustScore())
                .extractedData(toJson(result))
                .summary(result.summary())
                .build();
        entity = analysisRepository.save(entity);

        log.info("Phân tích GPKD xong: userId={} fileIds={} verdict={} trustScore={}",
                req.getUserId(), fileIds, entity.getVerdict(), entity.getTrustScore());
        return entity;
    }

    private List<String> businessLicenseFileIds(AnalyzeBusinessLicenseRequest req) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (req.getFileId() != null && !req.getFileId().isBlank()) {
            ids.add(req.getFileId());
        }
        if (req.getFileIds() != null) {
            req.getFileIds().stream()
                    .filter(v -> v != null && !v.isBlank())
                    .forEach(ids::add);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Cần truyền ít nhất một fileId GPKD");
        }
        return List.copyOf(ids);
    }

    private AiDocumentAnalyzer.DocumentCheckResult mergeBusinessLicenseResults(
            List<AiDocumentAnalyzer.DocumentCheckResult> results) {
        if (results == null || results.isEmpty()) {
            return new AiDocumentAnalyzer.DocumentCheckResult(
                    null, "UNREADABLE", 0, null, null, null, List.of(), List.of(),
                    "Không có trang GPKD nào để phân tích.");
        }

        List<String> findings = distinctStrings(results.stream()
                .flatMap(r -> safeList(r.findings()).stream())
                .toList());
        List<String> issues = distinctStrings(results.stream()
                .flatMap(r -> safeList(r.consistencyIssues()).stream())
                .toList());
        int trustScore = (int) Math.round(results.stream()
                .filter(r -> r.trustScore() != null)
                .mapToInt(AiDocumentAnalyzer.DocumentCheckResult::trustScore)
                .average()
                .orElse(50));

        return new AiDocumentAnalyzer.DocumentCheckResult(
                firstNonBlank(results.stream().map(AiDocumentAnalyzer.DocumentCheckResult::docTypeDetected).toList()),
                strongestVerdict(results),
                trustScore,
                firstNonBlank(results.stream().map(AiDocumentAnalyzer.DocumentCheckResult::ownerName).toList()),
                firstNonBlank(results.stream().map(AiDocumentAnalyzer.DocumentCheckResult::organizationName).toList()),
                firstNonBlank(results.stream().map(AiDocumentAnalyzer.DocumentCheckResult::extractedMonthlyIncome).toList()),
                findings,
                issues,
                mergeSummary(results)
        );
    }

    private String buildBusinessLicenseContext(AnalyzeBusinessLicenseRequest req) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        StringBuilder sb = new StringBuilder();
        sb.append("Chứng từ đính kèm là GIẤY CHỨNG NHẬN ĐĂNG KÝ KINH DOANH (hoặc GCN đăng ký hộ kinh doanh) ")
                .append("của Việt Nam. Nhiệm vụ: trích xuất và đối chiếu.\n\n");
        sb.append("MỐC THỜI GIAN KIỂM TRA: ngày hiện tại theo múi giờ Việt Nam là ")
                .append(today.format(VIETNAM_DATE))
                .append(". Chỉ coi một ngày trên chứng từ là ngày tương lai/chưa đến nếu ngày đó SAU mốc này. ")
                .append("Ngày cấp, ngày đăng ký thay đổi hoặc ngày cấp thay đổi lần thứ N nằm từ quá khứ đến hôm nay ")
                .append("là hợp lệ về mặt thời gian; không được đánh SUSPICIOUS chỉ vì ngày thay đổi sau ngày đăng ký lần đầu.\n\n");
        sb.append("CƠ QUAN CẤP HIỆN HÀNH: từ 01/07/2025, Cơ quan đăng ký kinh doanh cấp tỉnh thuộc Sở Tài chính. ")
                .append("Vì vậy các cách ghi như 'Sở Tài chính', 'Phòng Đăng ký kinh doanh thuộc Sở Tài chính' ")
                .append("hoặc 'Phòng Quản lý doanh nghiệp - Sở Tài chính' là hợp lệ; không được đánh SUSPICIOUS ")
                .append("chỉ vì không ghi 'Sở Kế hoạch và Đầu tư'.\n\n");
        sb.append("TRÍCH XUẤT từ chứng từ: tên doanh nghiệp/hộ kinh doanh (ghi vào organizationName), ")
                .append("tên người đại diện pháp luật/chủ hộ (ghi vào ownerName), số GCN đăng ký, mã số thuế, ")
                .append("ngày cấp, nơi cấp, địa chỉ trụ sở, loại hình (liệt kê trong findings).\n\n");
        sb.append("THÔNG TIN NGƯỜI DÙNG KHAI BÁO (để đối chiếu — mọi điểm lệch ghi vào consistencyIssues):\n");
        sb.append("- Loại hình: ").append(orUnknown(req.getExpectedBusinessType())).append("\n");
        sb.append("- Tên doanh nghiệp/hộ KD: ").append(orUnknown(req.getExpectedBusinessName())).append("\n");
        sb.append("- Số GCN đăng ký: ").append(orUnknown(req.getExpectedRegistrationNumber())).append("\n");
        sb.append("- Mã số thuế: ").append(orUnknown(req.getExpectedTaxCode())).append("\n");
        sb.append("- Người đại diện: ").append(orUnknown(req.getExpectedRepresentativeName())).append("\n");
        sb.append("- CCCD người đại diện (đã eKYC): ").append(orUnknown(req.getExpectedRepresentativeCccd())).append("\n\n");
        sb.append("QUAN TRỌNG: người đại diện trên GPKD phải trùng người đã eKYC. ")
                .append("Nếu tên người đại diện trên chứng từ khác tên khai báo → verdict SUSPICIOUS trở lên. ")
                .append("Chỉ đưa vấn đề ngày tháng vào consistencyIssues khi có mâu thuẫn thật sự: ngày nằm sau ")
                .append(today.format(VIETNAM_DATE))
                .append(", ngày không tồn tại, hoặc thứ tự ngày tự mâu thuẫn trên cùng chứng từ. ")
                .append("Không đưa 'Sở Tài chính không có thẩm quyền' vào issues/findings. ")
                .append("Kiểm tra thêm dấu hiệu chỉnh sửa: font không đồng nhất, con dấu mờ/bất thường, căn lề lệch.");
        return sb.toString();
    }

    private AiDocumentAnalyzer.DocumentCheckResult normalizeBusinessLicenseResult(
            AiDocumentAnalyzer.DocumentCheckResult result,
            AnalyzeBusinessLicenseRequest req) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        boolean representativeFound = representativeMatchesExpected(result.ownerName(), req.getExpectedRepresentativeName());
        List<String> issues = filterBusinessLicenseFalsePositives(result.consistencyIssues(), today, representativeFound);
        List<String> findings = filterBusinessLicenseFalsePositives(result.findings(), today, representativeFound);

        boolean changed = !sameList(result.consistencyIssues(), issues) || !sameList(result.findings(), findings);
        if (!changed) {
            return result;
        }

        String verdict = result.verdict();
        String summary = result.summary();
        if ((issues == null || issues.isEmpty())
                && containsBusinessLicenseFalsePositive(summary, today, representativeFound)) {
            summary = findings != null && findings.stream().anyMatch(this::looksLikeDocumentTamperingFinding)
                    ? "Thông tin đối chiếu khớp khai báo. Một số ghi chú về định dạng/chất lượng ảnh chỉ để thẩm định viên tham khảo thủ công."
                    : "Thông tin cơ bản khớp khai báo; không phát hiện điểm không khớp trọng yếu sau khi loại trừ cảnh báo sai về ngày/cơ quan cấp.";
        }
        if (issues == null || issues.isEmpty()) {
            verdict = "CONSISTENT";
        }

        return new AiDocumentAnalyzer.DocumentCheckResult(
                result.docTypeDetected(),
                verdict,
                result.trustScore(),
                result.ownerName(),
                result.organizationName(),
                result.extractedMonthlyIncome(),
                findings,
                issues,
                summary
        );
    }

    private List<String> filterBusinessLicenseFalsePositives(
            List<String> items,
            LocalDate today,
            boolean representativeFound) {
        if (items == null || items.isEmpty()) return items;
        return items.stream()
                .filter(item -> !isIssuerAuthorityFalsePositive(item))
                .filter(item -> !isFutureDateFalsePositive(item, today))
                .filter(item -> !isMissingRepresentativeFalsePositive(item, representativeFound))
                .filter(item -> !isPageScopedMissingFalsePositive(item))
                .toList();
    }

    private boolean isIssuerAuthorityFalsePositive(String item) {
        String normalized = normalizeVietnamese(item);
        return normalized.contains("so tai chinh")
                && (normalized.contains("khong co tham quyen")
                || normalized.contains("khong dung")
                || normalized.contains("phai la so ke hoach")
                || normalized.contains("so ke hoach va dau tu"));
    }

    private boolean isFutureDateFalsePositive(String item, LocalDate today) {
        String normalized = normalizeVietnamese(item);
        if (!normalized.contains("tuong lai") && !normalized.contains("chua den")) {
            return false;
        }

        Matcher matcher = VIETNAM_DATE_PATTERN.matcher(item);
        boolean foundDate = false;
        while (matcher.find()) {
            foundDate = true;
            try {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                if (LocalDate.of(year, month, day).isAfter(today)) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        return foundDate;
    }

    private boolean isMissingRepresentativeFalsePositive(String item, boolean representativeFound) {
        if (!representativeFound) return false;
        String normalized = normalizeVietnamese(item);
        boolean mentionsRepresentative = normalized.contains("nguoi dai dien")
                || normalized.contains("dai dien phap luat")
                || normalized.contains("chu tich")
                || normalized.contains("chu so huu");
        boolean saysMissing = normalized.contains("khong xuat hien")
                || normalized.contains("khong hien thi")
                || normalized.contains("khong duoc the hien")
                || normalized.contains("thieu thong tin")
                || normalized.contains("khong the doi chieu");
        return mentionsRepresentative && saysMissing;
    }

    private boolean representativeMatchesExpected(String extractedName, String expectedName) {
        if (extractedName == null || extractedName.isBlank()
                || expectedName == null || expectedName.isBlank()) {
            return false;
        }
        String extracted = normalizeVietnamese(extractedName);
        String expected = normalizeVietnamese(expectedName);
        return extracted.equals(expected) || extracted.contains(expected) || expected.contains(extracted);
    }

    private boolean containsBusinessLicenseFalsePositive(
            String summary,
            LocalDate today,
            boolean representativeFound) {
        return summary != null && (isIssuerAuthorityFalsePositive(summary)
                || isFutureDateFalsePositive(summary, today)
                || isMissingRepresentativeFalsePositive(summary, representativeFound)
                || isPageScopedMissingFalsePositive(summary));
    }

    private boolean isPageScopedMissingFalsePositive(String item) {
        String normalized = normalizeVietnamese(item);
        boolean pageScoped = normalized.contains("trang chung tu nay")
                || normalized.contains("trang nay")
                || normalized.contains("trang tai lieu");
        boolean saysMissing = normalized.contains("khong co")
                || normalized.contains("khong xuat hien")
                || normalized.contains("khong hien thi")
                || normalized.contains("khong duoc the hien")
                || normalized.contains("khong the doi chieu")
                || normalized.contains("khong the xac minh");
        return pageScoped && saysMissing;
    }

    private boolean looksLikeDocumentTamperingFinding(String item) {
        String normalized = normalizeVietnamese(item);
        return normalized.contains("font")
                || normalized.contains("con dau")
                || normalized.contains("chu ky")
                || normalized.contains("can le")
                || normalized.contains("dinh dang")
                || normalized.contains("chinh sua")
                || normalized.contains("bat thuong");
    }

    private boolean sameList(List<String> a, List<String> b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        return a.equals(b);
    }

    private List<String> safeList(List<String> items) {
        return items != null ? items : List.of();
    }

    private List<String> distinctStrings(List<String> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (items != null) {
            items.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .forEach(values::add);
        }
        return List.copyOf(values);
    }

    private String firstNonBlank(List<String> values) {
        if (values == null) return null;
        return values.stream()
                .filter(v -> v != null && !v.isBlank() && !"null".equalsIgnoreCase(v))
                .findFirst()
                .orElse(null);
    }

    private String strongestVerdict(List<AiDocumentAnalyzer.DocumentCheckResult> results) {
        String strongest = "CONSISTENT";
        for (AiDocumentAnalyzer.DocumentCheckResult result : results) {
            String verdict = result.verdict() != null ? result.verdict().toUpperCase(java.util.Locale.ROOT) : "UNREADABLE";
            if ("HIGH_RISK".equals(verdict)) return "HIGH_RISK";
            if ("SUSPICIOUS".equals(verdict)) strongest = "SUSPICIOUS";
            if ("UNREADABLE".equals(verdict) && "CONSISTENT".equals(strongest)) strongest = "UNREADABLE";
        }
        return strongest;
    }

    private String mergeSummary(List<AiDocumentAnalyzer.DocumentCheckResult> results) {
        List<String> summaries = distinctStrings(results.stream()
                .map(AiDocumentAnalyzer.DocumentCheckResult::summary)
                .filter(v -> v != null && !v.isBlank())
                .toList());
        if (summaries.isEmpty()) {
            return "Đã phân tích các trang GPKD; không có tóm tắt từ AI.";
        }
        return String.join(" ", summaries);
    }

    private String normalizeVietnamese(String value) {
        if (value == null) return "";
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
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
