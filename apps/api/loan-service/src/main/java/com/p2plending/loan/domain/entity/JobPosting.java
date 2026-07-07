package com.p2plending.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_posting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 255)
    private String position;

    @Column(length = 255)
    private String salary;

    @Column(length = 255)
    private String locations;

    @Column(name = "industry_type", length = 50)
    private String industryType;

    @Column(name = "working_form", length = 100)
    private String workingForm;

    @Column(length = 255)
    private String experience;

    @Column(name = "work_model", length = 100)
    private String workModel;

    @Column(length = 255)
    private String degree;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
