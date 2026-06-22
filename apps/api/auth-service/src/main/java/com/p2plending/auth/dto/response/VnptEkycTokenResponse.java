package com.p2plending.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token để mobile khởi tạo SDK VNPT eKYC.
 * accessToken được lấy từ VNPT oauth (username/password giữ ở backend),
 * tokenId/tokenKey là cặp khoá định danh đối tác VNPT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnptEkycTokenResponse {
    private String accessToken;
    private String tokenId;
    private String tokenKey;
}
