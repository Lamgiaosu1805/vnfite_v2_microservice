package com.p2plending.auth.controller;

import com.p2plending.auth.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * GET /api/auth/address/wards?province={code}
     * Trả về danh sách xã/phường/thị trấn theo tỉnh/thành phố.
     * Public — không yêu cầu JWT.
     */
    @GetMapping("/wards")
    public ResponseEntity<List<String>> getWards(@RequestParam String province) {
        return ResponseEntity.ok(addressService.getWardsByProvince(province));
    }
}
