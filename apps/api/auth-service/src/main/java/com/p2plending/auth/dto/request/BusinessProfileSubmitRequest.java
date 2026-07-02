package com.p2plending.auth.dto.request;

import com.p2plending.auth.domain.enums.BusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** Multipart form nộp hồ sơ doanh nghiệp (pattern KycInitRequest). */
@Data
public class BusinessProfileSubmitRequest {

    @NotNull(message = "Loại hình kinh doanh không được để trống")
    private BusinessType businessType;

    @NotBlank(message = "Tên doanh nghiệp/hộ kinh doanh không được để trống")
    private String businessName;

    @NotBlank(message = "Số giấy chứng nhận đăng ký kinh doanh không được để trống")
    private String registrationNumber;

    /** MST — hộ kinh doanh có thể chưa có. */
    private String taxCode;

    private LocalDate issueDate;

    /** Nơi cấp GCN. */
    private String issuedBy;

    @NotBlank(message = "Địa chỉ trụ sở không được để trống")
    private String headOfficeAddress;

    private String businessSector;

    @NotBlank(message = "Tên người đại diện không được để trống")
    private String representativeName;

    @NotBlank(message = "Số CCCD người đại diện không được để trống")
    @Pattern(regexp = "\\d{12}", message = "Số CCCD người đại diện phải gồm 12 chữ số")
    private String representativeCccd;

    @NotNull(message = "Ảnh giấy chứng nhận đăng ký kinh doanh không được để trống")
    private MultipartFile licenseImage;

    /** Ảnh bổ sung (trang 2, mặt sau...) — không bắt buộc. */
    private MultipartFile licenseExtra1Image;

    private MultipartFile licenseExtra2Image;
}
