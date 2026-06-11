package com.p2plending.credit.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Kết quả AI phân tích một chứng từ thu nhập của người gọi vốn.
 * verdict chỉ là cảnh báo mức độ tin cậy — không phải phán quyết giả mạo.
 */
@Entity
@Table(name = "document_analyses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "loan_request_id")
    private String loanRequestId;

    /** SALARY_STATEMENT | BANK_STATEMENT | LABOR_CONTRACT | BUSINESS_LICENSE | OTHER */
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** ID file gốc trên file-manager — admin xem lại qua GET /file-manager/v2/file/{fileId} */
    @Column(name = "file_id", length = 100)
    private String fileId;

    /** CONSISTENT | SUSPICIOUS | HIGH_RISK | UNREADABLE */
    @Column(name = "verdict", nullable = false, length = 20)
    private String verdict;

    @Column(name = "trust_score")
    private Integer trustScore;

    @Column(name = "extracted_data", columnDefinition = "TEXT")
    private String extractedData;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
