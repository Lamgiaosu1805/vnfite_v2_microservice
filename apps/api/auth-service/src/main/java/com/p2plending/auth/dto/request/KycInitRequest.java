package com.p2plending.auth.dto.request;

import com.p2plending.auth.domain.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
public class KycInitRequest {

    @NotBlank(message = "Số CCCD không được để trống")
    @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải gồm 12 chữ số")
    private String cccdNumber;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotNull(message = "Giới tính không được để trống")
    private Gender gender;

    @NotNull(message = "Ngày sinh không được để trống")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Địa chỉ thường trú không được để trống")
    private String permanentAddress;

    @NotBlank(message = "Quê quán không được để trống")
    private String hometown;

    @NotNull(message = "Ngày cấp không được để trống")
    private LocalDate issueDate;

    @NotBlank(message = "Nơi cấp không được để trống")
    private String issuingAuthority;

    /** null = không thời hạn */
    private LocalDate expiryDate;

    @NotNull(message = "Ảnh mặt trước không được để trống")
    private MultipartFile frontImage;

    @NotNull(message = "Ảnh mặt sau không được để trống")
    private MultipartFile backImage;

    @NotNull(message = "Ảnh chân dung không được để trống")
    private MultipartFile portraitImage;
}
