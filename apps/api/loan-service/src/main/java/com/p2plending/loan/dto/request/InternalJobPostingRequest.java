package com.p2plending.loan.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InternalJobPostingRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 500, message = "Tiêu đề tối đa 500 ký tự")
    private String title;

    @Size(max = 255, message = "Vị trí tối đa 255 ký tự")
    private String position;

    @Size(max = 255, message = "Mức lương tối đa 255 ký tự")
    private String salary;

    /** CSV các địa điểm cố định, vd: "Hà Nội,TP.HCM" */
    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String locations;

    private String industryType;

    @Size(max = 100, message = "Hình thức làm việc tối đa 100 ký tự")
    private String workingForm;

    @Size(max = 255, message = "Kinh nghiệm tối đa 255 ký tự")
    private String experience;

    @Size(max = 100, message = "Mô hình làm việc tối đa 100 ký tự")
    private String workModel;

    @Size(max = 255, message = "Bằng cấp tối đa 255 ký tự")
    private String degree;

    private String description;

    @Size(max = 500, message = "URL ảnh tối đa 500 ký tự")
    private String imageUrl;

    private String status;

    private LocalDateTime publishedAt;
}
