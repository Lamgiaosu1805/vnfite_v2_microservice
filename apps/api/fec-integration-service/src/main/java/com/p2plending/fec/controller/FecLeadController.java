package com.p2plending.fec.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.fec.dto.FecCallbackResponse;
import com.p2plending.fec.dto.FecLeadStatusCallbackRequest;
import com.p2plending.fec.dto.FecLeadSubmitRequest;
import com.p2plending.fec.dto.FecLeadSubmitResponse;
import com.p2plending.fec.service.FecLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FecLeadController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final FecLeadService fecLeadService;
    private final ObjectMapper objectMapper;

    @Value("${app.internal.secret}")
    private String internalSecret;

    @PostMapping("/internal/fec/leads")
    public ResponseEntity<FecLeadSubmitResponse> submitLead(
            @RequestHeader(INTERNAL_SECRET_HEADER) String secret,
            @Valid @RequestBody FecLeadSubmitRequest request) {
        requireInternalSecret(secret);
        return ResponseEntity.ok(fecLeadService.submitLead(request));
    }

    @PostMapping("/api/fec/lead-status-callback")
    public ResponseEntity<FecCallbackResponse> receiveLeadStatusCallback(
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader("signature") String signature,
            @RequestBody String rawBody) throws Exception {
        FecLeadStatusCallbackRequest request = objectMapper.readValue(rawBody, FecLeadStatusCallbackRequest.class);
        fecLeadService.handleStatusCallback(apiKey, signature, rawBody, request);
        return ResponseEntity.ok(FecCallbackResponse.builder()
                .code(200)
                .data(Map.of("leadgen_id", request.getLeadgenId()))
                .message("Success")
                .build());
    }

    private void requireInternalSecret(String secret) {
        if (!internalSecret.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret");
        }
    }
}
