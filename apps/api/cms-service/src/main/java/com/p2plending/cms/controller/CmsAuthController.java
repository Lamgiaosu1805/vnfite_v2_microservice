package com.p2plending.cms.controller;

import com.p2plending.cms.dto.request.CmsLoginRequest;
import com.p2plending.cms.dto.response.CmsAuthResponse;
import com.p2plending.cms.service.CmsAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/auth")
@RequiredArgsConstructor
public class CmsAuthController {

    private final CmsAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<CmsAuthResponse> login(@Valid @RequestBody CmsLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
